package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Invoice
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvoiceRepository : JpaRepository<Invoice, UUID> {
  fun findByPatientId(patientId: UUID): List<Invoice>

  fun findByVisitId(visitId: UUID): List<Invoice>

  fun findTopByOrderByInvoiceNumberDesc(): Invoice?

  fun findByStatus(status: String): List<Invoice>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID?,
  ): Invoice?
}
