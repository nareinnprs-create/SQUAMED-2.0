package com.squamed.core.emergency.repository

import com.squamed.core.emergency.domain.EmergencyRegistration
import com.squamed.core.emergency.domain.TraumaWorkflow
import com.squamed.core.emergency.domain.TriageAssessment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EmergencyRegistrationRepository : JpaRepository<EmergencyRegistration, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U14) — admin callers pass null.
  @Query("select r from EmergencyRegistration r where (:hid is null or r.hospitalId = :hid) and r.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<EmergencyRegistration>

  @Query("select r from EmergencyRegistration r where (:hid is null or r.hospitalId = :hid) and r.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<EmergencyRegistration>

  @Query("select r from EmergencyRegistration r where (:hid is null or r.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<EmergencyRegistration>
}

interface TriageAssessmentRepository : JpaRepository<TriageAssessment, UUID> {
  fun findByRegistrationId(registrationId: UUID): List<TriageAssessment>
}

interface TraumaWorkflowRepository : JpaRepository<TraumaWorkflow, UUID> {
  fun findByRegistrationId(registrationId: UUID): TraumaWorkflow?
}
