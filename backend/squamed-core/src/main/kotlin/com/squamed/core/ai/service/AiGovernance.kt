package com.squamed.core.ai.service

import com.squamed.core.ai.domain.AiIncident
import com.squamed.core.ai.domain.EvalRun
import com.squamed.core.ai.domain.GatewayPolicy
import com.squamed.core.ai.repository.AiIncidentRepository
import com.squamed.core.ai.repository.EvalRunRepository
import com.squamed.core.ai.repository.GatewayPolicyRepository
import com.squamed.core.ai.repository.InteractionLogRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class RedactionResult(
  val text: String,
  val redacted: Boolean,
  val hits: Int,
)

// 05.01: strip direct identifiers before a prompt leaves the platform. Names are kept - the
// operations assistant answers questions about them; MRN/QID/phone/email are not needed.
@Component
class PromptRedactor {
  private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
  private val mrn = Regex("\\bMQ-\\d{4}-\\d{4}\\b")
  private val qid = Regex("\\b\\d{11}\\b")
  private val phone = Regex("(?<!\\d)(?:\\+?974)?[\\s-]?\\d{8}(?!\\d)")

  // A UUID's first group is 8 hex characters, so ~2% of ids are 8 digits and match the phone rule.
  // Masking ids with a placeholder keeps them intact: the command bar resolves ids from the prompt,
  // and a redacted id silently turned "prepare discharge <uuid>" into "prepare discharge [PHONE]".
  private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

  fun redact(text: String): RedactionResult {
    val ids = mutableListOf<String>()
    var out =
      uuid.replace(text) { match ->
        ids.add(match.value)
        "\u0000ID${ids.size - 1}\u0000"
      }
    var hits = 0
    listOf(email to "[EMAIL]", mrn to "[MRN]", qid to "[QID]", phone to "[PHONE]").forEach { (pattern, token) ->
      val found = pattern.findAll(out).count()
      if (found > 0) {
        hits += found
        out = pattern.replace(out, token)
      }
    }
    ids.forEachIndexed { index, id -> out = out.replace("\u0000ID$index\u0000", id) }
    return RedactionResult(out, hits > 0, hits)
  }
}

data class AiUsageView(
  val capability: String,
  val enabled: Boolean,
  val dailyTokenCap: Int,
  val tokensToday: Long,
  val costTodayUsd: BigDecimal,
)

// 05.07 governance registry views.
data class EvalRunView(
  val id: UUID?,
  val capability: String,
  val subject: String,
  val suiteName: String,
  val casesPassed: Int,
  val casesTotal: Int,
  val score: BigDecimal,
  val verdict: String,
  val notes: String?,
  val recordedAt: Instant,
) {
  companion object {
    fun from(entity: EvalRun) =
      EvalRunView(
        entity.id,
        entity.capability,
        entity.subject,
        entity.suiteName,
        entity.casesPassed,
        entity.casesTotal,
        entity.score,
        entity.verdict,
        entity.notes,
        entity.recordedAt,
      )
  }
}

data class AiIncidentView(
  val id: UUID?,
  val capability: String,
  val severity: String,
  val summary: String,
  val detail: String?,
  val status: String,
  val reportedAt: Instant,
  val closedAt: Instant?,
  val resolution: String?,
) {
  companion object {
    fun from(entity: AiIncident) =
      AiIncidentView(
        entity.id,
        entity.capability,
        entity.severity,
        entity.summary,
        entity.detail,
        entity.status,
        entity.reportedAt,
        entity.closedAt,
        entity.resolution,
      )
  }
}

data class CostRowView(
  val name: String,
  val requests: Long,
  val tokens: Long,
  val costUsd: BigDecimal,
)

data class CostDashboardView(
  val from: Instant,
  val to: Instant,
  val byCapability: List<CostRowView>,
  val byModel: List<CostRowView>,
  val totalCostUsd: BigDecimal,
)

// 05.01 gateway governance + 05.07 registry: kill switch, caps, usage, evals, incidents, costs.
@Service
class AiGovernanceService(
  private val policyRepository: GatewayPolicyRepository,
  private val logRepository: InteractionLogRepository,
  private val evalRepository: EvalRunRepository,
  private val incidentRepository: AiIncidentRepository,
  private val eventPublisher: DomainEventPublisher,
  @Value("\${squamed.ai.daily-token-cap:200000}") private val defaultDailyTokenCap: Int = 200_000,
) {
  companion object {
    val CAPABILITIES = listOf("chat", "command", "scribe", "report")
  }

  @Transactional
  fun policy(
    hospitalId: UUID,
    capability: String,
  ): GatewayPolicy {
    val cap = capability.lowercase()
    if (cap !in CAPABILITIES) throw IllegalArgumentException("AI_CAPABILITY_INVALID")
    return policyRepository.findByHospitalIdAndCapability(hospitalId, cap)
      ?: policyRepository.save(GatewayPolicy(hospitalId = hospitalId, capability = cap, dailyTokenCap = defaultDailyTokenCap))
  }

  @Transactional
  fun assertAllowed(
    hospitalId: UUID,
    capability: String,
  ) {
    val policy = policy(hospitalId, capability)
    if (!policy.enabled) throw IllegalStateException("AI_CAPABILITY_DISABLED")
    if (policy.dailyTokenCap <= 0 || logRepository.sumTokensSince(hospitalId, capability, todayStart()) >= policy.dailyTokenCap) {
      throw IllegalStateException("AI_DAILY_TOKEN_CAP_REACHED")
    }
  }

  @Transactional
  fun update(
    hospitalId: UUID,
    capability: String,
    enabled: Boolean?,
    dailyTokenCap: Int?,
  ): GatewayPolicy {
    val policy = policy(hospitalId, capability)
    enabled?.let { policy.enabled = it }
    dailyTokenCap?.let {
      if (it < 0) throw IllegalArgumentException("AI_TOKEN_CAP_INVALID")
      policy.dailyTokenCap = it
    }
    return policyRepository.save(policy)
  }

  @Transactional
  fun policies(hospitalId: UUID): List<GatewayPolicy> = CAPABILITIES.map { policy(hospitalId, it) }

  @Transactional(readOnly = true)
  fun usage(hospitalId: UUID): List<AiUsageView> =
    CAPABILITIES.map { capability ->
      val policy = policyRepository.findByHospitalIdAndCapability(hospitalId, capability)
      val from = todayStart()
      AiUsageView(
        capability = capability,
        enabled = policy?.enabled ?: true,
        dailyTokenCap = policy?.dailyTokenCap ?: defaultDailyTokenCap,
        tokensToday = logRepository.sumTokensSince(hospitalId, capability, from),
        costTodayUsd = logRepository.sumCostSince(hospitalId, capability, from),
      )
    }

  @Transactional
  fun recordEval(
    hospitalId: UUID,
    capability: String,
    subject: String,
    suiteName: String,
    casesPassed: Int,
    casesTotal: Int,
    notes: String?,
    actorId: UUID?,
  ): EvalRunView {
    val cap = capability.lowercase()
    if (cap !in CAPABILITIES) throw IllegalArgumentException("AI_CAPABILITY_INVALID")
    if (casesTotal <= 0 || casesPassed < 0 || casesPassed > casesTotal) throw IllegalArgumentException("AI_EVAL_COUNTS_INVALID")
    if (subject.isBlank() || suiteName.isBlank()) throw IllegalArgumentException("AI_EVAL_SUBJECT_REQUIRED")
    val score = BigDecimal.valueOf(casesPassed.toLong() * 100).divide(BigDecimal.valueOf(casesTotal.toLong()), 2, RoundingMode.HALF_UP)
    val saved =
      evalRepository.save(
        EvalRun(
          hospitalId = hospitalId,
          capability = cap,
          subject = subject.trim(),
          suiteName = suiteName.trim(),
          casesPassed = casesPassed,
          casesTotal = casesTotal,
          score = score,
          verdict = if (casesPassed == casesTotal) "PASS" else "FAIL",
          notes = notes,
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return EvalRunView.from(saved)
  }

  @Transactional(readOnly = true)
  fun evals(
    hospitalId: UUID,
    subject: String?,
  ): List<EvalRunView> =
    (
      if (subject.isNullOrBlank()) {
        evalRepository.findByHospitalIdOrderByRecordedAtDesc(hospitalId)
      } else {
        evalRepository.findByHospitalIdAndSubjectOrderByRecordedAtDesc(hospitalId, subject)
      }
    ).map(EvalRunView::from)

  @Transactional
  fun reportIncident(
    hospitalId: UUID,
    capability: String,
    severity: String,
    summary: String,
    detail: String?,
    actorId: UUID?,
  ): AiIncidentView {
    val cap = capability.lowercase()
    if (cap !in CAPABILITIES) throw IllegalArgumentException("AI_CAPABILITY_INVALID")
    val sev = severity.trim().lowercase()
    if (sev !in setOf("low", "medium", "high", "critical")) throw IllegalArgumentException("AI_INCIDENT_SEVERITY_INVALID")
    if (summary.isBlank()) throw IllegalArgumentException("AI_INCIDENT_SUMMARY_REQUIRED")
    val saved =
      incidentRepository.save(
        AiIncident(
          hospitalId = hospitalId,
          capability = cap,
          severity = sev,
          summary = summary.trim().take(300),
          detail = detail,
          reportedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "ai.incident_reported",
        occurredAt = Instant.now(),
        source = "ai-service",
        entityType = "ai_incident",
        entityId = saved.id,
        hospitalId = hospitalId,
        payload = mapOf("capability" to saved.capability, "severity" to saved.severity),
        actorType = "user",
        actorId = actorId,
      ),
    )
    return AiIncidentView.from(saved)
  }

  @Transactional(readOnly = true)
  fun incidents(
    hospitalId: UUID,
    status: String?,
  ): List<AiIncidentView> =
    (
      if (status.isNullOrBlank()) {
        incidentRepository.findByHospitalIdOrderByReportedAtDesc(hospitalId)
      } else {
        incidentRepository.findByHospitalIdAndStatusOrderByReportedAtDesc(hospitalId, status.uppercase())
      }
    ).map(AiIncidentView::from)

  @Transactional
  fun updateIncident(
    id: UUID,
    hospitalId: UUID,
    status: String?,
    resolution: String?,
    actorId: UUID?,
  ): AiIncidentView {
    val incident = incidentRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("AI_INCIDENT_NOT_FOUND")
    if (incident.status == "CLOSED") throw IllegalStateException("AI_INCIDENT_CLOSED")
    status?.let {
      val next = it.trim().uppercase()
      if (next !in setOf("OPEN", "INVESTIGATING", "CLOSED")) throw IllegalArgumentException("AI_INCIDENT_STATUS_INVALID")
      if (next == "CLOSED" && resolution.isNullOrBlank()) throw IllegalArgumentException("AI_INCIDENT_RESOLUTION_REQUIRED")
      incident.status = next
      if (next == "CLOSED") incident.closedAt = Instant.now()
    }
    resolution?.let { incident.resolution = it.trim().take(400) }
    incident.updatedBy = actorId
    return AiIncidentView.from(incidentRepository.save(incident))
  }

  @Transactional(readOnly = true)
  fun costs(
    hospitalId: UUID,
    from: Instant?,
    to: Instant?,
  ): CostDashboardView {
    val end = to ?: Instant.now()
    val start = from ?: end.minus(Duration.ofDays(7))
    val byCapability = rows(logRepository.costByCapability(hospitalId, start, end))
    val byModel = rows(logRepository.costByModel(hospitalId, start, end))
    return CostDashboardView(
      from = start,
      to = end,
      byCapability = byCapability,
      byModel = byModel,
      totalCostUsd = byCapability.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.costUsd) },
    )
  }

  private fun rows(raw: List<Array<Any?>>): List<CostRowView> =
    raw.map { row ->
      CostRowView(
        name = (row[0] as String?) ?: "unknown",
        requests = (row[1] as Number).toLong(),
        tokens = (row[2] as Number).toLong(),
        costUsd = row[3] as BigDecimal,
      )
    }

  private fun todayStart() = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
}
