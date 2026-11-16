package com.squamed.core.nursing.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.nursing.api.MarCreateDto
import com.squamed.core.nursing.api.MarStatusDto
import com.squamed.core.nursing.api.MarView
import com.squamed.core.nursing.api.NursingAssessmentCreateDto
import com.squamed.core.nursing.api.NursingAssessmentView
import com.squamed.core.nursing.api.NursingCarePlanCreateDto
import com.squamed.core.nursing.api.NursingCarePlanStatusDto
import com.squamed.core.nursing.api.NursingCarePlanView
import com.squamed.core.nursing.api.NursingHandoverCreateDto
import com.squamed.core.nursing.api.NursingHandoverView
import com.squamed.core.nursing.api.NursingNoteCreateDto
import com.squamed.core.nursing.api.NursingNoteView
import com.squamed.core.nursing.api.RiskAssessmentCreateDto
import com.squamed.core.nursing.api.RiskAssessmentView
import com.squamed.core.nursing.api.VitalsCreateDto
import com.squamed.core.nursing.api.VitalsView
import com.squamed.core.nursing.domain.MedicationAdministrationRecord
import com.squamed.core.nursing.domain.NursingAssessment
import com.squamed.core.nursing.domain.NursingCarePlan
import com.squamed.core.nursing.domain.NursingHandover
import com.squamed.core.nursing.domain.NursingNote
import com.squamed.core.nursing.domain.RiskAssessment
import com.squamed.core.nursing.domain.VitalsEntry
import com.squamed.core.nursing.repository.MedicationAdministrationRecordRepository
import com.squamed.core.nursing.repository.NursingAssessmentRepository
import com.squamed.core.nursing.repository.NursingCarePlanRepository
import com.squamed.core.nursing.repository.NursingHandoverRepository
import com.squamed.core.nursing.repository.NursingNoteRepository
import com.squamed.core.nursing.repository.RiskAssessmentRepository
import com.squamed.core.nursing.repository.VitalsEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NursingService(
  private val assessmentRepository: NursingAssessmentRepository,
  private val carePlanRepository: NursingCarePlanRepository,
  private val noteRepository: NursingNoteRepository,
  private val handoverRepository: NursingHandoverRepository,
  private val vitalsRepository: VitalsEntryRepository,
  private val marRepository: MedicationAdministrationRecordRepository,
  private val riskAssessmentRepository: RiskAssessmentRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createAssessment(
    dto: NursingAssessmentCreateDto,
    actorId: UUID,
  ): NursingAssessmentView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("NURSING_PATIENT_REQUIRED")
    val assessmentType = dto.assessmentType ?: throw IllegalArgumentException("NURSING_ASSESSMENT_TYPE_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val assessment =
      assessmentRepository.save(
        NursingAssessment(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          assessmentType = assessmentType,
          findings = dto.findings,
          riskLevel = dto.riskLevel,
          assessedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return NursingAssessmentView.from(assessment)
  }

  @Transactional(readOnly = true)
  fun listAssessments(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<NursingAssessmentView> {
    val rows =
      if (patientId != null) {
        assessmentRepository.findByPatientIdOrderByAssessedAtDesc(patientId, hospitalId)
      } else {
        assessmentRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.assessedAt }.map { NursingAssessmentView.from(it) }
  }

  @Transactional
  fun createCarePlan(
    dto: NursingCarePlanCreateDto,
    actorId: UUID,
  ): NursingCarePlanView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("NURSING_PATIENT_REQUIRED")
    val title = dto.title ?: throw IllegalArgumentException("NURSING_CAREPLAN_TITLE_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val plan =
      carePlanRepository.save(
        NursingCarePlan(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          title = title,
          goal = dto.goal,
          interventions = dto.interventions,
        ).also { it.createdBy = actorId },
      )
    return NursingCarePlanView.from(plan)
  }

  @Transactional(readOnly = true)
  fun listCarePlans(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<NursingCarePlanView> {
    val rows =
      if (patientId != null) {
        carePlanRepository.findByPatientIdOrderByCreatedAtDesc(patientId, hospitalId)
      } else {
        carePlanRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.createdAt ?: Instant.EPOCH }.map { NursingCarePlanView.from(it) }
  }

  @Transactional
  fun updateCarePlanStatus(
    id: UUID,
    dto: NursingCarePlanStatusDto,
    actorId: UUID,
  ): NursingCarePlanView {
    val plan = carePlanRepository.findById(id).orElseThrow { IllegalArgumentException("NURSING_CAREPLAN_NOT_FOUND") }
    val status = dto.status ?: throw IllegalArgumentException("NURSING_CAREPLAN_STATUS_REQUIRED")
    if (status !in CAREPLAN_STATUSES) throw IllegalArgumentException("NURSING_CAREPLAN_INVALID_STATUS")
    val now = Instant.now()
    plan.status = status
    plan.updatedBy = actorId
    if (status == "completed") {
      plan.completedAt = now
    } else {
      plan.completedAt = null
    }
    carePlanRepository.save(plan)
    return NursingCarePlanView.from(plan)
  }

  @Transactional
  fun createNote(
    dto: NursingNoteCreateDto,
    actorId: UUID,
  ): NursingNoteView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("NURSING_PATIENT_REQUIRED")
    val content = dto.content ?: throw IllegalArgumentException("NURSING_NOTE_CONTENT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val note =
      noteRepository.save(
        NursingNote(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          noteType = dto.noteType ?: "progress",
          content = content,
          authoredBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return NursingNoteView.from(note)
  }

  @Transactional(readOnly = true)
  fun listNotes(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<NursingNoteView> {
    val rows =
      if (patientId != null) {
        noteRepository.findByPatientIdOrderByAuthoredAtDesc(patientId, hospitalId)
      } else {
        noteRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.authoredAt }.map { NursingNoteView.from(it) }
  }

  @Transactional
  fun createHandover(
    dto: NursingHandoverCreateDto,
    actorId: UUID,
  ): NursingHandoverView {
    val shift = dto.shift ?: throw IllegalArgumentException("NURSING_HANDOVER_SHIFT_REQUIRED")
    if (shift !in SHIFTS) throw IllegalArgumentException("NURSING_HANDOVER_INVALID_SHIFT")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val handover =
      handoverRepository.save(
        NursingHandover(
          hospitalId = hospitalId,
          wardId = dto.wardId,
          shift = shift,
          situation = dto.situation,
          background = dto.background,
          assessment = dto.assessment,
          recommendation = dto.recommendation,
          authoredBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return NursingHandoverView.from(handover)
  }

  @Transactional(readOnly = true)
  fun listHandovers(
    wardId: UUID?,
    hospitalId: UUID?,
  ): List<NursingHandoverView> {
    val rows =
      if (wardId != null) {
        handoverRepository.findByWardIdOrderByAuthoredAtDesc(wardId, hospitalId)
      } else {
        handoverRepository.findAllByOrderByAuthoredAtDesc(hospitalId)
      }
    return rows.map { NursingHandoverView.from(it) }
  }

  @Transactional
  fun recordVitals(
    dto: VitalsCreateDto,
    actorId: UUID,
  ): VitalsView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("NURSING_PATIENT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val vitals =
      vitalsRepository.save(
        VitalsEntry(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          systolic = dto.systolic,
          diastolic = dto.diastolic,
          heartRate = dto.heartRate,
          respiratoryRate = dto.respiratoryRate,
          temperature = dto.temperature,
          oxygenSaturation = dto.oxygenSaturation,
          gcs = dto.gcs,
          recordedBy = actorId,
          recordedAt = dto.recordedAt ?: Instant.now(),
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "nursing.vitals_recorded",
        occurredAt = Instant.now(),
        source = "nursing-service",
        entityType = "vitals_entry",
        entityId = vitals.id,
        hospitalId = hospitalId,
        payload = mapOf("patientId" to patientId),
      ),
    )
    return VitalsView.from(vitals)
  }

  @Transactional(readOnly = true)
  fun listVitals(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<VitalsView> {
    val rows =
      if (patientId != null) {
        vitalsRepository.findByPatientIdOrderByRecordedAtDesc(patientId, hospitalId)
      } else {
        vitalsRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.recordedAt }.map { VitalsView.from(it) }
  }

  @Transactional
  fun createMar(
    dto: MarCreateDto,
    actorId: UUID,
  ): MarView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("NURSING_PATIENT_REQUIRED")
    val medicationName = dto.medicationName ?: throw IllegalArgumentException("NURSING_MAR_MEDICATION_REQUIRED")
    val scheduledAt = dto.scheduledAt ?: throw IllegalArgumentException("NURSING_MAR_SCHEDULED_AT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val mar =
      marRepository.save(
        MedicationAdministrationRecord(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          medicationName = medicationName,
          dose = dto.dose,
          route = dto.route,
          scheduledAt = scheduledAt,
        ).also { it.createdBy = actorId },
      )
    return MarView.from(mar)
  }

  @Transactional(readOnly = true)
  fun listMar(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<MarView> {
    val rows =
      if (patientId != null) {
        marRepository.findByPatientIdOrderByScheduledAtDesc(patientId, hospitalId)
      } else {
        marRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.scheduledAt }.map { MarView.from(it) }
  }

  @Transactional
  fun updateMarStatus(
    id: UUID,
    dto: MarStatusDto,
    actorId: UUID,
  ): MarView {
    val mar = marRepository.findById(id).orElseThrow { IllegalArgumentException("NURSING_MAR_NOT_FOUND") }
    val status = dto.status ?: throw IllegalArgumentException("NURSING_MAR_STATUS_REQUIRED")
    if (status !in MAR_STATUSES) throw IllegalArgumentException("NURSING_MAR_INVALID_STATUS")
    val now = Instant.now()
    mar.status = status
    mar.updatedBy = actorId
    when (status) {
      "administered" -> {
        mar.administeredAt = now
        mar.administeredBy = actorId
        mar.omittedReason = null
      }

      "omitted" -> {
        mar.omittedReason = dto.omittedReason
        mar.administeredAt = null
        mar.administeredBy = null
      }

      else -> {
        mar.administeredAt = null
        mar.administeredBy = null
        mar.omittedReason = null
      }
    }
    marRepository.save(mar)
    if (status == "administered") {
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "nursing.medication_administered",
          occurredAt = now,
          source = "nursing-service",
          entityType = "medication_administration_record",
          entityId = id,
          hospitalId = mar.hospitalId,
          payload = mapOf("patientId" to mar.patientId, "medicationName" to mar.medicationName),
        ),
      )
    }
    return MarView.from(mar)
  }

  @Transactional
  fun createRiskAssessment(
    dto: RiskAssessmentCreateDto,
    actorId: UUID,
  ): RiskAssessmentView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("NURSING_PATIENT_REQUIRED")
    val riskType = dto.riskType ?: throw IllegalArgumentException("NURSING_RISK_TYPE_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val assessment =
      riskAssessmentRepository.save(
        RiskAssessment(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          riskType = riskType,
          score = dto.score,
          level = dto.level,
          assessedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return RiskAssessmentView.from(assessment)
  }

  @Transactional(readOnly = true)
  fun listRiskAssessments(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<RiskAssessmentView> {
    val rows =
      if (patientId != null) {
        riskAssessmentRepository.findByPatientIdOrderByAssessedAtDesc(patientId, hospitalId)
      } else {
        riskAssessmentRepository.findAllByHospital(hospitalId)
      }
    return rows.sortedByDescending { it.assessedAt }.map { RiskAssessmentView.from(it) }
  }

  companion object {
    val CAREPLAN_STATUSES = setOf("active", "in_progress", "completed", "cancelled")
    val MAR_STATUSES = setOf("scheduled", "administered", "omitted")
    val SHIFTS = setOf("morning", "evening", "night")
  }
}
