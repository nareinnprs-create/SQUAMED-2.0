package com.squamed.core.emergency.repository

import com.squamed.core.emergency.domain.EmergencyRegistration
import com.squamed.core.emergency.domain.TraumaWorkflow
import com.squamed.core.emergency.domain.TriageAssessment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmergencyRegistrationRepository : JpaRepository<EmergencyRegistration, UUID> {
  fun findByStatus(status: String): List<EmergencyRegistration>

  fun findByPatientId(patientId: UUID): List<EmergencyRegistration>
}

interface TriageAssessmentRepository : JpaRepository<TriageAssessment, UUID> {
  fun findByRegistrationId(registrationId: UUID): List<TriageAssessment>
}

interface TraumaWorkflowRepository : JpaRepository<TraumaWorkflow, UUID> {
  fun findByRegistrationId(registrationId: UUID): TraumaWorkflow?
}
