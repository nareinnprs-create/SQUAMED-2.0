package com.squamed.core.ops.repository

import com.squamed.core.ops.domain.Asset
import com.squamed.core.ops.domain.CssdCycle
import com.squamed.core.ops.domain.CssdIssue
import com.squamed.core.ops.domain.CssdTray
import com.squamed.core.ops.domain.EdEncounter
import com.squamed.core.ops.domain.GoodsReceipt
import com.squamed.core.ops.domain.GoodsReceiptLine
import com.squamed.core.ops.domain.PurchaseOrder
import com.squamed.core.ops.domain.PurchaseRequisition
import com.squamed.core.ops.domain.RequisitionLine
import com.squamed.core.ops.domain.StockItem
import com.squamed.core.ops.domain.WorkOrder
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PurchaseRequisitionRepository : JpaRepository<PurchaseRequisition, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): PurchaseRequisition?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<PurchaseRequisition>
}

interface RequisitionLineRepository : JpaRepository<RequisitionLine, UUID> {
  fun findByRequisitionId(requisitionId: UUID): List<RequisitionLine>
}

interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): PurchaseOrder?

  fun findByHospitalIdOrderByIssuedAtDesc(hospitalId: UUID): List<PurchaseOrder>
}

interface GoodsReceiptRepository : JpaRepository<GoodsReceipt, UUID> {
  fun findByPoIdOrderByCreatedAtAsc(poId: UUID): List<GoodsReceipt>

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<GoodsReceipt>
}

interface GoodsReceiptLineRepository : JpaRepository<GoodsReceiptLine, UUID> {
  fun findByGrnId(grnId: UUID): List<GoodsReceiptLine>
}

interface StockItemRepository : JpaRepository<StockItem, UUID> {
  fun findByHospitalIdAndItemCode(
    hospitalId: UUID,
    itemCode: String,
  ): StockItem?

  fun findByHospitalIdOrderByItemNameAsc(hospitalId: UUID): List<StockItem>
}

interface CssdTrayRepository : JpaRepository<CssdTray, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): CssdTray?

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): CssdTray?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<CssdTray>
}

interface CssdCycleRepository : JpaRepository<CssdCycle, UUID> {
  fun findByTrayIdOrderByCycleNumberAsc(trayId: UUID): List<CssdCycle>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): CssdCycle?
}

interface CssdIssueRepository : JpaRepository<CssdIssue, UUID> {
  fun findByTrayIdOrderByCreatedAtAsc(trayId: UUID): List<CssdIssue>

  fun findByTrayIdAndReturnedAtIsNull(trayId: UUID): CssdIssue?
}

interface AssetRepository : JpaRepository<Asset, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): Asset?

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Asset?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<Asset>
}

interface WorkOrderRepository : JpaRepository<WorkOrder, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): WorkOrder?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<WorkOrder>

  fun findByHospitalIdAndStatusOrderByCreatedAtDesc(
    hospitalId: UUID,
    status: String,
  ): List<WorkOrder>
}

interface EdEncounterRepository : JpaRepository<EdEncounter, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): EdEncounter?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<EdEncounter>

  fun findByHospitalIdAndStatus(
    hospitalId: UUID,
    status: String,
  ): List<EdEncounter>
}

// 07.05 full ED: scales, bays, mass-casualty.
interface EdTriageScaleRepository : JpaRepository<com.squamed.core.ops.domain.EdTriageScale, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): com.squamed.core.ops.domain.EdTriageScale?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<com.squamed.core.ops.domain.EdTriageScale>
}

interface EdBayRepository : JpaRepository<com.squamed.core.ops.domain.EdBay, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): com.squamed.core.ops.domain.EdBay?

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): com.squamed.core.ops.domain.EdBay?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<com.squamed.core.ops.domain.EdBay>
}

interface MassCasualtyRepository : JpaRepository<com.squamed.core.ops.domain.MassCasualtyEvent, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): com.squamed.core.ops.domain.MassCasualtyEvent?

  fun findByHospitalIdAndStatus(
    hospitalId: UUID,
    status: String,
  ): List<com.squamed.core.ops.domain.MassCasualtyEvent>
}
