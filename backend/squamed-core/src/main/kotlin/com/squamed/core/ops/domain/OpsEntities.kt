package com.squamed.core.ops.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "purchase_requisition", schema = "ops")
class PurchaseRequisition(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "number", nullable = false, length = 40)
  var number: String,
  @Column(name = "department", nullable = false, length = 100)
  var department: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "DRAFT",
  @Column(name = "notes", length = 600)
  var notes: String? = null,
  @Column(name = "requested_by")
  var requestedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "requisition_line", schema = "ops")
class RequisitionLine(
  @Column(name = "requisition_id", nullable = false)
  var requisitionId: UUID,
  @Column(name = "item_code", nullable = false, length = 60)
  var itemCode: String,
  @Column(name = "item_name", nullable = false, length = 200)
  var itemName: String,
  @Column(name = "quantity", nullable = false)
  var quantity: Int,
  @Column(name = "unit_price_qar", nullable = false)
  var unitPriceQar: BigDecimal = BigDecimal.ZERO,
) : BaseEntity()

@Entity
@Table(name = "purchase_order", schema = "ops")
class PurchaseOrder(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "requisition_id", nullable = false)
  var requisitionId: UUID,
  @Column(name = "vendor_id")
  var vendorId: UUID? = null,
  @Column(name = "number", nullable = false, length = 40)
  var number: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "ISSUED",
  @Column(name = "total_qar", nullable = false)
  var totalQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "issued_at", nullable = false)
  var issuedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "goods_receipt", schema = "ops")
class GoodsReceipt(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "po_id", nullable = false)
  var poId: UUID,
  @Column(name = "number", nullable = false, length = 40)
  var number: String,
  @Column(name = "received_by")
  var receivedBy: UUID? = null,
  @Column(name = "notes", length = 400)
  var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "goods_receipt_line", schema = "ops")
class GoodsReceiptLine(
  @Column(name = "grn_id", nullable = false)
  var grnId: UUID,
  @Column(name = "item_code", nullable = false, length = 60)
  var itemCode: String,
  @Column(name = "quantity", nullable = false)
  var quantity: Int,
) : BaseEntity()

@Entity
@Table(name = "stock_item", schema = "ops")
class StockItem(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "item_code", nullable = false, length = 60)
  var itemCode: String,
  @Column(name = "item_name", nullable = false, length = 200)
  var itemName: String,
  @Column(name = "quantity_on_hand", nullable = false)
  var quantityOnHand: Int = 0,
  @Column(name = "reorder_level", nullable = false)
  var reorderLevel: Int = 0,
) : BaseEntity()

@Entity
@Table(name = "cssd_tray", schema = "ops")
class CssdTray(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 60)
  var code: String,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "AVAILABLE",
) : BaseEntity()

@Entity
@Table(name = "cssd_cycle", schema = "ops")
class CssdCycle(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "tray_id", nullable = false)
  var trayId: UUID,
  @Column(name = "cycle_number", nullable = false)
  var cycleNumber: Int,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "RUNNING",
  @Column(name = "result", length = 20)
  var result: String? = null,
  @Column(name = "operator", length = 120)
  var operator: String? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "cssd_issue", schema = "ops")
class CssdIssue(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "tray_id", nullable = false)
  var trayId: UUID,
  @Column(name = "surgery_schedule_id")
  var surgeryScheduleId: UUID? = null,
  @Column(name = "issued_by")
  var issuedBy: UUID? = null,
  @Column(name = "returned_at")
  var returnedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "asset", schema = "ops")
class Asset(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 60)
  var code: String,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "category", nullable = false, length = 60)
  var category: String = "equipment",
  @Column(name = "location", length = 120)
  var location: String? = null,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "ACTIVE",
  @Column(name = "next_pm_on")
  var nextPmOn: LocalDate? = null,
) : BaseEntity()

@Entity
@Table(name = "work_order", schema = "ops")
class WorkOrder(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "asset_id", nullable = false)
  var assetId: UUID,
  @Column(name = "number", nullable = false, length = 40)
  var number: String,
  @Column(name = "type", nullable = false, length = 20)
  var type: String = "CORRECTIVE",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "OPEN",
  @Column(name = "description", nullable = false, length = 600)
  var description: String,
  @Column(name = "downtime_minutes", nullable = false)
  var downtimeMinutes: Int = 0,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "ed_encounter", schema = "ops")
class EdEncounter(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "triage_level", nullable = false)
  var triageLevel: Int,
  @Column(name = "chief_complaint", nullable = false, length = 400)
  var chiefComplaint: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "WAITING",
  @Column(name = "resus_activated", nullable = false)
  var resusActivated: Boolean = false,
  @Column(name = "closed_at")
  var closedAt: Instant? = null,
  // 07.05 full ED: scale, bay and trauma activation.
  @Column(name = "triage_scale_code", length = 20)
  var triageScaleCode: String? = null,
  @Column(name = "bay_id")
  var bayId: UUID? = null,
  @Column(name = "trauma_activated", nullable = false)
  var traumaActivated: Boolean = false,
  @Column(name = "trauma_activated_at")
  var traumaActivatedAt: Instant? = null,
) : BaseEntity()

// 07.05 full ED: configurable triage scale, resus/acute bays, mass-casualty mode.
@Entity
@Table(name = "ed_triage_scale", schema = "ops")
class EdTriageScale(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 20)
  var code: String,
  @Column(name = "name", nullable = false, length = 80)
  var name: String,
  @Column(name = "max_level", nullable = false)
  var maxLevel: Int,
  @Column(name = "active", nullable = false)
  var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "ed_bay", schema = "ops")
class EdBay(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 30)
  var code: String,
  @Column(name = "name", nullable = false, length = 80)
  var name: String,
  @Column(name = "type", nullable = false, length = 20)
  var type: String = "acute",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "AVAILABLE",
) : BaseEntity()

@Entity
@Table(name = "mass_casualty_event", schema = "ops")
class MassCasualtyEvent(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "ACTIVE",
  @Column(name = "declared_by")
  var declaredBy: UUID? = null,
  @Column(name = "declared_at", nullable = false)
  var declaredAt: Instant = Instant.now(),
  @Column(name = "stood_down_at")
  var stoodDownAt: Instant? = null,
) : BaseEntity()
