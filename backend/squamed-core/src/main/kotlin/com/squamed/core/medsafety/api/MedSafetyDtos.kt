package com.squamed.core.medsafety.api

import com.squamed.core.medsafety.domain.SafetyOverride
import java.time.Instant
import java.util.UUID

data class MedSafetyCheckDto(
  val patientId: UUID,
  val drugIds: List<UUID> = emptyList(),
  // Single daily dose in mg applied against each batch drug's dose rule (null skips dose legs).
  val doseMgPerDay: Double? = null,
  // Active prescription to leave out of duplicate/interaction context (dispense self-fire guard).
  val excludePrescriptionId: UUID? = null,
)

data class MedSafetyFindingView(
  val type: String,
  val severity: String,
  val drugA: String? = null,
  val drugB: String? = null,
  val message: String,
  // Provenance: distinct rule sources joined ("DATASET+RULE"); null for non-interaction findings.
  val source: String? = null,
  val confidence: Double? = null,
  val ruleId: UUID? = null,
)

data class MedSafetyResultView(
  val patientId: UUID,
  val findings: List<MedSafetyFindingView>,
  val highestSeverity: String,
)

data class MedSafetyOverrideDto(
  val patientId: UUID,
  val drugCode: String? = null,
  val findingType: String,
  val severity: String,
  val reason: String,
)

data class MedSafetyOverrideView(
  val id: UUID,
  val patientId: UUID,
  val drugCode: String?,
  val findingType: String,
  val severity: String,
  val reason: String,
  val occurredAt: Instant,
) {
  companion object {
    fun from(row: SafetyOverride) =
      MedSafetyOverrideView(
        row.id!!,
        row.patientId,
        row.drugCode,
        row.findingType,
        row.severity,
        row.reason,
        row.occurredAt,
      )
  }
}

data class OverrideKpiRowView(
  val findingType: String,
  val reason: String,
  val count: Long,
)
