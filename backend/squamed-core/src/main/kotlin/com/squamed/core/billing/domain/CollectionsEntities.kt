package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// 03.06 shift cash-up: expected = float + cash collected; variance on close.
@Entity
@Table(name = "shift_cashup", schema = "billing")
class ShiftCashup(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "cashier_id", nullable = false)
  var cashierId: UUID,
  @Column(name = "opened_at", nullable = false)
  var openedAt: Instant = Instant.now(),
  @Column(name = "closed_at")
  var closedAt: Instant? = null,
  @Column(name = "opening_float_qar", nullable = false, precision = 12, scale = 2)
  var openingFloatQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "cash_collected_qar", nullable = false, precision = 12, scale = 2)
  var cashCollectedQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "counted_cash_qar", precision = 12, scale = 2)
  var countedCashQar: BigDecimal? = null,
  @Column(name = "variance_qar", precision = 12, scale = 2)
  var varianceQar: BigDecimal? = null,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "OPEN",
  @Column(name = "note", length = 400)
  var note: String? = null,
) : BaseEntity()

// 03.06 payment reconciliation statement (gateway/bank total vs recorded payments).
@Entity
@Table(name = "recon_statement", schema = "billing")
class ReconStatement(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "method", nullable = false, length = 20)
  var method: String,
  @Column(name = "period_start", nullable = false)
  var periodStart: Instant,
  @Column(name = "period_end", nullable = false)
  var periodEnd: Instant,
  @Column(name = "statement_total_qar", nullable = false, precision = 12, scale = 2)
  var statementTotalQar: BigDecimal,
  @Column(name = "recorded_total_qar", nullable = false, precision = 12, scale = 2)
  var recordedTotalQar: BigDecimal,
  @Column(name = "variance_qar", nullable = false, precision = 12, scale = 2)
  var varianceQar: BigDecimal,
  @Column(name = "status", nullable = false, length = 10)
  var status: String,
) : BaseEntity()

// 03.06 refund: money out against one payment, supervisor-gated with a reason.
@Entity
@Table(name = "refund", schema = "billing")
class Refund(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "payment_id", nullable = false)
  var paymentId: UUID,
  @Column(name = "invoice_id", nullable = false)
  var invoiceId: UUID,
  @Column(name = "amount_qar", nullable = false, precision = 12, scale = 2)
  var amountQar: BigDecimal,
  @Column(name = "reason", nullable = false, length = 400)
  var reason: String,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "issued",
  @Column(name = "issued_by")
  var issuedBy: UUID? = null,
  @Column(name = "issued_at", nullable = false)
  var issuedAt: Instant = Instant.now(),
) : BaseEntity()
