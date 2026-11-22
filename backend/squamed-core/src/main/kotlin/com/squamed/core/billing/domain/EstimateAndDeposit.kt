package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// 03.02 estimate: quoted before service, items snapshot + total + expiry.
@Entity
@Table(name = "estimate", schema = "billing")
class Estimate(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "estimate_number", nullable = false, length = 50)
  var estimateNumber: String,
  @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
  var itemsJson: String,
  @Column(name = "total_qar", nullable = false, precision = 12, scale = 2)
  var totalQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "open",
  @Column(name = "expires_at")
  var expiresAt: Instant? = null,
) : BaseEntity()

// 03.02 deposit/advance: held money applied to one invoice.
@Entity
@Table(name = "deposit", schema = "billing")
class Deposit(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "amount_qar", nullable = false, precision = 12, scale = 2)
  var amountQar: BigDecimal,
  @Column(name = "method", nullable = false, length = 20)
  var method: String,
  @Column(name = "reference", length = 100)
  var reference: String? = null,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "held",
  @Column(name = "received_by")
  var receivedBy: UUID? = null,
  @Column(name = "received_at", nullable = false)
  var receivedAt: Instant = Instant.now(),
  @Column(name = "applied_invoice_id")
  var appliedInvoiceId: UUID? = null,
  @Column(name = "applied_at")
  var appliedAt: Instant? = null,
) : BaseEntity()
