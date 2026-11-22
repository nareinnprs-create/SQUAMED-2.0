package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invoice", schema = "billing")
class Invoice(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "visit_id")
  var visitId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "invoice_number", nullable = false, length = 50)
  var invoiceNumber: String,
  @Column(name = "subtotal_qar", nullable = false, precision = 12, scale = 2)
  var subtotalQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "discount_qar", nullable = false, precision = 12, scale = 2)
  var discountQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "tax_qar", nullable = false, precision = 12, scale = 2)
  var taxQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "total_qar", nullable = false, precision = 12, scale = 2)
  var totalQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "amount_paid_qar", nullable = false, precision = 12, scale = 2)
  var amountPaidQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "amount_due_qar", nullable = false, precision = 12, scale = 2)
  var amountDueQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "unpaid",
  @Column(name = "insurance_claim_status", length = 30)
  var insuranceClaimStatus: String? = null,
  // 03.02: payer-class repricing + gated discount reason.
  @Column(name = "payer_class", nullable = false, length = 20)
  var payerClass: String = "general",
  @Column(name = "discount_reason", length = 400)
  var discountReason: String? = null,
  // 03.03: invoice crossed the hospital e-invoicing threshold.
  @Column(name = "e_invoice_required", nullable = false)
  var eInvoiceRequired: Boolean = false,
  @Column(name = "issued_at", nullable = false)
  var issuedAt: Instant = Instant.now(),
) : BaseEntity()
