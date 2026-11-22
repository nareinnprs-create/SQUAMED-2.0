package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.CreditNote
import com.squamed.core.billing.domain.HospitalTaxProfile
import com.squamed.core.billing.domain.TaxLine
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HospitalTaxProfileRepository : JpaRepository<HospitalTaxProfile, UUID> {
  fun findByHospitalId(hospitalId: UUID): HospitalTaxProfile?
}

interface TaxLineRepository : JpaRepository<TaxLine, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<TaxLine>
}

interface CreditNoteRepository : JpaRepository<CreditNote, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<CreditNote>
}
