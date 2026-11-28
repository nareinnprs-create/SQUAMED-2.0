package com.squamed.core.ai.service

import com.squamed.core.ai.domain.EvalRun
import com.squamed.core.ai.repository.AgentConfigRepository
import com.squamed.core.ai.repository.AiIncidentRepository
import com.squamed.core.ai.repository.EvalRunRepository
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.laboratory.repository.CriticalResultRepository
import com.squamed.core.ops.repository.AssetRepository
import com.squamed.core.ops.repository.CssdTrayRepository
import com.squamed.core.ops.repository.PurchaseRequisitionRepository
import com.squamed.core.ops.repository.WorkOrderRepository
import com.squamed.core.pharmacy.repository.PharmacyStockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class BlockerFinding(
  val agentCode: String,
  val domain: String,
  val severity: String,
  val blocker: String,
  val ownerRole: String,
  val impact: String,
  val recommendedAction: String,
  val evidenceRef: String,
)

data class OrchestrationView(
  val question: String,
  val agentsConsulted: List<String>,
  val primaryBlocker: BlockerFinding?,
  val synthesized: Map<String, String>,
  val blockers: List<BlockerFinding>,
)

data class AgentEvalView(
  val agentCode: String,
  val casesPassed: Int,
  val casesTotal: Int,
  val unauthorizedActions: Int,
  val verdict: String,
  val evalRunId: UUID?,
)

// 07.01: the orchestrator decomposes a cross-domain question into agent diagnostics over governed
// platform state and synthesizes blocker -> owner -> impact -> recommended action. It never mutates
// source records; acting on a blocker goes through the owning workflow.
@Service
class AiOrchestratorService(
  private val admissionRepository: AdmissionRepository,
  private val criticalResultRepository: CriticalResultRepository,
  private val pharmacyStockRepository: PharmacyStockRepository,
  private val cssdTrayRepository: CssdTrayRepository,
  private val workOrderRepository: WorkOrderRepository,
  private val assetRepository: AssetRepository,
  private val requisitionRepository: PurchaseRequisitionRepository,
  private val incidentRepository: AiIncidentRepository,
  private val agentRepository: AgentConfigRepository,
  private val evalRunRepository: EvalRunRepository,
) {
  companion object {
    private val SEVERITY_RANK = mapOf("critical" to 0, "high" to 1, "medium" to 2, "low" to 3)

    // Ordered keyword families. First match wins, so specific operational language outranks the
    // generic clinical/executive fallbacks.
    private val DOMAIN_KEYWORDS: List<Pair<String, List<String>>> =
      listOf(
        "surgery" to listOf("surg", "theatre", "operation", " ot ", "cssd", "tray", "instrument", "steril"),
        "security" to listOf("security", "unauthori", "breach", "access control", "incident"),
        "procurement" to listOf("procure", "purchase", "requisition", "indent", "vendor", "supply", "supplies"),
        "pharmacy" to listOf("pharmac", "drug", "medication", "dispens", "stock", "formulary"),
        "facilities" to listOf("facilit", "equipment", "asset", "maintenance", "work order", "broken", "biomed"),
        "clinical" to listOf("discharge", "clinical", "patient", "lab ", "critical result", "ward", "nurse", "bed", "admission"),
        "executive" to listOf("executive", "overall", "hospital", "capacity", "tonight", "biggest", "leadership"),
      )

    private val CONSULTED: Map<String, List<String>> =
      mapOf(
        "surgery" to listOf("facilities", "clinical"),
        "security" to listOf("security"),
        "procurement" to listOf("procurement", "pharmacy"),
        "pharmacy" to listOf("pharmacy", "procurement"),
        "facilities" to listOf("facilities", "procurement"),
        "clinical" to listOf("clinical", "pharmacy"),
        "executive" to listOf("clinical", "pharmacy", "facilities", "procurement", "security", "executive"),
      )
  }

  fun classify(question: String): String {
    val q = question.lowercase()
    return DOMAIN_KEYWORDS.firstOrNull { (_, words) -> words.any { q.contains(it) } }?.first ?: "executive"
  }

  @Transactional(readOnly = true)
  fun orchestrate(
    hospitalId: UUID,
    question: String,
  ): OrchestrationView {
    if (question.isBlank()) throw IllegalArgumentException("AI_ORCHESTRATION_QUESTION_REQUIRED")
    val domain = classify(question)
    val consulted = CONSULTED[domain] ?: CONSULTED.getValue("executive")
    val now = Instant.now()
    val findings =
      consulted.flatMap { agent ->
        diagnose(agent, hospitalId, now).sortedBy { SEVERITY_RANK[it.severity] ?: 9 }
      }
    val primary =
      if (domain == "executive") {
        findings.sortedBy { SEVERITY_RANK[it.severity] ?: 9 }.firstOrNull()
      } else {
        consulted.firstNotNullOfOrNull { agent -> findings.firstOrNull { it.agentCode == agent } }
      }
    val synthesized =
      if (primary == null) {
        mapOf(
          "blocker" to "No blocking condition found",
          "owner" to "none",
          "impact" to "none",
          "recommendedAction" to "No action required",
        )
      } else {
        mapOf(
          "blocker" to primary.blocker,
          "owner" to primary.ownerRole,
          "impact" to primary.impact,
          "recommendedAction" to primary.recommendedAction,
        )
      }
    return OrchestrationView(
      question = question.trim(),
      agentsConsulted = consulted,
      primaryBlocker = primary,
      synthesized = synthesized,
      blockers = findings,
    )
  }

  // Runs the persona boundary against a fixed probe set; a disallowed probe that the policy lets
  // through is an unauthorized action and fails the suite.
  @Transactional
  fun runAgentEval(
    agentCode: String,
    hospitalId: UUID,
    actorId: UUID?,
  ): AgentEvalView {
    val agent = agentRepository.findByCodeIgnoreCase(agentCode) ?: throw IllegalArgumentException("AI_AGENT_NOT_FOUND")
    var passed = 0
    var unauthorized = 0
    AgentPersonas.PROBES.forEach { (type, relatedType) ->
      val allowed = AgentPersonas.isAllowed(agent.code, type, relatedType)
      val expected = expectedAllowed(agent.code, type, relatedType)
      if (allowed == expected) passed++
      if (allowed && !expected) unauthorized++
    }
    val total = AgentPersonas.PROBES.size
    val verdict = if (passed == total && unauthorized == 0) "PASS" else "FAIL"
    val run =
      evalRunRepository.save(
        EvalRun(
          hospitalId = hospitalId,
          capability = "command",
          subject = "${agent.code}@1",
          suiteName = "unauthorized_action",
          casesPassed = passed,
          casesTotal = total,
          score =
            BigDecimal
              .valueOf(passed.toLong() * 100)
              .divide(BigDecimal.valueOf(total.toLong()), 2, RoundingMode.HALF_UP),
          verdict = verdict,
          notes = "persona boundary probes; unauthorized=$unauthorized",
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return AgentEvalView(agent.code, passed, total, unauthorized, verdict, run.id)
  }

  // Ground truth for the eval harness, declared independently of the policy implementation.
  private fun expectedAllowed(
    agentCode: String,
    actionType: String,
    relatedType: String?,
  ): Boolean = AgentPersonas.expectedAllowed(agentCode, actionType, relatedType)

  private fun diagnose(
    agentCode: String,
    hospitalId: UUID,
    now: Instant,
  ): List<BlockerFinding> =
    when (agentCode) {
      "clinical" -> clinicalFindings(hospitalId, now)
      "pharmacy" -> pharmacyFindings(hospitalId)
      "facilities" -> facilitiesFindings(hospitalId)
      "procurement" -> procurementFindings(hospitalId)
      "security" -> securityFindings(hospitalId)
      else -> emptyList()
    }

  private fun clinicalFindings(
    hospitalId: UUID,
    now: Instant,
  ): List<BlockerFinding> {
    val findings = mutableListOf<BlockerFinding>()
    val openCritical = criticalResultRepository.findAllForHospital(hospitalId).count { it.status == "OPEN" }
    if (openCritical > 0) {
      findings +=
        BlockerFinding(
          agentCode = "clinical",
          domain = "clinical",
          severity = "critical",
          blocker = "$openCritical unacknowledged critical result(s)",
          ownerRole = "Attending consultant",
          impact = "Delayed clinical action on a critical value",
          recommendedAction = "Acknowledge and action the critical results, then close the loop with the lab",
          evidenceRef = "critical_result:open:$openCritical",
        )
    }
    val overdueDischarges =
      admissionRepository
        .findAllByHospital(hospitalId)
        .count { it.status == "admitted" && it.expectedDischargeAt?.isBefore(now) == true }
    if (overdueDischarges > 0) {
      findings +=
        BlockerFinding(
          agentCode = "clinical",
          domain = "clinical",
          severity = "high",
          blocker = "$overdueDischarges admission(s) past the expected discharge time",
          ownerRole = "Ward clinician",
          impact = "Bed-days held and discharge pipeline blocked",
          recommendedAction = "Complete the discharge checklist and sign the summary or reset the expected date",
          evidenceRef = "admission:overdue_discharge:$overdueDischarges",
        )
    }
    return findings
  }

  private fun pharmacyFindings(hospitalId: UUID): List<BlockerFinding> {
    val findings = mutableListOf<BlockerFinding>()
    val active = pharmacyStockRepository.findByIsActiveTrue(hospitalId)
    val today = LocalDate.now()
    val expired = active.filter { it.expiryDate?.isBefore(today) == true && it.quantityOnHand > 0 }
    if (expired.isNotEmpty()) {
      findings +=
        BlockerFinding(
          agentCode = "pharmacy",
          domain = "pharmacy",
          severity = "critical",
          blocker = "${expired.size} expired stock batch(es) still on hand",
          ownerRole = "Chief pharmacist",
          impact = "Dispensing risk and wastage",
          recommendedAction = "Quarantine and write off expired batches, then reconcile the ledger",
          evidenceRef = "pharmacy:expired:${expired.size}",
        )
    }
    val low = active.filter { it.quantityOnHand <= it.reorderLevel }
    if (low.isNotEmpty()) {
      findings +=
        BlockerFinding(
          agentCode = "pharmacy",
          domain = "pharmacy",
          severity = "high",
          blocker = "${low.size} drug(s) at or below reorder level",
          ownerRole = "Pharmacy inventory lead",
          impact = "Stock-out risk for ward and OT orders",
          recommendedAction = "Approve reorder proposals or raise an indent for the affected drugs",
          evidenceRef = "pharmacy:low_stock:${low.size}",
        )
    }
    return findings
  }

  private fun facilitiesFindings(hospitalId: UUID): List<BlockerFinding> {
    val findings = mutableListOf<BlockerFinding>()
    val trays = cssdTrayRepository.findByHospitalIdOrderByCodeAsc(hospitalId)
    val unavailable = trays.filter { it.status != "AVAILABLE" }
    if (trays.isNotEmpty() && unavailable.isNotEmpty()) {
      findings +=
        BlockerFinding(
          agentCode = "facilities",
          domain = "facilities",
          severity = "critical",
          blocker = "CSSD tray shortage: ${unavailable.size} of ${trays.size} tray(s) unavailable",
          ownerRole = "CSSD supervisor",
          impact = "Sterile instrument sets missing for scheduled cases",
          recommendedAction = "Expedite the sterilization cycle and release the trays before the next case",
          evidenceRef = "cssd:trays_unavailable:${unavailable.size}",
        )
    }
    val openWorkOrders = workOrderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, "OPEN")
    if (openWorkOrders.isNotEmpty()) {
      findings +=
        BlockerFinding(
          agentCode = "facilities",
          domain = "facilities",
          severity = "high",
          blocker = "${openWorkOrders.size} open equipment work order(s)",
          ownerRole = "Biomedical engineering lead",
          impact = "Equipment downtime and case cancellations",
          recommendedAction = "Prioritize corrective work orders on critical equipment and record downtime",
          evidenceRef = "work_order:open:${openWorkOrders.size}",
        )
    }
    val downAssets = assetRepository.findByHospitalIdOrderByCodeAsc(hospitalId).filter { it.status == "DOWN" }
    if (downAssets.isNotEmpty()) {
      findings +=
        BlockerFinding(
          agentCode = "facilities",
          domain = "facilities",
          severity = "high",
          blocker = "${downAssets.size} asset(s) marked down",
          ownerRole = "Biomedical engineering lead",
          impact = "Reduced equipment availability",
          recommendedAction = "Repair or replace the affected assets and update the asset register",
          evidenceRef = "asset:down:${downAssets.size}",
        )
    }
    return findings
  }

  private fun procurementFindings(hospitalId: UUID): List<BlockerFinding> {
    val pending =
      requisitionRepository
        .findByHospitalIdOrderByCreatedAtDesc(hospitalId)
        .filter { it.status == "SUBMITTED" }
    if (pending.isEmpty()) return emptyList()
    return listOf(
      BlockerFinding(
        agentCode = "procurement",
        domain = "procurement",
        severity = "high",
        blocker = "${pending.size} requisition(s) awaiting approval",
        ownerRole = "Procurement officer",
        impact = "Supply lead time and stock-out risk",
        recommendedAction = "Approve or reject the pending requisitions and issue purchase orders",
        evidenceRef = "requisition:pending:${pending.size}",
      ),
    )
  }

  private fun securityFindings(hospitalId: UUID): List<BlockerFinding> {
    val open =
      incidentRepository
        .findByHospitalIdAndStatusOrderByReportedAtDesc(hospitalId, "OPEN")
        .filter { it.severity == "high" || it.severity == "critical" }
    if (open.isEmpty()) return emptyList()
    return listOf(
      BlockerFinding(
        agentCode = "security",
        domain = "security",
        severity = "high",
        blocker = "${open.size} open high-severity incident(s)",
        ownerRole = "Security officer",
        impact = "Unresolved access or integrity risk",
        recommendedAction = "Investigate the incidents and record the resolution in the register",
        evidenceRef = "incident:open_high:${open.size}",
      ),
    )
  }
}
