package com.squamed.core.ops.api

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RequisitionLineDto(
  val itemCode: String,
  val itemName: String,
  val quantity: Int,
  val unitPriceQar: BigDecimal = BigDecimal.ZERO,
)

data class RequisitionCreateDto(
  val department: String,
  val notes: String? = null,
  val lines: List<RequisitionLineDto>,
)

data class RequisitionView(
  val id: UUID?,
  val number: String,
  val department: String,
  val status: String,
  val notes: String?,
  val lines: List<RequisitionLineDto>,
  val totalQar: BigDecimal,
)

data class OrderCreateDto(
  val vendorId: UUID? = null,
)

data class OrderView(
  val id: UUID?,
  val number: String,
  val requisitionId: UUID,
  val vendorId: UUID?,
  val status: String,
  val totalQar: BigDecimal,
  val issuedAt: Instant?,
)

data class ReceiptLineDto(
  val itemCode: String,
  val quantity: Int,
)

data class ReceiptCreateDto(
  val lines: List<ReceiptLineDto>,
  val notes: String? = null,
)

data class ReceiptView(
  val id: UUID?,
  val number: String,
  val poId: UUID,
  val lines: List<ReceiptLineDto>,
)

data class StockView(
  val itemCode: String,
  val itemName: String,
  val quantityOnHand: Int,
  val reorderLevel: Int,
)

data class VendorPerformanceView(
  val vendorId: UUID?,
  val orders: Int,
  val received: Int,
  val totalQar: BigDecimal,
  val avgLeadDays: Double?,
)

data class TrayCreateDto(
  val code: String,
  val name: String,
)

data class TrayView(
  val id: UUID?,
  val code: String,
  val name: String,
  val status: String,
)

data class CycleView(
  val id: UUID?,
  val trayId: UUID,
  val cycleNumber: Int,
  val status: String,
  val result: String?,
  val completedAt: Instant?,
)

data class CycleCompleteDto(
  val result: String,
)

data class IssueCreateDto(
  val surgeryScheduleId: UUID? = null,
)

data class IssueView(
  val id: UUID?,
  val trayId: UUID,
  val surgeryScheduleId: UUID?,
  val issuedAt: Instant?,
  val returnedAt: Instant?,
)

data class TraceabilityView(
  val tray: TrayView,
  val cycles: List<CycleView>,
  val issues: List<IssueView>,
)

data class AssetCreateDto(
  val code: String,
  val name: String,
  val category: String? = null,
  val location: String? = null,
  val nextPmOn: LocalDate? = null,
)

data class AssetView(
  val id: UUID?,
  val code: String,
  val name: String,
  val category: String,
  val location: String?,
  val status: String,
  val nextPmOn: LocalDate?,
)

data class WorkOrderCreateDto(
  val assetId: UUID,
  val type: String? = null,
  val description: String,
  val downtimeMinutes: Int? = null,
)

data class WorkOrderView(
  val id: UUID?,
  val number: String,
  val assetId: UUID,
  val type: String,
  val status: String,
  val description: String,
  val downtimeMinutes: Int,
  val completedAt: Instant?,
)

data class EdEncounterCreateDto(
  val patientId: UUID,
  val triageLevel: Int,
  val chiefComplaint: String,
  val resusActivated: Boolean? = null,
)

data class EdStatusDto(
  val status: String,
)

data class EdEncounterView(
  val id: UUID?,
  val patientId: UUID,
  val triageLevel: Int,
  val chiefComplaint: String,
  val status: String,
  val resusActivated: Boolean,
  val closedAt: Instant?,
  val triageScaleCode: String? = null,
  val bayId: UUID? = null,
  val traumaActivated: Boolean = false,
)

data class EdDashboardView(
  val waiting: Int,
  val inTreatment: Int,
  val resus: Int,
  val admitted: Int,
  val discharged: Int,
  val byTriage: Map<String, Int>,
  val resusAvailable: Int = 0,
  val resusOccupied: Int = 0,
  val traumaActive: Int = 0,
  val massCasualtyActive: Boolean = false,
)

data class OpsStatusDto(
  val status: String,
)

data class WorkOrderCompleteDto(
  val downtimeMinutes: Int? = null,
)

data class TriageScaleDto(
  val code: String,
  val name: String,
  val maxLevel: Int,
)

data class TriageScaleView(
  val id: UUID?,
  val code: String,
  val name: String,
  val maxLevel: Int,
  val active: Boolean,
)

data class EdBayDto(
  val code: String,
  val name: String,
  val type: String = "acute",
)

data class EdBayView(
  val id: UUID?,
  val code: String,
  val name: String,
  val type: String,
  val status: String,
)

data class BayAssignDto(
  val bayId: UUID,
)

data class TraumaActivateDto(
  val activate: Boolean = true,
)

data class MassCasualtyDto(
  val note: String? = null,
)

data class MassCasualtyView(
  val id: UUID?,
  val status: String,
  val note: String?,
  val declaredAt: Instant?,
  val stoodDownAt: Instant?,
)
