package com.squamed.core.anaesthesia.api

import com.squamed.core.anaesthesia.domain.AnaesthesiaEvaluation
import com.squamed.core.anaesthesia.domain.AnaesthesiaMedication
import com.squamed.core.anaesthesia.domain.AnaesthesiaRecord
import com.squamed.core.anaesthesia.domain.IntraOpMonitoring
import com.squamed.core.anaesthesia.domain.PacuRecord
import com.squamed.core.anaesthesia.domain.RecoveryRecord
import java.time.Instant
import java.util.UUID

data class EvaluationCreateDto(
  val hospitalId: UUID? = null,
  val surgeryScheduleId: UUID? = null,
  val patientId: UUID? = null,
  val asaStatus: String? = null,
  val airwayAssessment: String? = null,
  val allergies: String? = null,
  val medicalConditions: String? = null,
  val medications: String? = null,
  val previousComplications: String? = null,
  val fastingHours: Int? = null,
  val labNotes: String? = null,
  val riskLevel: String? = null,
)

data class AnaesthesiaRecordCreateDto(
  val hospitalId: UUID? = null,
  val surgeryScheduleId: UUID? = null,
  val patientId: UUID? = null,
  val anaesthetistId: UUID? = null,
  val airwayDevice: String? = null,
  val intubationGrade: String? = null,
)

data class AnaesthesiaRecordStatusDto(
  val status: String? = null,
)

data class MonitoringCreateDto(
  val systolic: Int? = null,
  val diastolic: Int? = null,
  val heartRate: Int? = null,
  val oxygenSaturation: Int? = null,
  val etco2: Int? = null,
  val temperature: Double? = null,
  val hourlyUrineMl: Int? = null,
  val anaesthesiaDepth: String? = null,
  val notes: String? = null,
)

data class MedicationCreateDto(
  val medicationName: String? = null,
  val dose: String? = null,
  val route: String? = null,
  val notes: String? = null,
)

data class RecoveryCreateDto(
  val aldreteScore: Int? = null,
  val dischargeCriteriaMet: Boolean? = null,
  val observations: String? = null,
)

data class PacuCreateDto(
  val nurseAssignment: String? = null,
  val painScore: Int? = null,
  val nauseaScore: Int? = null,
  val bleedingNotes: String? = null,
  val oxygenSaturation: Int? = null,
  val heartRate: Int? = null,
  val systolic: Int? = null,
  val diastolic: Int? = null,
  val notes: String? = null,
)

data class EvaluationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val patientId: UUID?,
  val asaStatus: String,
  val airwayAssessment: String?,
  val allergies: String?,
  val medicalConditions: String?,
  val medications: String?,
  val previousComplications: String?,
  val fastingHours: Int?,
  val labNotes: String?,
  val riskLevel: String?,
  val status: String,
  val evaluatedBy: UUID?,
  val evaluatedAt: Instant?,
) {
  companion object {
    fun from(entity: AnaesthesiaEvaluation) =
      EvaluationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        patientId = entity.patientId,
        asaStatus = entity.asaStatus,
        airwayAssessment = entity.airwayAssessment,
        allergies = entity.allergies ?: "",
        medicalConditions = entity.medicalConditions ?: "",
        medications = entity.medications ?: "",
        previousComplications = entity.previousComplications ?: "",
        fastingHours = entity.fastingHours,
        labNotes = entity.labNotes,
        riskLevel = entity.riskLevel,
        status = entity.status,
        evaluatedBy = entity.evaluatedBy,
        evaluatedAt = entity.evaluatedAt,
      )
  }
}

data class AnaesthesiaRecordView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val patientId: UUID?,
  val anaesthetistId: UUID?,
  val airwayDevice: String?,
  val intubationGrade: String?,
  val status: String,
  val startedAt: Instant?,
  val endedAt: Instant?,
) {
  companion object {
    fun from(entity: AnaesthesiaRecord) =
      AnaesthesiaRecordView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        patientId = entity.patientId,
        anaesthetistId = entity.anaesthetistId,
        airwayDevice = entity.airwayDevice,
        intubationGrade = entity.intubationGrade,
        status = entity.status,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
      )
  }
}

data class MonitoringView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val anaesthesiaRecordId: UUID?,
  val patientId: UUID?,
  val monitoredAt: Instant?,
  val systolic: Int?,
  val diastolic: Int?,
  val heartRate: Int,
  val oxygenSaturation: Int?,
  val etco2: Int?,
  val temperature: Double?,
  val hourlyUrineMl: Int?,
  val anaesthesiaDepth: String?,
  val notes: String?,
  val recordedBy: UUID?,
) {
  companion object {
    fun from(entity: IntraOpMonitoring) =
      MonitoringView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        anaesthesiaRecordId = entity.anaesthesiaRecordId,
        patientId = entity.patientId,
        monitoredAt = entity.monitoredAt,
        systolic = entity.systolic,
        diastolic = entity.diastolic,
        heartRate = entity.heartRate,
        oxygenSaturation = entity.oxygenSaturation,
        etco2 = entity.etco2,
        temperature = entity.temperature,
        hourlyUrineMl = entity.hourlyUrineMl,
        anaesthesiaDepth = entity.anaesthesiaDepth,
        notes = entity.notes,
        recordedBy = entity.recordedBy,
      )
  }
}

data class MedicationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val anaesthesiaRecordId: UUID?,
  val patientId: UUID?,
  val medicationName: String,
  val dose: String?,
  val route: String?,
  val givenAt: Instant?,
  val givenBy: UUID?,
  val notes: String?,
) {
  companion object {
    fun from(entity: AnaesthesiaMedication) =
      MedicationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        anaesthesiaRecordId = entity.anaesthesiaRecordId,
        patientId = entity.patientId,
        medicationName = entity.medicationName,
        dose = entity.dose,
        route = entity.route,
        givenAt = entity.givenAt,
        givenBy = entity.givenBy,
        notes = entity.notes,
      )
  }
}

data class RecoveryView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val patientId: UUID?,
  val aldreteScore: Int,
  val dischargeCriteriaMet: Boolean,
  val recoveryStartedAt: Instant?,
  val recoveryEndedAt: Instant?,
  val observations: String?,
  val recordedBy: UUID?,
) {
  companion object {
    fun from(entity: RecoveryRecord) =
      RecoveryView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        patientId = entity.patientId,
        aldreteScore = entity.aldreteScore,
        dischargeCriteriaMet = entity.dischargeCriteriaMet,
        recoveryStartedAt = entity.recoveryStartedAt,
        recoveryEndedAt = entity.recoveryEndedAt,
        observations = entity.observations,
        recordedBy = entity.recordedBy,
      )
  }
}

data class PacuView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val patientId: UUID?,
  val nurseAssignment: String?,
  val painScore: Int?,
  val nauseaScore: Int?,
  val bleedingNotes: String?,
  val oxygenSaturation: Int?,
  val heartRate: Int?,
  val systolic: Int?,
  val diastolic: Int?,
  val notes: String?,
  val recordedBy: UUID?,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: PacuRecord) =
      PacuView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        patientId = entity.patientId,
        nurseAssignment = entity.nurseAssignment,
        painScore = entity.painScore,
        nauseaScore = entity.nauseaScore,
        bleedingNotes = entity.bleedingNotes,
        oxygenSaturation = entity.oxygenSaturation,
        heartRate = entity.heartRate,
        systolic = entity.systolic,
        diastolic = entity.diastolic,
        notes = entity.notes,
        recordedBy = entity.recordedBy,
        recordedAt = entity.recordedAt,
      )
  }
}
