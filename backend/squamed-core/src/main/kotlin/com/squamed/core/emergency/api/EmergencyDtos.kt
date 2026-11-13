package com.squamed.core.emergency.api

import com.squamed.core.emergency.domain.EmergencyRegistration
import com.squamed.core.emergency.domain.TraumaWorkflow
import com.squamed.core.emergency.domain.TriageAssessment
import java.time.Instant
import java.util.UUID

data class EmergencyRegisterDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val arrivalMode: String? = null,
  val arrivalAt: Instant? = null,
  val presentingComplaint: String? = null,
)

data class TriageCreateDto(
  val esiLevel: Int? = null,
  val triageCategory: String? = null,
  val painScale: Int? = null,
  val vitalTriggers: Boolean? = null,
  val acuityNotes: String? = null,
)

data class TraumaSurveyDto(
  val surveyPhase: String? = null,
  val airway: String? = null,
  val breathing: String? = null,
  val circulation: String? = null,
  val disability: String? = null,
  val exposure: String? = null,
  val surveyNotes: String? = null,
)

data class EmergencyStatusDto(
  val status: String? = null,
  val admittedAdmissionId: UUID? = null,
)

data class EmergencyRegistrationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val arrivalMode: String,
  val arrivalAt: Instant?,
  val presentingComplaint: String?,
  val status: String,
  val triageEsi: Int?,
  val priority: String,
  val registeredBy: UUID?,
  val admittedAdmissionId: UUID?,
  val dischargedAt: Instant?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: EmergencyRegistration) =
      EmergencyRegistrationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        arrivalMode = entity.arrivalMode,
        arrivalAt = entity.arrivalAt,
        presentingComplaint = entity.presentingComplaint,
        status = entity.status,
        triageEsi = entity.triageEsi,
        priority = entity.priority,
        registeredBy = entity.registeredBy,
        admittedAdmissionId = entity.admittedAdmissionId,
        dischargedAt = entity.dischargedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class TriageView(
  val id: UUID?,
  val hospitalId: UUID?,
  val registrationId: UUID?,
  val patientId: UUID?,
  val esiLevel: Int,
  val triageCategory: String?,
  val painScale: Int?,
  val vitalTriggers: Boolean,
  val acuityNotes: String?,
  val assessedBy: UUID?,
  val assessedAt: Instant?,
) {
  companion object {
    fun from(entity: TriageAssessment) =
      TriageView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        registrationId = entity.registrationId,
        patientId = entity.patientId,
        esiLevel = entity.esiLevel,
        triageCategory = entity.triageCategory,
        painScale = entity.painScale,
        vitalTriggers = entity.vitalTriggers,
        acuityNotes = entity.acuityNotes,
        assessedBy = entity.assessedBy,
        assessedAt = entity.assessedAt,
      )
  }
}

data class TraumaWorkflowView(
  val id: UUID?,
  val hospitalId: UUID?,
  val registrationId: UUID?,
  val patientId: UUID?,
  val surveyPhase: String,
  val airway: String?,
  val breathing: String?,
  val circulation: String?,
  val disability: String?,
  val exposure: String?,
  val surveyNotes: String?,
  val activatedBy: UUID?,
  val activatedAt: Instant?,
  val completedBy: UUID?,
  val completedAt: Instant?,
) {
  companion object {
    fun from(entity: TraumaWorkflow) =
      TraumaWorkflowView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        registrationId = entity.registrationId,
        patientId = entity.patientId,
        surveyPhase = entity.surveyPhase,
        airway = entity.airway,
        breathing = entity.breathing,
        circulation = entity.circulation,
        disability = entity.disability,
        exposure = entity.exposure,
        surveyNotes = entity.surveyNotes,
        activatedBy = entity.activatedBy,
        activatedAt = entity.activatedAt,
        completedBy = entity.completedBy,
        completedAt = entity.completedAt,
      )
  }
}
