package com.squamed.core.kiosk.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment_receipt", schema = "kiosk")
class KioskPaymentReceipt(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "invoice_id", nullable = false) var invoiceId: UUID,
  @Column(name = "payment_id", nullable = false) var paymentId: UUID,
  @Column(name = "amount_qar", nullable = false) var amountQar: BigDecimal,
  @Column(name = "method", nullable = false, length = 30) var method: String,
  @Column(name = "receipt_number", nullable = false, length = 40) var receiptNumber: String,
) : BaseEntity()

@Entity
@Table(name = "report_collection", schema = "kiosk")
class KioskReportCollection(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "report_type", nullable = false, length = 10) var reportType: String,
  @Column(name = "report_id", nullable = false) var reportId: UUID,
  @Column(name = "receipt_number", nullable = false, length = 40) var receiptNumber: String,
  @Column(name = "collected_at", nullable = false) var collectedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "campus", schema = "kiosk")
class KioskCampus(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "name", nullable = false, length = 160) var name: String,
) : BaseEntity()

@Entity
@Table(name = "map_node", schema = "kiosk")
class KioskMapNode(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "campus_id", nullable = false) var campusId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "name", nullable = false, length = 160) var name: String,
  @Column(name = "node_type", nullable = false, length = 30) var nodeType: String = "ROOM",
  @Column(name = "floor", length = 20) var floor: String? = null,
  @Column(name = "x") var x: BigDecimal? = null,
  @Column(name = "y") var y: BigDecimal? = null,
) : BaseEntity()

@Entity
@Table(name = "map_edge", schema = "kiosk")
class KioskMapEdge(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "campus_id", nullable = false) var campusId: UUID,
  @Column(name = "from_node_id", nullable = false) var fromNodeId: UUID,
  @Column(name = "to_node_id", nullable = false) var toNodeId: UUID,
  @Column(name = "distance_m", nullable = false) var distanceM: BigDecimal,
  @Column(name = "accessible", nullable = false) var accessible: Boolean = true,
  @Column(name = "bidirectional", nullable = false) var bidirectional: Boolean = true,
) : BaseEntity()
