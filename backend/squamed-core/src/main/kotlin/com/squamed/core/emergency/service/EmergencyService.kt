package com.squamed.core.emergency.service

import com.squamed.core.emergency.api.EmergencyRegisterDto
import com.squamed.core.emergency.api.EmergencyRegistrationView
import com.squamed.core.emergency.api.EmergencyStatusDto
import com.squamed.core.emergency.api.TraumaSurveyDto
import com.squamed.core.emergency.api.TraumaWorkflowView
import com.squamed.core.emergency.api.TriageCreateDto
import com.squamed.core.emergency.api.TriageView
import com.squamed.core.emergency.domain.EmergencyRegistration
import com.squamed.core.emergency.domain.TraumaWorkflow
import com.squamed.core.emergency.domain.TriageAssessment
import com.squamed.core.emergency.repository.EmergencyRegistrationRepository
import com.squamed.core.emergency.repository.TraumaWorkflowRepository
import com.squamed.core.emergency.repository.TriageAssessmentRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class EmergencyService(
  private val registrationRepository: EmergencyRegistrationRepository,
  private val triageRepository: TriageAssessmentRepository,
  private val traumaRepository: TraumaWorkflowRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun register(
    dto: EmergencyRegisterDto,
    actorId: UUID,
  ): EmergencyRegistrationView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("EMERGENCY_PATIENT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("EMERGENCY_HOSPITAL_REQUIRED")
    val arrivalMode = dto.arrivalMode ?: "walk_in"
    if (arrivalMode !in ARRIVAL_MODES) throw IllegalArgumentException("EMERGENCY_INVALID_ARRIVAL_MODE")
    val registration =
      registrationRepository.save(
        EmergencyRegistration(
          hospitalId = hospitalId,
          patientId = patientId,
          arrivalMode = arrivalMode,
          arrivalAt = dto.arrivalAt ?: Instant.now(),
          presentingComplaint = dto.presentingComplaint,
          registeredBy = actorId,
        ).also { it.createdBy = actorId },
      )
    val registrationId = registration.id ?: throw IllegalStateException("EMERGENCY_REGISTRATION_ID_MISSING")
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "emergency.registered",
        occurredAt = Instant.now(),
        source = "emergency-service",
        entityType = "emergency_registration",
        entityId = registrationId,
        hospitalId = hospitalId,
        payload = mapOf("patientId" to patientId, "arrivalMode" to arrivalMode),
      ),
    )
    return EmergencyRegistrationView.from(registration)
  }

  @Transactional(readOnly = true)
  fun listRegistrations(
    status: String?,
    patientId: UUID?,
  ): List<EmergencyRegistrationView> {
    val rows =
      when {
        status != null -> registrationRepository.findByStatus(status)
        patientId != null -> registrationRepository.findByPatientId(patientId)
        else -> registrationRepository.findAll()
      }
    return rows
      .sortedByDescending { it.arrivalAt }
      .map { EmergencyRegistrationView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getRegistration(id: UUID): EmergencyRegistrationView = EmergencyRegistrationView.from(findRegistration(id))

  @Transactional
  fun triage(
    registrationId: UUID,
    dto: TriageCreateDto,
    actorId: UUID,
  ): TriageView {
    val registration = findRegistration(registrationId)
    val esiLevel = dto.esiLevel ?: throw IllegalArgumentException("EMERGENCY_ESI_REQUIRED")
    if (esiLevel !in 1..5) throw IllegalArgumentException("EMERGENCY_TRIAGE_INVALID_ESI")
    if (registration.triageEsi != null) throw IllegalArgumentException("EMERGENCY_ALREADY_TRIAGED")
    val triage =
      triageRepository.save(
        TriageAssessment(
          hospitalId = registration.hospitalId,
          registrationId = registrationId,
          patientId = registration.patientId,
          esiLevel = esiLevel,
          triageCategory = dto.triageCategory,
          painScale = dto.painScale,
          vitalTriggers = dto.vitalTriggers ?: false,
          acuityNotes = dto.acuityNotes,
          assessedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    registration.triageEsi = esiLevel
    registration.priority = if (esiLevel <= 2) "stat" else "routine"
    registration.status = "triaged"
    registration.updatedBy = actorId
    registrationRepository.save(registration)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "emergency.triaged",
        occurredAt = Instant.now(),
        source = "emergency-service",
        entityType = "triage_assessment",
        entityId = triage.id,
        hospitalId = registration.hospitalId,
        payload = mapOf("patientId" to registration.patientId, "esiLevel" to esiLevel),
      ),
    )
    return TriageView.from(triage)
  }

  @Transactional
  fun activateTrauma(
    registrationId: UUID,
    actorId: UUID,
  ): TraumaWorkflowView {
    val registration = findRegistration(registrationId)
    val existing = traumaRepository.findByRegistrationId(registrationId)
    if (existing != null) throw IllegalArgumentException("EMERGENCY_TRAUMA_ALREADY_ACTIVE")
    val workflow =
      traumaRepository.save(
        TraumaWorkflow(
          hospitalId = registration.hospitalId,
          registrationId = registrationId,
          patientId = registration.patientId,
          activatedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "emergency.trauma_activated",
        occurredAt = Instant.now(),
        source = "emergency-service",
        entityType = "trauma_workflow",
        entityId = workflow.id,
        hospitalId = registration.hospitalId,
        payload = mapOf("patientId" to registration.patientId),
      ),
    )
    return TraumaWorkflowView.from(workflow)
  }

  @Transactional
  fun updateTraumaSurvey(
    registrationId: UUID,
    dto: TraumaSurveyDto,
    actorId: UUID,
  ): TraumaWorkflowView {
    val workflow = traumaRepository.findByRegistrationId(registrationId) ?: throw IllegalArgumentException("EMERGENCY_TRAUMA_NOT_ACTIVE")
    val phase = dto.surveyPhase ?: throw IllegalArgumentException("EMERGENCY_TRAUMA_PHASE_REQUIRED")
    if (phase !in TRAUMA_PHASES) throw IllegalArgumentException("EMERGENCY_TRAUMA_INVALID_PHASE")
    val now = Instant.now()
    workflow.surveyPhase = phase
    workflow.airway = dto.airway ?: workflow.airway
    workflow.breathing = dto.breathing ?: workflow.breathing
    workflow.circulation = dto.circulation ?: workflow.circulation
    workflow.disability = dto.disability ?: workflow.disability
    workflow.exposure = dto.exposure ?: workflow.exposure
    workflow.surveyNotes = dto.surveyNotes ?: workflow.surveyNotes
    workflow.updatedBy = actorId
    if (phase == "completed") {
      workflow.completedBy = actorId
      workflow.completedAt = now
    } else {
      workflow.completedBy = null
      workflow.completedAt = null
    }
    traumaRepository.save(workflow)
    if (phase == "completed") {
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "emergency.trauma_completed",
          occurredAt = now,
          source = "emergency-service",
          entityType = "trauma_workflow",
          entityId = workflow.id,
          hospitalId = workflow.hospitalId,
          payload = mapOf("patientId" to workflow.patientId),
        ),
      )
    }
    return TraumaWorkflowView.from(workflow)
  }

  @Transactional
  fun updateStatus(
    registrationId: UUID,
    dto: EmergencyStatusDto,
    actorId: UUID,
  ): EmergencyRegistrationView {
    val registration = findRegistration(registrationId)
    val status = dto.status ?: throw IllegalArgumentException("EMERGENCY_STATUS_REQUIRED")
    if (status !in REGISTRATION_STATUSES) throw IllegalArgumentException("EMERGENCY_INVALID_STATUS")
    val now = Instant.now()
    registration.status = status
    registration.updatedBy = actorId
    when (status) {
      "admitted" -> {
        val admissionId = dto.admittedAdmissionId ?: throw IllegalArgumentException("EMERGENCY_DISPOSITION_ADMISSION_REQUIRED")
        registration.admittedAdmissionId = admissionId
      }

      "discharged" -> {
        registration.dischargedAt = now
        registration.admittedAdmissionId = null
      }

      else -> {
        if (status != "admitted") registration.admittedAdmissionId = null
      }
    }
    registrationRepository.save(registration)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "emergency.disposition_changed",
        occurredAt = now,
        source = "emergency-service",
        entityType = "emergency_registration",
        entityId = registrationId,
        hospitalId = registration.hospitalId,
        payload = mapOf("patientId" to registration.patientId, "status" to status),
      ),
    )
    return EmergencyRegistrationView.from(registration)
  }

  private fun findRegistration(id: UUID): EmergencyRegistration =
    registrationRepository.findById(id).orElseThrow { IllegalArgumentException("EMERGENCY_REGISTRATION_NOT_FOUND") }

  companion object {
    val ARRIVAL_MODES = setOf("walk_in", "ambulance", "transfer")
    val TRAUMA_PHASES = setOf("primary", "secondary", "tertiary", "completed")
    val REGISTRATION_STATUSES = setOf("registered", "triaged", "in_treatment", "admitted", "discharged")
  }
}
