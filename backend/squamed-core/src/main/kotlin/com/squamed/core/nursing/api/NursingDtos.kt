package com.squamed.core.nursing.api

import com.squamed.core.nursing.domain.MedicationAdministrationRecord
import com.squamed.core.nursing.domain.NursingAssessment
import com.squamed.core.nursing.domain.NursingCarePlan
import com.squamed.core.nursing.domain.NursingHandover
import com.squamed.core.nursing.domain.NursingNote
import com.squamed.core.nursing.domain.RiskAssessment
import com.squamed.core.nursing.domain.VitalsEntry
import java.time.Instant
import java.util.UUID

data class NursingAssessmentCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val assessmentType: String? = null,
  val findings: String? = null,
  val riskLevel: String? = null,
  // 04.03: optional configurable form + captured field values.
  val templateCode: String? = null,
  val fields: Map<String, String> = emptyMap(),
)

data class NursingCarePlanCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val title: String? = null,
  val goal: String? = null,
  val interventions: String? = null,
  val problem: String? = null,
)

data class NursingCarePlanStatusDto(
  val status: String? = null,
)

data class NursingNoteCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val noteType: String? = null,
  val content: String? = null,
)

data class NursingHandoverCreateDto(
  val hospitalId: UUID? = null,
  val wardId: UUID? = null,
  val shift: String? = null,
  val situation: String? = null,
  val background: String? = null,
  val assessment: String? = null,
  val recommendation: String? = null,
)

data class VitalsCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val systolic: Int? = null,
  val diastolic: Int? = null,
  val heartRate: Int? = null,
  val respiratoryRate: Int? = null,
  val temperature: Double? = null,
  val oxygenSaturation: Int? = null,
  val gcs: Int? = null,
  val recordedAt: Instant? = null,
)

data class MarCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val medicationName: String? = null,
  val dose: String? = null,
  val route: String? = null,
  val scheduledAt: Instant? = null,
)

data class MarStatusDto(
  val status: String? = null,
  val omittedReason: String? = null,
)

data class RiskAssessmentCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val riskType: String? = null,
  val score: Int? = null,
  val level: String? = null,
  // 04.03: derive the level from a configured scale.
  val scaleCode: String? = null,
)

data class NursingAssessmentView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val assessmentType: String,
  val findings: String?,
  val riskLevel: String?,
  val assessedBy: UUID?,
  val assessedAt: Instant?,
  val templateCode: String?,
  val fields: Map<String, String>,
) {
  companion object {
    fun from(entity: NursingAssessment) =
      NursingAssessmentView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        assessmentType = entity.assessmentType,
        findings = entity.findings,
        riskLevel = entity.riskLevel,
        assessedBy = entity.assessedBy,
        assessedAt = entity.assessedAt,
        templateCode = entity.templateCode,
        fields =
          entity.fieldsJson?.let { raw ->
            val node =
              com.fasterxml.jackson.databind
                .ObjectMapper()
                .readTree(raw)
            node
              .fieldNames()
              .asSequence()
              .associateWith { node[it].asText() }
          } ?: emptyMap(),
      )
  }
}

data class NursingCarePlanView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val title: String,
  val goal: String?,
  val interventions: String?,
  val status: String,
  val completedAt: Instant?,
  val createdAt: Instant?,
  val problem: String?,
  val templateCode: String?,
) {
  companion object {
    fun from(entity: NursingCarePlan) =
      NursingCarePlanView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        title = entity.title,
        goal = entity.goal,
        interventions = entity.interventions,
        status = entity.status,
        completedAt = entity.completedAt,
        createdAt = entity.createdAt,
        problem = entity.problem,
        templateCode = entity.templateCode,
      )
  }
}

data class NursingNoteView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val noteType: String,
  val content: String,
  val authoredBy: UUID?,
  val authoredAt: Instant?,
) {
  companion object {
    fun from(entity: NursingNote) =
      NursingNoteView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        noteType = entity.noteType,
        content = entity.content,
        authoredBy = entity.authoredBy,
        authoredAt = entity.authoredAt,
      )
  }
}

data class NursingHandoverView(
  val id: UUID?,
  val hospitalId: UUID?,
  val wardId: UUID?,
  val shift: String,
  val situation: String?,
  val background: String?,
  val assessment: String?,
  val recommendation: String?,
  val authoredBy: UUID?,
  val authoredAt: Instant?,
) {
  companion object {
    fun from(entity: NursingHandover) =
      NursingHandoverView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        wardId = entity.wardId,
        shift = entity.shift,
        situation = entity.situation,
        background = entity.background,
        assessment = entity.assessment,
        recommendation = entity.recommendation,
        authoredBy = entity.authoredBy,
        authoredAt = entity.authoredAt,
      )
  }
}

data class VitalsView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val systolic: Int?,
  val diastolic: Int?,
  val heartRate: Int?,
  val respiratoryRate: Int?,
  val temperature: Double?,
  val oxygenSaturation: Int?,
  val gcs: Int?,
  val recordedBy: UUID?,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: VitalsEntry) =
      VitalsView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        systolic = entity.systolic,
        diastolic = entity.diastolic,
        heartRate = entity.heartRate,
        respiratoryRate = entity.respiratoryRate,
        temperature = entity.temperature,
        oxygenSaturation = entity.oxygenSaturation,
        gcs = entity.gcs,
        recordedBy = entity.recordedBy,
        recordedAt = entity.recordedAt,
      )
  }
}

data class MarView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val medicationName: String,
  val dose: String?,
  val route: String?,
  val scheduledAt: Instant?,
  val administeredAt: Instant?,
  val administeredBy: UUID?,
  val omittedReason: String?,
  val status: String,
  val prescriptionId: UUID?,
  val drugCode: String?,
  val isPrn: Boolean,
  val highAlert: Boolean,
  val omissionCode: String?,
  val secondCheckerId: UUID?,
  val barcodeVerified: Boolean,
  val prnReason: String?,
) {
  companion object {
    fun from(entity: MedicationAdministrationRecord) =
      MarView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        medicationName = entity.medicationName,
        dose = entity.dose,
        route = entity.route,
        scheduledAt = entity.scheduledAt,
        administeredAt = entity.administeredAt,
        administeredBy = entity.administeredBy,
        omittedReason = entity.omittedReason,
        status = entity.status,
        prescriptionId = entity.prescriptionId,
        drugCode = entity.drugCode,
        isPrn = entity.isPrn,
        highAlert = entity.highAlert,
        omissionCode = entity.omissionCode,
        secondCheckerId = entity.secondCheckerId,
        barcodeVerified = entity.barcodeVerified,
        prnReason = entity.prnReason,
      )
  }
}

data class RiskAssessmentView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val riskType: String,
  val score: Int?,
  val level: String?,
  val assessedBy: UUID?,
  val assessedAt: Instant?,
  val scaleCode: String?,
) {
  companion object {
    fun from(entity: RiskAssessment) =
      RiskAssessmentView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        riskType = entity.riskType,
        score = entity.score,
        level = entity.level,
        assessedBy = entity.assessedBy,
        assessedAt = entity.assessedAt,
        scaleCode = entity.scaleCode,
      )
  }
}

// 04.02 eMAR surfaces.
data class MarScheduleDto(
  val prescriptionId: UUID,
  val hospitalId: UUID? = null,
  val admissionId: UUID? = null,
  val startAt: Instant? = null,
  val doses: Int? = null,
)

data class MarAdministerDto(
  val patientBarcode: String? = null,
  val drugBarcode: String? = null,
  val secondCheckerId: UUID? = null,
  val prnReason: String? = null,
)

data class MarOmitDto(
  val code: String,
  val reason: String? = null,
  val notifyDoctor: Boolean? = null,
)

// 04.03 assessments + care plans + risk scores.
data class AssessmentTemplateDto(
  val id: UUID? = null,
  val hospitalId: UUID? = null,
  val code: String,
  val name: String,
  val fields: List<String> = emptyList(),
  val isActive: Boolean = true,
) {
  companion object {
    fun from(entity: com.squamed.core.nursing.domain.AssessmentTemplate) =
      AssessmentTemplateDto(
        entity.id,
        entity.hospitalId,
        entity.code,
        entity.name,
        com.fasterxml.jackson.databind
          .ObjectMapper()
          .readTree(entity.fieldsJson)
          .map { it.asText() },
        entity.isActive,
      )
  }
}

data class CarePlanTemplateDto(
  val id: UUID? = null,
  val hospitalId: UUID? = null,
  val code: String,
  val name: String,
  val problem: String,
  val goal: String? = null,
  val interventions: String? = null,
  val isActive: Boolean = true,
) {
  companion object {
    fun from(entity: com.squamed.core.nursing.domain.CarePlanTemplate) =
      CarePlanTemplateDto(entity.id, entity.hospitalId, entity.code, entity.name, entity.problem, entity.goal, entity.interventions, entity.isActive)
  }
}

data class CarePlanApplyDto(
  val patientId: UUID,
  val templateCode: String,
  val hospitalId: UUID? = null,
  val admissionId: UUID? = null,
)

data class RiskScaleDto(
  val id: UUID? = null,
  val hospitalId: UUID? = null,
  val code: String,
  val name: String,
  val minScore: Int = 0,
  val lowMax: Int,
  val moderateMax: Int,
  val isActive: Boolean = true,
) {
  companion object {
    fun from(entity: com.squamed.core.nursing.domain.RiskScale) =
      RiskScaleDto(entity.id, entity.hospitalId, entity.code, entity.name, entity.minScore, entity.lowMax, entity.moderateMax, entity.isActive)
  }
}
