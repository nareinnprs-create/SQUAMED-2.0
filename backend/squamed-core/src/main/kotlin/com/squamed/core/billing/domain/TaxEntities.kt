package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// 03.03 India GST profile per hospital entity.
@Entity
@Table(name = "hospital_tax_profile", schema = "billing")
class HospitalTaxProfile(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "gstin", length = 20)
  var gstin: String? = null,
  @Column(name = "state_code", length = 4)
  var stateCode: String? = null,
  @Column(name = "is_gst_registered", nullable = false)
  var isGstRegistered: Boolean = true,
  @Column(name = "e_invoice_threshold_qar", precision = 12, scale = 2)
  var eInvoiceThresholdQar: BigDecimal? = null,
) : BaseEntity()

// 03.03 per-invoice tax line (grouped by HSN + category).
@Entity
@Table(name = "tax_line", schema = "billing")
class TaxLine(
  @Column(name = "invoice_id", nullable = false)
  var invoiceId: UUID,
  @Column(name = "hsn_code", length = 12)
  var hsnCode: String? = null,
  @Column(name = "tax_category", nullable = false, length = 20)
  var taxCategory: String,
  @Column(name = "taxable_qar", nullable = false, precision = 12, scale = 2)
  var taxableQar: BigDecimal,
  @Column(name = "rate_pct", nullable = false, precision = 5, scale = 2)
  var ratePct: BigDecimal,
  @Column(name = "tax_qar", nullable = false, precision = 12, scale = 2)
  var taxQar: BigDecimal,
  @Column(name = "is_exempt", nullable = false)
  var isExempt: Boolean = false,
) : BaseEntity()

// 03.03 supervisor-gated credit note (corrections after finalize are credit notes, never edits).
@Entity
@Table(name = "credit_note", schema = "billing")
class CreditNote(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "invoice_id", nullable = false)
  var invoiceId: UUID,
  @Column(name = "note_number", nullable = false, length = 50)
  var noteNumber: String,
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
