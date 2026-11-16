package com.squamed.core.icu.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.icu.api.CriticalAlertCreateDto
import com.squamed.core.icu.api.CriticalAlertStatusDto
import com.squamed.core.icu.api.CriticalAlertView
import com.squamed.core.icu.api.FluidBalanceCreateDto
import com.squamed.core.icu.api.FluidBalanceView
import com.squamed.core.icu.api.IcuVitalsCreateDto
import com.squamed.core.icu.api.IcuVitalsView
import com.squamed.core.icu.api.InfusionCreateDto
import com.squamed.core.icu.api.InfusionStatusDto
import com.squamed.core.icu.api.InfusionView
import com.squamed.core.icu.api.VentilationCreateDto
import com.squamed.core.icu.api.VentilationView
import com.squamed.core.icu.domain.IcuCriticalAlert
import com.squamed.core.icu.domain.IcuFluidBalance
import com.squamed.core.icu.domain.IcuInfusionRecord
import com.squamed.core.icu.domain.IcuVentilationRecord
import com.squamed.core.icu.domain.IcuVitalsEntry
import com.squamed.core.icu.repository.IcuCriticalAlertRepository
import com.squamed.core.icu.repository.IcuFluidBalanceRepository
import com.squamed.core.icu.repository.IcuInfusionRecordRepository
import com.squamed.core.icu.repository.IcuVentilationRecordRepository
import com.squamed.core.icu.repository.IcuVitalsEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class IcuService(
  private val vitalsRepository: IcuVitalsEntryRepository,
  private val fluidBalanceRepository: IcuFluidBalanceRepository,
  private val ventilationRepository: IcuVentilationRecordRepository,
  private val infusionRepository: IcuInfusionRecordRepository,
  private val alertRepository: IcuCriticalAlertRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun recordVitals(
    dto: IcuVitalsCreateDto,
    actorId: UUID,
  ): IcuVitalsView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("ICU_PATIENT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("ICU_HOSPITAL_REQUIRED")
    val vitals =
      vitalsRepository.save(
        IcuVitalsEntry(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          systolic = dto.systolic,
          diastolic = dto.diastolic,
          heartRate = dto.heartRate,
          respiratoryRate = dto.respiratoryRate,
          temperature = dto.temperature,
          oxygenSaturation = dto.oxygenSaturation,
          map = dto.map,
          etco2 = dto.etco2,
          gcs = dto.gcs,
          recordedBy = actorId,
          recordedAt = dto.recordedAt ?: Instant.now(),
        ).also { it.createdBy = actorId },
      )
    return IcuVitalsView.from(vitals)
  }

  @Transactional(readOnly = true)
  fun listVitals(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<IcuVitalsView> {
    val rows =
      if (patientId != null) {
        vitalsRepository.findByPatientIdOrderByRecordedAtDesc(patientId, hospitalId)
      } else {
        vitalsRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.recordedAt }.map { IcuVitalsView.from(it) }
  }

  @Transactional
  fun createFluidBalance(
    dto: FluidBalanceCreateDto,
    actorId: UUID,
  ): FluidBalanceView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("ICU_PATIENT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("ICU_HOSPITAL_REQUIRED")
    val balance =
      fluidBalanceRepository.save(
        IcuFluidBalance(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          inputMl = dto.inputMl,
          outputMl = dto.outputMl,
          inputType = dto.inputType,
          outputType = dto.outputType,
          balanceAt = dto.balanceAt ?: Instant.now(),
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return FluidBalanceView.from(balance)
  }

  @Transactional(readOnly = true)
  fun listFluidBalance(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<FluidBalanceView> {
    val rows =
      if (patientId != null) {
        fluidBalanceRepository.findByPatientIdOrderByBalanceAtDesc(patientId, hospitalId)
      } else {
        fluidBalanceRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.balanceAt }.map { FluidBalanceView.from(it) }
  }

  @Transactional
  fun createVentilation(
    dto: VentilationCreateDto,
    actorId: UUID,
  ): VentilationView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("ICU_PATIENT_REQUIRED")
    val mode = dto.mode ?: throw IllegalArgumentException("ICU_VENTILATION_MODE_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("ICU_HOSPITAL_REQUIRED")
    val record =
      ventilationRepository.save(
        IcuVentilationRecord(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          mode = mode,
          fio2Percent = dto.fio2Percent,
          peep = dto.peep,
          tidalVolumeMl = dto.tidalVolumeMl,
          respiratoryRateSet = dto.respiratoryRateSet,
          oxygenSaturation = dto.oxygenSaturation,
          recordedBy = actorId,
          recordedAt = dto.recordedAt ?: Instant.now(),
        ).also { it.createdBy = actorId },
      )
    return VentilationView.from(record)
  }

  @Transactional(readOnly = true)
  fun listVentilation(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<VentilationView> {
    val rows =
      if (patientId != null) {
        ventilationRepository.findByPatientIdOrderByRecordedAtDesc(patientId, hospitalId)
      } else {
        ventilationRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.recordedAt }.map { VentilationView.from(it) }
  }

  @Transactional
  fun createInfusion(
    dto: InfusionCreateDto,
    actorId: UUID,
  ): InfusionView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("ICU_PATIENT_REQUIRED")
    val drugName = dto.drugName ?: throw IllegalArgumentException("ICU_INFUSION_DRUG_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("ICU_HOSPITAL_REQUIRED")
    val infusion =
      infusionRepository.save(
        IcuInfusionRecord(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          drugName = drugName,
          dose = dto.dose,
          rateMlPerHour = dto.rateMlPerHour,
          route = dto.route,
          startedAt = dto.startedAt ?: Instant.now(),
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return InfusionView.from(infusion)
  }

  @Transactional(readOnly = true)
  fun listInfusions(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<InfusionView> {
    val rows =
      if (patientId != null) {
        infusionRepository.findByPatientIdOrderByStartedAtDesc(patientId, hospitalId)
      } else {
        infusionRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.startedAt }.map { InfusionView.from(it) }
  }

  @Transactional
  fun updateInfusionStatus(
    id: UUID,
    dto: InfusionStatusDto,
    actorId: UUID,
  ): InfusionView {
    val infusion = infusionRepository.findById(id).orElseThrow { IllegalArgumentException("ICU_INFUSION_NOT_FOUND") }
    val status = dto.status ?: throw IllegalArgumentException("ICU_INFUSION_STATUS_REQUIRED")
    if (status !in INFUSION_STATUSES) throw IllegalArgumentException("ICU_INFUSION_INVALID_STATUS")
    val now = Instant.now()
    infusion.status = status
    infusion.updatedBy = actorId
    if (status == "stopped") {
      infusion.stoppedAt = now
    } else {
      infusion.stoppedAt = null
    }
    infusionRepository.save(infusion)
    return InfusionView.from(infusion)
  }

  @Transactional
  fun createAlert(
    dto: CriticalAlertCreateDto,
    actorId: UUID,
  ): CriticalAlertView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("ICU_PATIENT_REQUIRED")
    val alertType = dto.alertType ?: throw IllegalArgumentException("ICU_ALERT_TYPE_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("ICU_HOSPITAL_REQUIRED")
    val alert =
      alertRepository.save(
        IcuCriticalAlert(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          alertType = alertType,
          severity = dto.severity ?: "critical",
          message = dto.message,
          payload = dto.payload,
        ).also { it.createdBy = actorId },
      )
    val alertId = alert.id
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "icu.critical_alert",
        occurredAt = Instant.now(),
        source = "icu-service",
        entityType = "icu_critical_alert",
        entityId = alertId,
        hospitalId = hospitalId,
        payload = mapOf("patientId" to patientId, "alertType" to alertType, "severity" to alert.severity),
      ),
    )
    return CriticalAlertView.from(alert)
  }

  @Transactional(readOnly = true)
  fun listAlerts(
    patientId: UUID?,
    status: String?,
    hospitalId: UUID?,
  ): List<CriticalAlertView> {
    val rows =
      when {
        patientId != null && status != null -> alertRepository.findByPatientIdAndStatusOrderByTriggeredAtDesc(patientId, status, hospitalId)
        patientId != null -> alertRepository.findByPatientIdOrderByTriggeredAtDesc(patientId, hospitalId)
        else -> alertRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.triggeredAt }.map { CriticalAlertView.from(it) }
  }

  @Transactional
  fun acknowledgeAlert(
    id: UUID,
    actorId: UUID,
  ): CriticalAlertView {
    val alert = findAlert(id)
    if (alert.status == "open") {
      val now = Instant.now()
      alert.status = "acknowledged"
      alert.acknowledgedBy = actorId
      alert.acknowledgedAt = now
      alert.updatedBy = actorId
      alertRepository.save(alert)
    }
    return CriticalAlertView.from(alert)
  }

  @Transactional
  fun updateAlertStatus(
    id: UUID,
    dto: CriticalAlertStatusDto,
    actorId: UUID,
  ): CriticalAlertView {
    val alert = findAlert(id)
    val status = dto.status ?: throw IllegalArgumentException("ICU_ALERT_STATUS_REQUIRED")
    if (status !in ALERT_STATUSES) throw IllegalArgumentException("ICU_ALERT_INVALID_STATUS")
    val now = Instant.now()
    alert.status = status
    alert.updatedBy = actorId
    if (status != "open" && alert.acknowledgedAt == null) {
      alert.acknowledgedBy = actorId
      alert.acknowledgedAt = now
    }
    if (status == "open") {
      alert.acknowledgedBy = null
      alert.acknowledgedAt = null
    }
    alertRepository.save(alert)
    return CriticalAlertView.from(alert)
  }

  private fun findAlert(id: UUID): IcuCriticalAlert = alertRepository.findById(id).orElseThrow { IllegalArgumentException("ICU_ALERT_NOT_FOUND") }

  companion object {
    val INFUSION_STATUSES = setOf("active", "stopped")
    val ALERT_STATUSES = setOf("open", "acknowledged", "resolved")
  }
}
