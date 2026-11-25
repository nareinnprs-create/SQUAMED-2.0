package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.CreditNote
import com.squamed.core.billing.domain.HospitalTaxProfile
import com.squamed.core.billing.domain.TaxLine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface HospitalTaxProfileRepository : JpaRepository<HospitalTaxProfile, UUID> {
  fun findByHospitalId(hospitalId: UUID): HospitalTaxProfile?
}

interface TaxLineRepository : JpaRepository<TaxLine, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<TaxLine>

  // 07.04 GST pack: tax lines of invoices issued in a window.
  @Query(
    "select t from TaxLine t, com.squamed.core.billing.domain.Invoice i " +
      "where i.id = t.invoiceId and (:hid is null or i.hospitalId = :hid) " +
      "and i.issuedAt >= :from and i.issuedAt < :to",
  )
  fun findForInvoicePeriod(
    @Param("hid") hospitalId: UUID?,
    @Param("from") from: java.time.Instant,
    @Param("to") to: java.time.Instant,
  ): List<TaxLine>
}

interface CreditNoteRepository : JpaRepository<CreditNote, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<CreditNote>
}
