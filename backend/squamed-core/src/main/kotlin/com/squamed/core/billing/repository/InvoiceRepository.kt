package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Invoice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface InvoiceRepository : JpaRepository<Invoice, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U7/D9) — admin callers pass null.
  @Query("select i from Invoice i where (:hid is null or i.hospitalId = :hid) and i.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Invoice>

  @Query("select i from Invoice i where (:hid is null or i.hospitalId = :hid) and i.visitId = :visitId")
  fun findByVisitId(
    @Param("hid") hospitalId: UUID?,
    @Param("visitId") visitId: UUID,
  ): List<Invoice>

  fun findTopByOrderByInvoiceNumberDesc(): Invoice?

  @Query("select i from Invoice i where (:hid is null or i.hospitalId = :hid) and i.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<Invoice>

  @Query("select i from Invoice i where (:hid is null or i.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Invoice>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID?,
  ): Invoice?
}
