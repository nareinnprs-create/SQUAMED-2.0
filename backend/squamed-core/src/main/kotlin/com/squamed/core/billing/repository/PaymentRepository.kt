package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<Payment>
}
