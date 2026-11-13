package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Charge
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChargeRepository : JpaRepository<Charge, UUID> {
  fun findByPatientId(patientId: UUID): List<Charge>

  fun findByVisitId(visitId: UUID): List<Charge>

  fun findByPatientIdOrderByPostedAtDesc(patientId: UUID): List<Charge>

  fun findByVisitIdOrderByPostedAtDesc(visitId: UUID): List<Charge>
}
