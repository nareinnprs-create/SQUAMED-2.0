package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment", schema = "billing")
class Payment(
  @Column(name = "invoice_id", nullable = false)
  var invoiceId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "amount_qar", nullable = false, precision = 12, scale = 2)
  var amountQar: BigDecimal,
  @Column(name = "method", nullable = false, length = 30)
  var method: String,
  @Column(name = "reference", length = 100)
  var reference: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "completed",
  @Column(name = "received_by")
  var receivedBy: UUID? = null,
  @Column(name = "received_at", nullable = false)
  var receivedAt: Instant = Instant.now(),
) : BaseEntity()
