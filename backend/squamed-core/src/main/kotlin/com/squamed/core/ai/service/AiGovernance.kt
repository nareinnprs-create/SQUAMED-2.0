package com.squamed.core.ai.service

import com.squamed.core.ai.domain.GatewayPolicy
import com.squamed.core.ai.repository.GatewayPolicyRepository
import com.squamed.core.ai.repository.InteractionLogRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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

  fun redact(text: String): RedactionResult {
    var out = text
    var hits = 0
    listOf(email to "[EMAIL]", mrn to "[MRN]", qid to "[QID]", phone to "[PHONE]").forEach { (pattern, token) ->
      val found = pattern.findAll(out).count()
      if (found > 0) {
        hits += found
        out = pattern.replace(out, token)
      }
    }
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

// 05.01: per-capability kill switch + hard daily tenant cap. Read fresh on every request so a
// switch flip takes effect immediately (< 1 min by construction).
@Service
class AiGovernanceService(
  private val policyRepository: GatewayPolicyRepository,
  private val logRepository: InteractionLogRepository,
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

  private fun todayStart() = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant()
}
