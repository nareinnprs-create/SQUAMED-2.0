package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {
  fun findByInvoiceId(invoiceId: UUID): List<Payment>

  // 03.06 collections: payments of one method inside a window (recon + GL), hospital via the invoice.
  @Query(
    "select p from Payment p, com.squamed.core.billing.domain.Invoice i " +
      "where i.id = p.invoiceId and (:hid is null or i.hospitalId = :hid) and p.method = :method " +
      "and p.receivedAt >= :from and p.receivedAt < :to",
  )
  fun findForPeriod(
    @Param("hid") hospitalId: UUID?,
    @Param("method") method: String,
    @Param("from") from: Instant,
    @Param("to") to: Instant,
  ): List<Payment>

  // 03.06 cash-up: a cashier's cash payments since the shift opened.
  @Query(
    "select p from Payment p where p.receivedBy = :cashierId and p.method = 'cash' and p.receivedAt >= :from and p.receivedAt <= :to",
  )
  fun cashForCashier(
    @Param("cashierId") cashierId: UUID,
    @Param("from") from: Instant,
    @Param("to") to: Instant,
  ): List<Payment>
}
