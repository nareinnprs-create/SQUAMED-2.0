package com.squamed.core.encounter.api

import com.squamed.core.encounter.domain.ClinicalNote
import com.squamed.core.encounter.domain.Diagnosis
import com.squamed.core.encounter.domain.Encounter
import com.squamed.core.encounter.domain.OrderEntry
import com.squamed.core.encounter.domain.Prescription
import com.squamed.core.encounter.domain.VitalSign
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class StartEncounterDto(
  val patientId: UUID,
  val visitId: UUID? = null,
  val doctorId: UUID? = null,
  val departmentId: UUID? = null,
  val reason: String? = null,
  val chiefComplaint: String? = null,
)

data class VitalSignRequestDto(
  val recordedAt: Instant? = null,
  val recordedBy: UUID? = null,
  val bloodPressureSystolic: Int? = null,
  val bloodPressureDiastolic: Int? = null,
  val heartRate: Int? = null,
  val respiratoryRate: Int? = null,
  val temperatureC: BigDecimal? = null,
  val spo2Percent: Int? = null,
  val heightCm: BigDecimal? = null,
  val weightKg: BigDecimal? = null,
  val painScore: Int? = null,
  val notes: String? = null,
)

data class ClinicalNoteRequestDto(
  val noteType: String? = null,
  val title: String? = null,
  val subjective: String? = null,
  val objective: String? = null,
  val assessment: String? = null,
  val plan: String? = null,
)

data class DiagnosisRequestDto(
  val code: String,
  val system: String? = null,
  val name: String,
  val type: String? = null,
  val notes: String? = null,
)

data class OrderRequestDto(
  val orderType: String,
  val orderCode: String? = null,
  val orderName: String,
  val priority: String? = null,
  val instructions: String? = null,
  val orderedBy: UUID? = null,
  val orderedAt: Instant? = null,
)

data class PrescriptionRequestDto(
  val drugId: UUID? = null,
  val drugName: String,
  val strength: String? = null,
  val form: String? = null,
  val dose: String? = null,
  val frequency: String? = null,
  val route: String? = null,
  val duration: String? = null,
  val instructions: String? = null,
  val quantity: Int? = null,
  val isPrn: Boolean? = null,
  val prescribedBy: UUID? = null,
  val prescribedAt: Instant? = null,
)

class SignNoteRequest

data class EncounterView(
  val id: UUID?,
  val hospitalId: UUID?,
  val visitId: UUID?,
  val patientId: UUID,
  val doctorId: UUID?,
  val departmentId: UUID?,
  val encounterType: String,
  val status: String,
  val reason: String?,
  val chiefComplaint: String?,
  val startedAt: Instant?,
  val endedAt: Instant?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Encounter) =
      EncounterView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        visitId = entity.visitId,
        patientId = entity.patientId,
        doctorId = entity.doctorId,
        departmentId = entity.departmentId,
        encounterType = entity.encounterType,
        status = entity.status,
        reason = entity.reason,
        chiefComplaint = entity.chiefComplaint,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class VitalSignView(
  val id: UUID?,
  val encounterId: UUID?,
  val patientId: UUID,
  val recordedAt: Instant?,
  val recordedBy: UUID?,
  val bloodPressureSystolic: Int?,
  val bloodPressureDiastolic: Int?,
  val heartRate: Int?,
  val respiratoryRate: Int?,
  val temperatureC: BigDecimal?,
  val spo2Percent: Int?,
  val heightCm: BigDecimal?,
  val weightKg: BigDecimal?,
  val bmi: BigDecimal?,
  val painScore: Int?,
  val notes: String?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: VitalSign) =
      VitalSignView(
        id = entity.id,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        recordedAt = entity.recordedAt,
        recordedBy = entity.recordedBy,
        bloodPressureSystolic = entity.bloodPressureSystolic,
        bloodPressureDiastolic = entity.bloodPressureDiastolic,
        heartRate = entity.heartRate,
        respiratoryRate = entity.respiratoryRate,
        temperatureC = entity.temperatureC,
        spo2Percent = entity.spo2Percent,
        heightCm = entity.heightCm,
        weightKg = entity.weightKg,
        bmi = entity.bmi,
        painScore = entity.painScore,
        notes = entity.notes,
        createdAt = entity.createdAt,
      )
  }
}

data class ClinicalNoteView(
  val id: UUID?,
  val encounterId: UUID,
  val patientId: UUID,
  val noteType: String,
  val title: String?,
  val subjective: String?,
  val objective: String?,
  val assessment: String?,
  val plan: String?,
  val isSigned: Boolean,
  val signedAt: Instant?,
  val signedBy: UUID?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: ClinicalNote) =
      ClinicalNoteView(
        id = entity.id,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        noteType = entity.noteType,
        title = entity.title,
        subjective = entity.subjective,
        objective = entity.objective,
        assessment = entity.assessment,
        plan = entity.plan,
        isSigned = entity.isSigned,
        signedAt = entity.signedAt,
        signedBy = entity.signedBy,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class DiagnosisView(
  val id: UUID?,
  val encounterId: UUID?,
  val patientId: UUID,
  val code: String,
  val system: String,
  val name: String,
  val type: String,
  val notes: String?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Diagnosis) =
      DiagnosisView(
        id = entity.id,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        code = entity.code,
        system = entity.system,
        name = entity.name,
        type = entity.type,
        notes = entity.notes,
        createdAt = entity.createdAt,
      )
  }
}

data class OrderView(
  val id: UUID?,
  val hospitalId: UUID?,
  val encounterId: UUID?,
  val patientId: UUID,
  val orderType: String,
  val orderCode: String?,
  val orderName: String,
  val status: String,
  val priority: String,
  val instructions: String?,
  val orderedBy: UUID?,
  val orderedAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: OrderEntry) =
      OrderView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        orderType = entity.orderType,
        orderCode = entity.orderCode,
        orderName = entity.orderName,
        status = entity.status,
        priority = entity.priority,
        instructions = entity.instructions,
        orderedBy = entity.orderedBy,
        orderedAt = entity.orderedAt,
        createdAt = entity.createdAt,
      )
  }
}

data class PrescriptionView(
  val id: UUID?,
  val encounterId: UUID?,
  val patientId: UUID,
  val drugId: UUID?,
  val drugName: String,
  val strength: String?,
  val form: String?,
  val dose: String?,
  val frequency: String?,
  val route: String?,
  val duration: String?,
  val instructions: String?,
  val quantity: Int?,
  val isPrn: Boolean,
  val status: String,
  val prescribedBy: UUID?,
  val prescribedAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Prescription) =
      PrescriptionView(
        id = entity.id,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        drugId = entity.drugId,
        drugName = entity.drugName,
        strength = entity.strength,
        form = entity.form,
        dose = entity.dose,
        frequency = entity.frequency,
        route = entity.route,
        duration = entity.duration,
        instructions = entity.instructions,
        quantity = entity.quantity,
        isPrn = entity.isPrn,
        status = entity.status,
        prescribedBy = entity.prescribedBy,
        prescribedAt = entity.prescribedAt,
        createdAt = entity.createdAt,
      )
  }
}

data class EncounterDetailView(
  val encounter: EncounterView,
  val latestVitals: VitalSignView? = null,
  val notes: List<ClinicalNoteView> = emptyList(),
  val diagnoses: List<DiagnosisView> = emptyList(),
  val orders: List<OrderView> = emptyList(),
  val prescriptions: List<PrescriptionView> = emptyList(),
)
