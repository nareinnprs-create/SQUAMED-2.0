package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.ContactPoint
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContactPointRepository : JpaRepository<ContactPoint, UUID> {
  fun findByPatientId(patientId: UUID): List<ContactPoint>
}
