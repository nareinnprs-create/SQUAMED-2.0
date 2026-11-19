package com.squamed.core.medsafety.service

import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.encounter.repository.VitalSignRepository
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.laboratory.repository.LabTestRequestRepository
import com.squamed.core.masterdata.domain.Drug
import com.squamed.core.masterdata.repository.DrugRepository
import com.squamed.core.medsafety.api.MedSafetyCheckDto
import com.squamed.core.medsafety.api.MedSafetyFindingView
import com.squamed.core.medsafety.api.MedSafetyOverrideDto
import com.squamed.core.medsafety.api.MedSafetyOverrideView
import com.squamed.core.medsafety.api.MedSafetyResultView
import com.squamed.core.medsafety.api.OverrideKpiRowView
import com.squamed.core.medsafety.repository.AllergenClassMapRepository
import com.squamed.core.medsafety.repository.DoseRuleRepository
import com.squamed.core.medsafety.repository.InteractionRuleRepository
import com.squamed.core.medsafety.repository.SafetyOverrideRepository
import com.squamed.core.patient.domain.Allergy
import com.squamed.core.patient.repository.AllergyRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

@Service
class MedicationSafetyService(
  private val interactionRuleRepository: InteractionRuleRepository,
  private val doseRuleRepository: DoseRuleRepository,
  private val allergenClassMapRepository: AllergenClassMapRepository,
  private val safetyOverrideRepository: SafetyOverrideRepository,
  private val drugRepository: DrugRepository,
  private val allergyRepository: AllergyRepository,
  private val prescriptionRepository: PrescriptionRepository,
  private val vitalSignRepository: VitalSignRepository,
  private val patientRecordRepository: PatientRecordRepository,
  private val labTestRequestRepository: LabTestRequestRepository,
  private val tenantResolver: TenantResolver,
  private val meterRegistry: MeterRegistry,
  // §5 risk 2: BLOCK fires only at >= confidence threshold; below it the BLOCK downgrades to WARN.
  @Value("\${squamed.med-safety.block-confidence-threshold:0.90}")
  private val blockConfidenceThreshold: Double,
) {
  private val severityRank = mapOf("NONE" to 0, "INFO" to 1, "WARN" to 2, "BLOCK" to 3)

  private fun maxSeverity(findings: List<MedSafetyFindingView>): String =
    findings.maxOfOrNull { severityRank[it.severity] ?: 0 }?.let { rank -> severityRank.entries.first { it.value == rank }.key } ?: "NONE"

  /** Active master-data drug by id, else by free-text name against code/generic/brand. */
  fun resolveDrug(
    drugId: UUID?,
    name: String?,
  ): Drug? =
    drugId?.let { drugRepository.findById(it).orElse(null)?.takeIf { d -> d.isActive } }
      ?: name?.trim()?.takeIf { it.isNotEmpty() }?.let { n ->
        drugRepository.findFirstByCodeIgnoreCaseAndIsActiveTrue(n)
          ?: drugRepository.findFirstByGenericNameIgnoreCaseAndIsActiveTrue(n)
          ?: drugRepository.findFirstByBrandNameIgnoreCaseAndIsActiveTrue(n)
      }

  fun screenForPrescribing(
    patientId: UUID,
    drugId: UUID,
  ): MedSafetyResultView = check(MedSafetyCheckDto(patientId = patientId, drugIds = listOf(drugId)))

  fun screenForDispense(
    patientId: UUID,
    drugId: UUID,
    excludePrescriptionId: UUID?,
  ): MedSafetyResultView = check(MedSafetyCheckDto(patientId = patientId, drugIds = listOf(drugId), excludePrescriptionId = excludePrescriptionId))

  @Transactional(readOnly = true)
  fun check(dto: MedSafetyCheckDto): MedSafetyResultView {
    val batch = dto.drugIds.mapNotNull { resolveDrug(it, null) }.distinctBy { it.id }
    val findings = mutableListOf<MedSafetyFindingView>()

    // Context: the patient's other active prescriptions (the one being re-checked is excluded
    // so a dispense never fires against itself).
    val contextPrescriptions =
      prescriptionRepository
        .findByPatientId(dto.patientId)
        .filter { it.status == "prescribed" && it.id != dto.excludePrescriptionId }
    val contextDrugs =
      contextPrescriptions
        .mapNotNull { resolveDrug(it.drugId, it.drugName) }
        .distinctBy { it.code }
        .filter { ctx -> batch.none { it.code.equals(ctx.code, ignoreCase = true) } }

    findings += allergyFindings(dto.patientId, batch)
    findings += interactionFindings(batch, contextDrugs)
    findings += duplicateFindings(dto.patientId, dto.excludePrescriptionId, batch, contextDrugs, contextPrescriptions)
    findings += doseFindings(dto, batch)
    findings += renalFindings(dto.patientId, batch)

    return MedSafetyResultView(dto.patientId, findings, maxSeverity(findings))
  }

  // -- allergy: ingredient-level matching on tokens + class expansion + >=4-char substring (01.04 parity) --

  private fun tokensOf(vararg texts: String?): Set<String> =
    texts
      .filterNotNull()
      .flatMap { it.lowercase().split(Regex("[^a-z0-9]+")) }
      .filter { it.length >= 4 }
      .toSet()

  private fun allergyFindings(
    patientId: UUID,
    batch: List<Drug>,
  ): List<MedSafetyFindingView> {
    if (batch.isEmpty()) return emptyList()
    // SAFETY L49: the NKA sentinel counts as documented and never matches a drug.
    val allergies =
      allergyRepository
        .findByPatientIdAndIsActiveTrue(patientId)
        .filterNot { it.allergen.trim().equals(Allergy.NKA_ALLERGEN, ignoreCase = true) }
    if (allergies.isEmpty()) return emptyList()
    val classMapByToken = allergenClassMapRepository.findAll().groupBy({ it.allergenToken.lowercase() }, { it.ingredientToken.lowercase() })
    return allergies.flatMap { allergy ->
      val allergenRaw = allergy.allergen.trim().lowercase()
      val allergenTokens = tokensOf(allergy.allergen)
      val expanded = allergenTokens.flatMap { classMapByToken[it].orEmpty() }.toSet()
      batch.mapNotNull { drug ->
        val drugTokens = tokensOf(drug.code, drug.name, drug.genericName, drug.brandName, drug.ingredients)
        val matched =
          drugTokens.any { token ->
            token in allergenTokens || token in expanded ||
              (token.length >= 4 && allergenRaw.length >= 4 && (allergenRaw.contains(token) || token.contains(allergenRaw)))
          }
        if (matched) {
          MedSafetyFindingView(
            type = "ALLERGY",
            // §5 rule 4: documented block-severity allergy is not overridable at the gate.
            severity = if (allergy.severity.equals("block", ignoreCase = true)) "BLOCK" else "WARN",
            drugA = drug.code,
            message = "Allergy match: '${allergy.allergen}' vs ${drug.code}",
          )
        } else {
          null
        }
      }
    }
  }

  // -- interactions: pair rules in either orientation, merged across sources --

  private fun interactionFindings(
    batch: List<Drug>,
    contextDrugs: List<Drug>,
  ): List<MedSafetyFindingView> {
    if (batch.isEmpty()) return emptyList()
    val rules = interactionRuleRepository.findByIsActiveTrue()
    if (rules.isEmpty()) return emptyList()
    val byPair = HashMap<Pair<String, String>, MutableList<InteractionRuleInternal>>()
    for (r in rules) {
      val a = r.drugACode.trim().lowercase()
      val b = r.drugBCode.trim().lowercase()
      val key = if (a <= b) a to b else b to a
      byPair.getOrPut(key) { mutableListOf() }.add(InteractionRuleInternal(r.severity, r.confidence, r.source, r.description, r.id))
    }
    val all = batch + contextDrugs
    val findings = mutableListOf<MedSafetyFindingView>()
    for (i in all.indices) {
      for (j in i + 1 until all.size) {
        val a = all[i]
        val b = all[j]
        if (a.code.equals(b.code, ignoreCase = true)) continue
        // Context-vs-context pairs are not actionable now; only pairs involving a batch drug fire.
        val involvesBatch = i < batch.size || j < batch.size
        if (!involvesBatch) continue
        val x = a.code.trim().lowercase()
        val y = b.code.trim().lowercase()
        val candidates = byPair[if (x <= y) x to y else y to x] ?: continue
        val best = candidates.maxByOrNull { it.confidence } ?: continue
        var severity = candidates.maxByOrNull { severityRank[it.severity] ?: 0 }!!.severity
        if (severity == "BLOCK" && best.confidence < blockConfidenceThreshold) severity = "WARN"
        val sources =
          candidates
            .map { it.source }
            .distinct()
            .sorted()
            .joinToString("+")
        findings +=
          MedSafetyFindingView(
            type = "INTERACTION",
            severity = severity,
            drugA = a.code,
            drugB = b.code,
            message = best.description ?: "Interaction between ${a.code} and ${b.code}",
            source = sources,
            confidence = best.confidence,
            ruleId = best.ruleId,
          )
      }
    }
    return findings.distinctBy { Triple(it.drugA, it.drugB, it.severity) }
  }

  // -- duplicate therapy: same generic or shared ingredient token vs batch/active prescriptions --

  private fun duplicateFindings(
    patientId: UUID,
    excludePrescriptionId: UUID?,
    batch: List<Drug>,
    contextDrugs: List<Drug>,
    contextPrescriptions: List<com.squamed.core.encounter.domain.Prescription>,
  ): List<MedSafetyFindingView> {
    if (batch.isEmpty()) return emptyList()

    data class Entry(
      val label: String,
      val code: String?,
      val generic: String?,
      val ingredients: Set<String>,
    )

    val batchEntries = batch.map { Entry(it.code, it.code, it.genericName, tokensOf(it.genericName, it.ingredients)) }
    val rxEntries =
      contextDrugs
        .filter { ctx -> batch.none { it.code.equals(ctx.code, ignoreCase = true) } }
        .map { Entry(it.code, it.code, it.genericName, tokensOf(it.genericName, it.ingredients)) } +
        contextPrescriptions
          .filter { rx -> rx.id != excludePrescriptionId && rx.drugId == null }
          .map { Entry(it.drugName, null, null, tokensOf(it.drugName)) }
    val combined = batchEntries + rxEntries
    val findings = mutableListOf<MedSafetyFindingView>()
    for (i in combined.indices) {
      for (j in i + 1 until combined.size) {
        val involvesBatch = i < batchEntries.size || j < batchEntries.size
        if (!involvesBatch) continue
        val a = combined[i]
        val b = combined[j]
        val duplicate =
          (a.code != null && a.code.equals(b.code, ignoreCase = true)) ||
            (!a.generic.isNullOrBlank() && a.generic.equals(b.generic, ignoreCase = true)) ||
            a.ingredients.intersect(b.ingredients).isNotEmpty()
        if (duplicate) {
          findings +=
            MedSafetyFindingView(
              type = "DUPLICATE",
              severity = "WARN",
              drugA = a.label,
              drugB = b.label,
              message = "Duplicate therapy: ${a.label} and ${b.label}",
            )
        }
      }
    }
    return findings.distinctBy { it.drugA to it.drugB }
  }

  // -- max dose: pediatric weight-based cap (age < 12 + latest weight) or adult daily cap --

  private fun doseFindings(
    dto: MedSafetyCheckDto,
    batch: List<Drug>,
  ): List<MedSafetyFindingView> {
    val dose = dto.doseMgPerDay ?: return emptyList()
    if (batch.isEmpty()) return emptyList()
    val rules = doseRuleRepository.findByIsActiveTrue().associateBy { it.drugCode.trim().lowercase() }
    val patient = patientRecordRepository.findById(dto.patientId).orElse(null)
    val ageYears = patient?.dateOfBirth?.let { Period.between(it, LocalDate.now()).years }
    val weightKg = vitalSignRepository.findTopByPatientIdOrderByRecordedAtDesc(dto.patientId)?.weightKg?.toDouble()
    return batch.mapNotNull { drug ->
      val rule = rules[drug.code.trim().lowercase()] ?: return@mapNotNull null
      val pediatricLimit =
        if (ageYears != null && ageYears < 12 && weightKg != null && rule.maxDailyMgPerKg != null) weightKg * rule.maxDailyMgPerKg!! else null
      val limit = pediatricLimit ?: rule.adultMaxDailyMg ?: return@mapNotNull null
      if (dose > limit) {
        MedSafetyFindingView(
          type = "MAX_DOSE",
          severity = "WARN",
          drugA = drug.code,
          message = "Daily dose $dose mg exceeds limit $limit mg for ${drug.code}",
        )
      } else {
        null
      }
    }
  }

  // -- renal: drug flagged for renal adjustment + latest released eGFR below its threshold --

  private fun renalFindings(
    patientId: UUID,
    batch: List<Drug>,
  ): List<MedSafetyFindingView> {
    if (batch.isEmpty()) return emptyList()
    val rules =
      doseRuleRepository.findByIsActiveTrue().associateBy { it.drugCode.trim().lowercase() }
    val flagged = batch.filter { rules[it.code.trim().lowercase()]?.renalFlag == true }
    if (flagged.isEmpty()) return emptyList()
    val egfr =
      labTestRequestRepository
        .findReleasedResults(patientId, "eGFR")
        .firstOrNull()
        ?.resultValue
        ?.trim()
        ?.toDoubleOrNull() ?: return emptyList()
    return flagged.mapNotNull { drug ->
      val rule = rules[drug.code.trim().lowercase()]!!
      val threshold = rule.renalEgfrThreshold
      if (threshold != null && egfr < threshold) {
        MedSafetyFindingView(
          type = "RENAL",
          severity = "WARN",
          drugA = drug.code,
          message = "eGFR $egfr below renal threshold $threshold for ${drug.code}",
        )
      } else {
        null
      }
    }
  }

  // -- overrides + KPIs --

  @Transactional
  fun recordOverride(
    dto: MedSafetyOverrideDto,
    overriddenBy: UUID?,
    hospitalId: UUID?,
  ): MedSafetyOverrideView {
    val reason = dto.reason.trim()
    if (reason.isEmpty()) throw IllegalArgumentException("OVERRIDE_REASON_REQUIRED")
    val row =
      safetyOverrideRepository.save(
        com.squamed.core.medsafety.domain.SafetyOverride(
          hospitalId = tenantResolver.pinOrDefault(null, hospitalId),
          patientId = dto.patientId,
          drugCode = dto.drugCode,
          findingType = dto.findingType,
          severity = dto.severity,
          reason = reason,
          overriddenBy = overriddenBy,
        ),
      )
    meterRegistry.counter("squamed.medsafety.overrides", "type", dto.findingType).increment()
    return MedSafetyOverrideView.from(row)
  }

  @Transactional(readOnly = true)
  fun overrideKpis(
    days: Int,
    hospitalId: UUID?,
  ): List<OverrideKpiRowView> {
    val since = Instant.now().minus(Duration.ofDays(days.toLong().coerceAtLeast(1)))
    val rows =
      if (hospitalId != null) {
        safetyOverrideRepository.findByHospitalIdAndOccurredAtAfterOrderByOccurredAtDesc(hospitalId, since)
      } else {
        safetyOverrideRepository.findByOccurredAtAfterOrderByOccurredAtDesc(since)
      }
    return rows
      .groupBy { it.findingType to it.reason }
      .map { (key, group) -> OverrideKpiRowView(key.first, key.second, group.size.toLong()) }
      .sortedByDescending { it.count }
  }

  /** Merged-rule view used only inside the interaction merge. */
  private data class InteractionRuleInternal(
    val severity: String,
    val confidence: Double,
    val source: String,
    val description: String?,
    val ruleId: UUID?,
  )
}
