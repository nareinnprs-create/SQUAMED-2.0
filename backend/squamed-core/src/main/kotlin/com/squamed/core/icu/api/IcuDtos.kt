package com.squamed.core.icu.api

import com.squamed.core.icu.domain.IcuCriticalAlert
import com.squamed.core.icu.domain.IcuFluidBalance
import com.squamed.core.icu.domain.IcuInfusionRecord
import com.squamed.core.icu.domain.IcuVentilationRecord
import com.squamed.core.icu.domain.IcuVitalsEntry
import java.time.Instant
import java.util.UUID

data class IcuVitalsCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val systolic: Int? = null,
  val diastolic: Int? = null,
  val heartRate: Int? = null,
  val respiratoryRate: Int? = null,
  val temperature: Double? = null,
  val oxygenSaturation: Int? = null,
  val map: Int? = null,
  val etco2: Int? = null,
  val gcs: Int? = null,
  val recordedAt: Instant? = null,
)

data class FluidBalanceCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val inputMl: Double? = null,
  val outputMl: Double? = null,
  val inputType: String? = null,
  val outputType: String? = null,
  val balanceAt: Instant? = null,
)

data class VentilationCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val mode: String? = null,
  val fio2Percent: Int? = null,
  val peep: Double? = null,
  val tidalVolumeMl: Int? = null,
  val respiratoryRateSet: Int? = null,
  val oxygenSaturation: Int? = null,
  val recordedAt: Instant? = null,
)

data class InfusionCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val drugName: String? = null,
  val dose: String? = null,
  val rateMlPerHour: Double? = null,
  val route: String? = null,
  val startedAt: Instant? = null,
)

data class InfusionStatusDto(
  val status: String? = null,
)

data class CriticalAlertCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val alertType: String? = null,
  val severity: String? = null,
  val message: String? = null,
  val payload: String? = null,
)

data class CriticalAlertStatusDto(
  val status: String? = null,
)

data class IcuVitalsView(
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
  val map: Int?,
  val etco2: Int?,
  val gcs: Int?,
  val recordedBy: UUID?,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: IcuVitalsEntry) =
      IcuVitalsView(
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
        map = entity.map,
        etco2 = entity.etco2,
        gcs = entity.gcs,
        recordedBy = entity.recordedBy,
        recordedAt = entity.recordedAt,
      )
  }
}

data class FluidBalanceView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val inputMl: Double?,
  val outputMl: Double?,
  val inputType: String?,
  val outputType: String?,
  val balanceAt: Instant?,
  val recordedBy: UUID?,
) {
  companion object {
    fun from(entity: IcuFluidBalance) =
      FluidBalanceView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        inputMl = entity.inputMl,
        outputMl = entity.outputMl,
        inputType = entity.inputType,
        outputType = entity.outputType,
        balanceAt = entity.balanceAt,
        recordedBy = entity.recordedBy,
      )
  }
}

data class VentilationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val mode: String,
  val fio2Percent: Int?,
  val peep: Double?,
  val tidalVolumeMl: Int?,
  val respiratoryRateSet: Int?,
  val oxygenSaturation: Int?,
  val status: String,
  val recordedBy: UUID?,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: IcuVentilationRecord) =
      VentilationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        mode = entity.mode,
        fio2Percent = entity.fio2Percent,
        peep = entity.peep,
        tidalVolumeMl = entity.tidalVolumeMl,
        respiratoryRateSet = entity.respiratoryRateSet,
        oxygenSaturation = entity.oxygenSaturation,
        status = entity.status,
        recordedBy = entity.recordedBy,
        recordedAt = entity.recordedAt,
      )
  }
}

data class InfusionView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val drugName: String,
  val dose: String?,
  val rateMlPerHour: Double?,
  val route: String?,
  val startedAt: Instant?,
  val stoppedAt: Instant?,
  val status: String,
  val recordedBy: UUID?,
) {
  companion object {
    fun from(entity: IcuInfusionRecord) =
      InfusionView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        drugName = entity.drugName,
        dose = entity.dose,
        rateMlPerHour = entity.rateMlPerHour,
        route = entity.route,
        startedAt = entity.startedAt,
        stoppedAt = entity.stoppedAt,
        status = entity.status,
        recordedBy = entity.recordedBy,
      )
  }
}

data class CriticalAlertView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val alertType: String,
  val severity: String,
  val message: String?,
  val payload: String?,
  val triggeredAt: Instant?,
  val acknowledgedBy: UUID?,
  val acknowledgedAt: Instant?,
  val status: String,
) {
  companion object {
    fun from(entity: IcuCriticalAlert) =
      CriticalAlertView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        alertType = entity.alertType,
        severity = entity.severity,
        message = entity.message,
        payload = entity.payload,
        triggeredAt = entity.triggeredAt,
        acknowledgedBy = entity.acknowledgedBy,
        acknowledgedAt = entity.acknowledgedAt,
        status = entity.status,
      )
  }
}

// 04.05 ICU charting read models + severity scores.
data class FlowsheetRowView(
  val hourStart: Instant,
  val vitals: IcuVitalsView?,
  val ventilation: VentilationView?,
  val infusions: List<InfusionView>,
  val inputMl: Double,
  val outputMl: Double,
  val netMl: Double,
)

data class FluidBalanceTotalsView(
  val patientId: UUID,
  val hours: Int,
  val inputMl: Double,
  val outputMl: Double,
  val netMl: Double,
)

data class SeverityScoreRequestDto(
  val patientId: UUID,
  val hospitalId: UUID? = null,
  val admissionId: UUID? = null,
  val pao2: Double? = null,
  val fio2: Double? = null,
  val ventilated: Boolean? = null,
  val platelets: Double? = null,
  val bilirubin: Double? = null,
  val map: Int? = null,
  val vasopressor: String? = null,
  val gcs: Int? = null,
  val creatinine: Double? = null,
)

data class SeverityScoreView(
  val id: UUID?,
  val patientId: UUID,
  val scoreType: String,
  val respiration: Int,
  val coagulation: Int,
  val liver: Int,
  val cardiovascular: Int,
  val cns: Int,
  val renal: Int,
  val total: Int,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.icu.domain.IcuSeverityScore) =
      SeverityScoreView(
        entity.id,
        entity.patientId,
        entity.scoreType,
        entity.respiration,
        entity.coagulation,
        entity.liver,
        entity.cardiovascular,
        entity.cns,
        entity.renal,
        entity.total,
        entity.recordedAt,
      )
  }
}
