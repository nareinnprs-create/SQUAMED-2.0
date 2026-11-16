package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "consult_receipt", schema = "reception")
class ConsultReceipt(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "visit_id", nullable = false)
  var visitId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "receipt_number", nullable = false, length = 20)
  var receiptNumber: String,
  @Column(name = "amount_qar", nullable = false, precision = 12, scale = 2)
  var amountQar: BigDecimal,
  @Column(name = "mode", nullable = false, length = 20)
  var mode: String = "cash",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "issued",
  @Column(name = "issued_at", nullable = false)
  var issuedAt: Instant,
  @Column(name = "voided_at")
  var voidedAt: Instant? = null,
) : BaseEntity()
