package com.squamed.core.nursing.service

import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.masterdata.repository.DrugRepository
import com.squamed.core.nursing.api.MarAdministerDto
import com.squamed.core.nursing.api.MarCreateDto
import com.squamed.core.nursing.api.MarOmitDto
import com.squamed.core.nursing.api.MarScheduleDto
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
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
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
  private val prescriptionRepository: PrescriptionRepository,
  private val drugRepository: DrugRepository,
  private val patientRecordLookup: com.squamed.core.patient.repository.PatientRecordRepository,
  private val taskService: TaskService,
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
      publishMar(mar, "nursing.medication_administered", mapOf("patientId" to mar.patientId, "medicationName" to mar.medicationName))
    }
    return MarView.from(mar)
  }

  // -- 04.02 eMAR: schedule from an active prescription, barcode-checked administration, omissions --

  @Transactional
  fun scheduleMar(
    dto: MarScheduleDto,
    actorId: UUID,
  ): List<MarView> {
    val prescription = prescriptionRepository.findById(dto.prescriptionId).orElseThrow { IllegalArgumentException("PRESCRIPTION_NOT_FOUND") }
    if (prescription.status != "prescribed" && prescription.status != "active") throw IllegalArgumentException("PRESCRIPTION_NOT_ACTIVE")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("NURSING_HOSPITAL_REQUIRED")
    val frequency = prescription.frequency?.trim()?.uppercase()
    val isPrn = frequency == "PRN" || prescription.isPrn
    val intervalHours = frequency?.let { FREQUENCY_HOURS[it] }
    val doseCount = dto.doses ?: 1
    if (!isPrn && intervalHours == null && doseCount > 1) throw IllegalArgumentException("MAR_FREQUENCY_UNSCHEDULABLE")
    val start = dto.startAt ?: Instant.now()
    val drug = prescription.drugId?.let { drugRepository.findById(it).orElse(null) }
    val highAlert =
      HIGH_ALERT_TOKENS.any { token ->
        prescription.drugName.lowercase().contains(token) || (drug?.genericName?.lowercase()?.contains(token) == true)
      }
    val rows =
      (0 until doseCount.coerceAtLeast(1)).map { index ->
        marRepository.save(
          MedicationAdministrationRecord(
            hospitalId = hospitalId,
            patientId = prescription.patientId,
            admissionId = dto.admissionId,
            medicationName = prescription.drugName,
            dose = prescription.dose,
            route = prescription.route,
            scheduledAt = if (intervalHours != null) start.plus(Duration.ofHours(intervalHours * index)) else start,
            prescriptionId = prescription.id,
            drugCode = prescription.drugId?.toString() ?: prescription.drugName,
            isPrn = isPrn,
            highAlert = highAlert,
          ).also { it.createdBy = actorId },
        )
      }
    rows.forEach { row ->
      publishMar(row, "nursing.dose_scheduled", mapOf("patientId" to row.patientId, "scheduledAt" to row.scheduledAt.toString()))
    }
    return rows.map { MarView.from(it) }
  }

  @Transactional
  fun administerMar(
    id: UUID,
    dto: MarAdministerDto,
    actorId: UUID,
  ): MarView {
    val mar = marRepository.findById(id).orElseThrow { IllegalArgumentException("NURSING_MAR_NOT_FOUND") }
    if (mar.status != "scheduled") throw IllegalArgumentException("MAR_NOT_SCHEDULED")
    // Barcode patient + drug hard block (SAFETY: mismatch = block).
    val patientBarcode = dto.patientBarcode?.trim()
    val drugBarcode = dto.drugBarcode?.trim()
    if (patientBarcode.isNullOrEmpty() || drugBarcode.isNullOrEmpty()) throw IllegalArgumentException("MAR_BARCODE_REQUIRED")
    val patient = patientRecordLookup.findById(mar.patientId).orElse(null)
    val expectedPatient = patient?.mrn ?: patient?.id?.toString()
    if (expectedPatient != null && patientBarcode != expectedPatient) throw IllegalStateException("MAR_BARCODE_MISMATCH")
    if (mar.drugCode != null && drugBarcode != mar.drugCode) throw IllegalStateException("MAR_BARCODE_MISMATCH")
    // PRN doses need a reason.
    val prnReason = dto.prnReason?.trim()
    if (mar.isPrn && prnReason.isNullOrEmpty()) throw IllegalArgumentException("MAR_PRN_REASON_REQUIRED")
    // High-alert doses need a second checker who is not the administrator.
    if (mar.highAlert) {
      val checker = dto.secondCheckerId ?: throw IllegalArgumentException("HIGH_ALERT_SECOND_CHECK_REQUIRED")
      if (checker == actorId) throw IllegalArgumentException("HIGH_ALERT_SECOND_CHECK_REQUIRED")
    }
    val now = Instant.now()
    mar.status = "administered"
    mar.administeredAt = now
    mar.administeredBy = actorId
    mar.omittedReason = null
    mar.omissionCode = null
    mar.secondCheckerId = dto.secondCheckerId
    mar.barcodeVerified = true
    mar.prnReason = prnReason
    mar.updatedBy = actorId
    marRepository.save(mar)
    publishMar(mar, "nursing.dose_administered", mapOf("patientId" to mar.patientId, "medicationName" to mar.medicationName))
    return MarView.from(mar)
  }

  @Transactional
  fun omitMar(
    id: UUID,
    dto: MarOmitDto,
    actorId: UUID,
  ): MarView {
    val mar = marRepository.findById(id).orElseThrow { IllegalArgumentException("NURSING_MAR_NOT_FOUND") }
    if (mar.status != "scheduled") throw IllegalArgumentException("MAR_NOT_SCHEDULED")
    val code = dto.code.trim().uppercase()
    if (code !in OMISSION_CODES) throw IllegalArgumentException("MAR_OMISSION_CODE_INVALID")
    val reason = dto.reason?.trim()
    if (reason.isNullOrEmpty()) throw IllegalArgumentException("MAR_OMISSION_REASON_REQUIRED")
    mar.status = "omitted"
    mar.omissionCode = code
    mar.omittedReason = reason
    mar.administeredAt = null
    mar.administeredBy = null
    mar.updatedBy = actorId
    marRepository.save(mar)
    publishMar(mar, "nursing.dose_omitted", mapOf("patientId" to mar.patientId, "code" to code, "reason" to reason))
    // Omission notifies the prescriber through the task engine (unassigned if the rx has no prescriber).
    if (dto.notifyDoctor != false) {
      val rx = mar.prescriptionId?.let { prescriptionRepository.findById(it).orElse(null) }
      taskService.createTask(
        TaskCreateDto(
          hospitalId = mar.hospitalId,
          title = "Omitted dose: ${mar.medicationName}",
          description = "$code - $reason",
          assigneeId = rx?.prescribedBy,
          priority = "high",
          relatedType = "MAR",
          relatedId = mar.id,
        ),
        actorId,
      )
    }
    return MarView.from(mar)
  }

  private fun publishMar(
    mar: MedicationAdministrationRecord,
    eventType: String,
    payload: Map<String, Any?>,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "nursing-service",
        entityType = "medication_administration_record",
        entityId = mar.id,
        hospitalId = mar.hospitalId,
        payload = payload,
      ),
    )
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
    val OMISSION_CODES =
      setOf("PATIENT_REFUSED", "NPO", "VOMITING", "UNAVAILABLE", "HELD_FOR_CLINICAL_REASON", "PATIENT_ABSENT")
    val HIGH_ALERT_TOKENS = listOf("insulin", "heparin", "warfarin", "morphine", "potassium", "digoxin")
    val FREQUENCY_HOURS =
      mapOf("OD" to 24L, "HS" to 24L, "BD" to 12L, "BID" to 12L, "TID" to 8L, "TDS" to 8L, "QID" to 6L, "QDS" to 6L)
  }
}
