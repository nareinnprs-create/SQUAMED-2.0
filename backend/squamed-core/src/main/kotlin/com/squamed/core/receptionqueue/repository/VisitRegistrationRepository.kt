package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.VisitRegistration
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VisitRegistrationRepository : JpaRepository<VisitRegistration, UUID> {
  fun findByPatientId(patientId: UUID): List<VisitRegistration>

  fun findByVisitNumber(visitNumber: String): VisitRegistration?

  fun findByStatus(status: String): List<VisitRegistration>

  fun findByHospitalId(hospitalId: UUID): List<VisitRegistration>

  fun findTopByOrderByVisitNumberDesc(): VisitRegistration?
}
