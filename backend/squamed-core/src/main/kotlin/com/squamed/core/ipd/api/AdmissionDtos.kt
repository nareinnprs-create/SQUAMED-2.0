package com.squamed.core.ipd.api

import com.squamed.core.ipd.domain.Admission
import com.squamed.core.ipd.domain.AdmissionTransfer
import com.squamed.core.ipd.domain.Attendant
import com.squamed.core.ipd.domain.DischargeSummary
import java.time.Instant
import java.util.UUID

data class AdmissionCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionType: String? = null,
  val bedId: UUID? = null,
  val wardId: UUID? = null,
  val roomId: UUID? = null,
  val attendingDoctorId: UUID? = null,
  val admissionDiagnosis: String? = null,
  val source: String? = null,
)

data class AdmissionTransferDto(
  val toBedId: UUID? = null,
  val reason: String? = null,
)

data class AdmissionDischargeDto(
  val dischargeType: String? = null,
  val dischargeNotes: String? = null,
  val summaryText: String? = null,
  val followUpPlan: String? = null,
  val medicationsAtDischarge: String? = null,
  val instructions: String? = null,
)

data class DischargeSummaryUpsertDto(
  val summaryText: String? = null,
  val followUpPlan: String? = null,
  val medicationsAtDischarge: String? = null,
  val instructions: String? = null,
)

data class AttendantCreateDto(
  val name: String? = null,
  val relationship: String? = null,
  val phone: String? = null,
  val notes: String? = null,
)

data class AdmissionView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionType: String,
  val status: String,
  val bedId: UUID?,
  val wardId: UUID?,
  val roomId: UUID?,
  val attendingDoctorId: UUID?,
  val admissionDiagnosis: String?,
  val source: String,
  val admittedAt: Instant?,
  val admittedBy: UUID?,
  val dischargedAt: Instant?,
  val dischargeType: String?,
  val dischargeNotes: String?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Admission) =
      AdmissionView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionType = entity.admissionType,
        status = entity.status,
        bedId = entity.bedId,
        wardId = entity.wardId,
        roomId = entity.roomId,
        attendingDoctorId = entity.attendingDoctorId,
        admissionDiagnosis = entity.admissionDiagnosis,
        source = entity.source,
        admittedAt = entity.admittedAt,
        admittedBy = entity.admittedBy,
        dischargedAt = entity.dischargedAt,
        dischargeType = entity.dischargeType,
        dischargeNotes = entity.dischargeNotes,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class AdmissionTransferView(
  val id: UUID?,
  val admissionId: UUID?,
  val fromBedId: UUID?,
  val toBedId: UUID?,
  val reason: String?,
  val transferredAt: Instant?,
  val transferredBy: UUID?,
) {
  companion object {
    fun from(entity: AdmissionTransfer) =
      AdmissionTransferView(
        id = entity.id,
        admissionId = entity.admissionId,
        fromBedId = entity.fromBedId,
        toBedId = entity.toBedId,
        reason = entity.reason,
        transferredAt = entity.transferredAt,
        transferredBy = entity.transferredBy,
      )
  }
}

data class DischargeSummaryView(
  val id: UUID?,
  val admissionId: UUID?,
  val summaryText: String?,
  val followUpPlan: String?,
  val medicationsAtDischarge: String?,
  val instructions: String?,
  val preparedBy: UUID?,
  val preparedAt: Instant?,
) {
  companion object {
    fun from(entity: DischargeSummary) =
      DischargeSummaryView(
        id = entity.id,
        admissionId = entity.admissionId,
        summaryText = entity.summaryText,
        followUpPlan = entity.followUpPlan,
        medicationsAtDischarge = entity.medicationsAtDischarge,
        instructions = entity.instructions,
        preparedBy = entity.preparedBy,
        preparedAt = entity.preparedAt,
      )
  }
}

data class AttendantView(
  val id: UUID?,
  val admissionId: UUID?,
  val name: String,
  val relationship: String?,
  val phone: String?,
  val notes: String?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Attendant) =
      AttendantView(
        id = entity.id,
        admissionId = entity.admissionId,
        name = entity.name,
        relationship = entity.relationship,
        phone = entity.phone,
        notes = entity.notes,
        createdAt = entity.createdAt,
      )
  }
}
