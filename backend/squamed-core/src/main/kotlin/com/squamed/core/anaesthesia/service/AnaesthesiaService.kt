package com.squamed.core.anaesthesia.service

import com.squamed.core.anaesthesia.api.AnaesthesiaRecordCreateDto
import com.squamed.core.anaesthesia.api.AnaesthesiaRecordStatusDto
import com.squamed.core.anaesthesia.api.AnaesthesiaRecordView
import com.squamed.core.anaesthesia.api.EvaluationCreateDto
import com.squamed.core.anaesthesia.api.EvaluationView
import com.squamed.core.anaesthesia.api.MedicationCreateDto
import com.squamed.core.anaesthesia.api.MedicationView
import com.squamed.core.anaesthesia.api.MonitoringCreateDto
import com.squamed.core.anaesthesia.api.MonitoringView
import com.squamed.core.anaesthesia.api.PacuCreateDto
import com.squamed.core.anaesthesia.api.PacuView
import com.squamed.core.anaesthesia.api.RecoveryCreateDto
import com.squamed.core.anaesthesia.api.RecoveryView
import com.squamed.core.anaesthesia.domain.AnaesthesiaEvaluation
import com.squamed.core.anaesthesia.domain.AnaesthesiaMedication
import com.squamed.core.anaesthesia.domain.AnaesthesiaRecord
import com.squamed.core.anaesthesia.domain.IntraOpMonitoring
import com.squamed.core.anaesthesia.domain.PacuRecord
import com.squamed.core.anaesthesia.domain.RecoveryRecord
import com.squamed.core.anaesthesia.repository.AnaesthesiaEvaluationRepository
import com.squamed.core.anaesthesia.repository.AnaesthesiaMedicationRepository
import com.squamed.core.anaesthesia.repository.AnaesthesiaRecordRepository
import com.squamed.core.anaesthesia.repository.IntraOpMonitoringRepository
import com.squamed.core.anaesthesia.repository.PacuRecordRepository
import com.squamed.core.anaesthesia.repository.RecoveryRecordRepository
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.surgery.repository.SurgeryScheduleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AnaesthesiaService(
  private val evaluationRepository: AnaesthesiaEvaluationRepository,
  private val recordRepository: AnaesthesiaRecordRepository,
  private val monitoringRepository: IntraOpMonitoringRepository,
  private val medicationRepository: AnaesthesiaMedicationRepository,
  private val recoveryRepository: RecoveryRecordRepository,
  private val pacuRepository: PacuRecordRepository,
  private val surgeryScheduleRepository: SurgeryScheduleRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createEvaluation(
    dto: EvaluationCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): EvaluationView {
    val schedule = requireSurgerySchedule(dto.surgeryScheduleId, hospitalId)
    val patientId = dto.patientId ?: throw IllegalArgumentException("ANAESTHESIA_PATIENT_REQUIRED")
    val asaStatus = dto.asaStatus ?: throw IllegalArgumentException("ANAESTHESIA_ASA_REQUIRED")
    if (asaStatus !in ASA_STATUSES) throw IllegalArgumentException("ANAESTHESIA_INVALID_ASA")
    val evaluation =
      evaluationRepository.save(
        AnaesthesiaEvaluation(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = schedule.id!!,
          patientId = patientId,
          asaStatus = asaStatus,
          airwayAssessment = dto.airwayAssessment,
          allergies = dto.allergies,
          medicalConditions = dto.medicalConditions,
          medications = dto.medications,
          previousComplications = dto.previousComplications,
          fastingHours = dto.fastingHours,
          labNotes = dto.labNotes,
          riskLevel = dto.riskLevel,
        ).also { it.createdBy = actorId },
      )
    return EvaluationView.from(evaluation)
  }

  @Transactional
  fun confirmEvaluation(
    id: UUID,
    actorId: UUID,
    hospitalId: UUID?,
  ): EvaluationView {
    val evaluation = findEvaluation(id, hospitalId)
    if (evaluation.status != "draft") throw IllegalArgumentException("ANAESTHESIA_EVALUATION_NOT_DRAFT")
    evaluation.status = "confirmed"
    evaluation.evaluatedBy = actorId
    evaluation.evaluatedAt = Instant.now()
    evaluation.updatedBy = actorId
    val saved = evaluationRepository.save(evaluation)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "anaesthesia.evaluation_confirmed",
        occurredAt = Instant.now(),
        source = "anaesthesia-service",
        entityType = "anaesthesia_evaluation",
        entityId = saved.id,
        hospitalId = saved.hospitalId,
        payload = mapOf("patientId" to saved.patientId, "asaStatus" to saved.asaStatus),
      ),
    )
    return EvaluationView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listEvaluations(
    surgeryScheduleId: UUID?,
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<EvaluationView> {
    val rows =
      when {
        surgeryScheduleId != null -> evaluationRepository.findBySurgeryScheduleId(hospitalId, surgeryScheduleId)
        patientId != null -> evaluationRepository.findByPatientId(hospitalId, patientId)
        else -> evaluationRepository.findAllForHospital(hospitalId)
      }
    return rows.map { EvaluationView.from(it) }
  }

  @Transactional
  fun createRecord(
    dto: AnaesthesiaRecordCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): AnaesthesiaRecordView {
    val schedule = requireSurgerySchedule(dto.surgeryScheduleId, hospitalId)
    val patientId = dto.patientId ?: throw IllegalArgumentException("ANAESTHESIA_PATIENT_REQUIRED")
    val airwayDevice = dto.airwayDevice
    if (airwayDevice != null && airwayDevice !in AIRWAY_DEVICES) throw IllegalArgumentException("ANAESTHESIA_INVALID_AIRWAY_DEVICE")
    val record =
      recordRepository.save(
        AnaesthesiaRecord(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = schedule.id!!,
          patientId = patientId,
          anaesthetistId = dto.anaesthetistId,
          airwayDevice = airwayDevice,
          intubationGrade = dto.intubationGrade,
        ).also { it.createdBy = actorId },
      )
    return AnaesthesiaRecordView.from(record)
  }

  @Transactional
  fun updateRecordStatus(
    id: UUID,
    dto: AnaesthesiaRecordStatusDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): AnaesthesiaRecordView {
    val record = findRecord(id, hospitalId)
    val target = dto.status ?: throw IllegalArgumentException("ANAESTHESIA_STATUS_REQUIRED")
    if (target !in RECORD_STATUSES) throw IllegalArgumentException("ANAESTHESIA_INVALID_STATUS")
    val now = Instant.now()
    when (target) {
      "induced" -> {
        if (record.status != "planned") throw IllegalArgumentException("ANAESTHESIA_CANNOT_INDUCE")
        record.startedAt = now
      }

      "completed" -> {
        if (record.status !in setOf("induced", "maintenance", "emergence")) throw IllegalArgumentException("ANAESTHESIA_CANNOT_COMPLETE")
        record.endedAt = now
      }

      else -> {
        if (record.status == "completed") throw IllegalArgumentException("ANAESTHESIA_RECORD_LOCKED")
      }
    }
    record.status = target
    record.updatedBy = actorId
    val saved = recordRepository.save(record)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "anaesthesia.${target.lowercase().replace("_", "-")}",
        occurredAt = now,
        source = "anaesthesia-service",
        entityType = "anaesthesia_record",
        entityId = saved.id,
        hospitalId = saved.hospitalId,
        payload = mapOf("patientId" to saved.patientId, "status" to target),
      ),
    )
    return AnaesthesiaRecordView.from(saved)
  }

  @Transactional
  fun addMonitoring(
    recordId: UUID,
    dto: MonitoringCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): MonitoringView {
    val record = findRecord(recordId, hospitalId)
    val heartRate = dto.heartRate ?: throw IllegalArgumentException("ANAESTHESIA_HEART_RATE_REQUIRED")
    return MonitoringView.from(
      monitoringRepository.save(
        IntraOpMonitoring(
          hospitalId = record.hospitalId,
          surgeryScheduleId = record.surgeryScheduleId,
          anaesthesiaRecordId = recordId,
          patientId = record.patientId,
          systolic = dto.systolic,
          diastolic = dto.diastolic,
          heartRate = heartRate,
          oxygenSaturation = dto.oxygenSaturation,
          etco2 = dto.etco2,
          temperature = dto.temperature,
          hourlyUrineMl = dto.hourlyUrineMl,
          anaesthesiaDepth = dto.anaesthesiaDepth,
          notes = dto.notes,
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional(readOnly = true)
  fun listMonitoring(
    recordId: UUID,
    hospitalId: UUID?,
  ): List<MonitoringView> {
    findRecord(recordId, hospitalId)
    return monitoringRepository.findByAnaesthesiaRecordId(recordId).map { MonitoringView.from(it) }
  }

  @Transactional
  fun addMedication(
    recordId: UUID,
    dto: MedicationCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): MedicationView {
    val record = findRecord(recordId, hospitalId)
    val name = dto.medicationName ?: throw IllegalArgumentException("ANAESTHESIA_MEDICATION_NAME_REQUIRED")
    return MedicationView.from(
      medicationRepository.save(
        AnaesthesiaMedication(
          hospitalId = record.hospitalId,
          surgeryScheduleId = record.surgeryScheduleId,
          anaesthesiaRecordId = recordId,
          patientId = record.patientId,
          medicationName = name,
          dose = dto.dose,
          route = dto.route,
          givenBy = actorId,
          notes = dto.notes,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional(readOnly = true)
  fun listMedications(
    recordId: UUID,
    hospitalId: UUID?,
  ): List<MedicationView> {
    findRecord(recordId, hospitalId)
    return medicationRepository.findByAnaesthesiaRecordId(recordId).map { MedicationView.from(it) }
  }

  @Transactional
  fun recordRecovery(
    recordId: UUID,
    dto: RecoveryCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): RecoveryView {
    val record = findRecord(recordId, hospitalId)
    val aldreteScore = dto.aldreteScore ?: throw IllegalArgumentException("ANAESTHESIA_ALDRE_TE_SCORE_REQUIRED")
    if (aldreteScore !in 0..10) throw IllegalArgumentException("ANAESTHESIA_INVALID_ALDRE_TE_SCORE")
    val dischargeMet = dto.dischargeCriteriaMet ?: false
    val recovery =
      recoveryRepository.save(
        RecoveryRecord(
          hospitalId = record.hospitalId,
          surgeryScheduleId = record.surgeryScheduleId,
          patientId = record.patientId,
          aldreteScore = aldreteScore,
          dischargeCriteriaMet = dischargeMet,
          recoveryEndedAt = if (dischargeMet) Instant.now() else null,
          observations = dto.observations,
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    if (dischargeMet) {
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "anaesthesia.recovery_completed",
          occurredAt = Instant.now(),
          source = "anaesthesia-service",
          entityType = "recovery_record",
          entityId = recovery.id,
          hospitalId = recovery.hospitalId,
          payload = mapOf("patientId" to recovery.patientId, "aldreteScore" to aldreteScore),
        ),
      )
    }
    return RecoveryView.from(recovery)
  }

  @Transactional(readOnly = true)
  fun listRecovery(
    recordId: UUID,
    hospitalId: UUID?,
  ): List<RecoveryView> {
    val record = findRecord(recordId, hospitalId)
    return recoveryRepository.findBySurgeryScheduleId(record.surgeryScheduleId).map { RecoveryView.from(it) }
  }

  @Transactional
  fun recordPacu(
    recordId: UUID,
    dto: PacuCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): PacuView {
    val record = findRecord(recordId, hospitalId)
    return PacuView.from(
      pacuRepository.save(
        PacuRecord(
          hospitalId = record.hospitalId,
          surgeryScheduleId = record.surgeryScheduleId,
          patientId = record.patientId,
          nurseAssignment = dto.nurseAssignment,
          painScore = dto.painScore,
          nauseaScore = dto.nauseaScore,
          bleedingNotes = dto.bleedingNotes,
          oxygenSaturation = dto.oxygenSaturation,
          heartRate = dto.heartRate,
          systolic = dto.systolic,
          diastolic = dto.diastolic,
          notes = dto.notes,
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional(readOnly = true)
  fun listPacu(
    recordId: UUID,
    hospitalId: UUID?,
  ): List<PacuView> {
    val record = findRecord(recordId, hospitalId)
    return pacuRepository.findBySurgeryScheduleId(record.surgeryScheduleId).map { PacuView.from(it) }
  }

  private fun requireSurgerySchedule(
    scheduleId: UUID?,
    hospitalId: UUID?,
  ): com.squamed.core.surgery.domain.SurgerySchedule {
    val id = scheduleId ?: throw IllegalArgumentException("ANAESTHESIA_SURGERY_SCHEDULE_REQUIRED")
    val schedule =
      surgeryScheduleRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("ANAESTHESIA_SURGERY_SCHEDULE_NOT_FOUND") }
    // 00.04 tenant sweep (D1): write rows inherit the schedule's hospital, so pin the write at the parent load
    requireSameHospital(hospitalId, schedule.hospitalId)
    return schedule
  }

  private fun findEvaluation(
    id: UUID,
    hospitalId: UUID?,
  ): AnaesthesiaEvaluation {
    val evaluation = evaluationRepository.findById(id).orElseThrow { IllegalArgumentException("ANAESTHESIA_EVALUATION_NOT_FOUND") }
    requireSameHospital(hospitalId, evaluation.hospitalId) // 00.04 tenant sweep
    return evaluation
  }

  private fun findRecord(
    id: UUID,
    hospitalId: UUID?,
  ): AnaesthesiaRecord {
    val record = recordRepository.findById(id).orElseThrow { IllegalArgumentException("ANAESTHESIA_RECORD_NOT_FOUND") }
    requireSameHospital(hospitalId, record.hospitalId) // 00.04 tenant sweep
    return record
  }

  companion object {
    val ASA_STATUSES = setOf("I", "II", "III", "IV", "V", "VI")
    val AIRWAY_DEVICES = setOf("mask", "lma", "ett", "double_lumen", "tracheotomy", "nasal_cannula")
    val RECORD_STATUSES = setOf("planned", "induced", "maintenance", "emergence", "completed")
  }
}
