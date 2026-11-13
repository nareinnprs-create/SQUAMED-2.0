package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.InvoiceLine
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvoiceLineRepository : JpaRepository<InvoiceLine, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<InvoiceLine>
}
