package com.squamed.core.ops.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.ops.api.AssetCreateDto
import com.squamed.core.ops.api.AssetView
import com.squamed.core.ops.api.CycleView
import com.squamed.core.ops.api.EdDashboardView
import com.squamed.core.ops.api.EdEncounterCreateDto
import com.squamed.core.ops.api.EdEncounterView
import com.squamed.core.ops.api.IssueView
import com.squamed.core.ops.api.OrderView
import com.squamed.core.ops.api.ReceiptCreateDto
import com.squamed.core.ops.api.ReceiptLineDto
import com.squamed.core.ops.api.ReceiptView
import com.squamed.core.ops.api.RequisitionCreateDto
import com.squamed.core.ops.api.RequisitionLineDto
import com.squamed.core.ops.api.RequisitionView
import com.squamed.core.ops.api.StockView
import com.squamed.core.ops.api.TraceabilityView
import com.squamed.core.ops.api.TrayCreateDto
import com.squamed.core.ops.api.TrayView
import com.squamed.core.ops.api.VendorPerformanceView
import com.squamed.core.ops.api.WorkOrderCreateDto
import com.squamed.core.ops.api.WorkOrderView
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
import com.squamed.core.ops.repository.AssetRepository
import com.squamed.core.ops.repository.CssdCycleRepository
import com.squamed.core.ops.repository.CssdIssueRepository
import com.squamed.core.ops.repository.CssdTrayRepository
import com.squamed.core.ops.repository.EdEncounterRepository
import com.squamed.core.ops.repository.GoodsReceiptLineRepository
import com.squamed.core.ops.repository.GoodsReceiptRepository
import com.squamed.core.ops.repository.PurchaseOrderRepository
import com.squamed.core.ops.repository.PurchaseRequisitionRepository
import com.squamed.core.ops.repository.RequisitionLineRepository
import com.squamed.core.ops.repository.StockItemRepository
import com.squamed.core.ops.repository.WorkOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ProcurementService(
  private val requisitionRepository: PurchaseRequisitionRepository,
  private val lineRepository: RequisitionLineRepository,
  private val orderRepository: PurchaseOrderRepository,
  private val receiptRepository: GoodsReceiptRepository,
  private val receiptLineRepository: GoodsReceiptLineRepository,
  private val stockRepository: StockItemRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createRequisition(
    hospitalId: UUID,
    dto: RequisitionCreateDto,
    actorId: UUID?,
  ): RequisitionView {
    if (dto.department.isBlank()) throw IllegalArgumentException("REQUISITION_DEPARTMENT_REQUIRED")
    if (dto.lines.isEmpty()) throw IllegalArgumentException("REQUISITION_LINES_REQUIRED")
    if (dto.lines.any { it.quantity <= 0 }) throw IllegalArgumentException("REQUISITION_QUANTITY_INVALID")
    val saved =
      requisitionRepository.save(
        PurchaseRequisition(
          hospitalId = hospitalId,
          number = nextNumber("PR", requisitionRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).size),
          department = dto.department.trim(),
          notes = dto.notes,
          requestedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    dto.lines.forEach {
      lineRepository.save(
        RequisitionLine(
          requisitionId = saved.id!!,
          itemCode = it.itemCode.trim(),
          itemName = it.itemName.trim(),
          quantity = it.quantity,
          unitPriceQar = it.unitPriceQar,
        ).also { line -> line.createdBy = actorId },
      )
    }
    return view(saved)
  }

  @Transactional
  fun transitionRequisition(
    id: UUID,
    hospitalId: UUID,
    target: String,
    actorId: UUID?,
  ): RequisitionView {
    val requisition = findRequisition(id, hospitalId)
    val next = target.trim().uppercase()
    val allowed =
      when (requisition.status) {
        "DRAFT" -> setOf("SUBMITTED", "CANCELLED")
        "SUBMITTED" -> setOf("APPROVED", "CANCELLED")
        else -> emptySet()
      }
    if (next !in allowed) throw IllegalStateException("REQUISITION_TRANSITION_INVALID")
    requisition.status = next
    requisition.updatedBy = actorId
    return view(requisitionRepository.save(requisition))
  }

  @Transactional
  fun order(
    id: UUID,
    hospitalId: UUID,
    vendorId: UUID?,
    actorId: UUID?,
  ): OrderView {
    val requisition = findRequisition(id, hospitalId)
    if (requisition.status != "APPROVED") throw IllegalStateException("REQUISITION_NOT_APPROVED")
    val lines = lineRepository.findByRequisitionId(id)
    val total = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.unitPriceQar.multiply(BigDecimal.valueOf(line.quantity.toLong()))) }
    val order =
      orderRepository.save(
        PurchaseOrder(
          hospitalId = hospitalId,
          requisitionId = id,
          vendorId = vendorId,
          number = nextNumber("PO", orderRepository.findByHospitalIdOrderByIssuedAtDesc(hospitalId).size),
          totalQar = total,
        ).also { it.createdBy = actorId },
      )
    requisition.status = "ORDERED"
    requisition.updatedBy = actorId
    requisitionRepository.save(requisition)
    publish(hospitalId, "ops.po_issued", "purchase_order", order.id, actorId, mapOf("totalQar" to total.toString(), "vendorId" to vendorId?.toString()))
    return orderView(order)
  }

  @Transactional
  fun receive(
    poId: UUID,
    hospitalId: UUID,
    dto: ReceiptCreateDto,
    actorId: UUID?,
  ): ReceiptView {
    val order = orderRepository.findByIdAndHospitalId(poId, hospitalId) ?: throw IllegalArgumentException("ORDER_NOT_FOUND")
    if (order.status != "ISSUED") throw IllegalStateException("ORDER_NOT_OPEN")
    if (dto.lines.isEmpty()) throw IllegalArgumentException("RECEIPT_LINES_REQUIRED")
    if (dto.lines.any { it.quantity <= 0 }) throw IllegalArgumentException("RECEIPT_QUANTITY_INVALID")
    val requisitionLines = lineRepository.findByRequisitionId(order.requisitionId).associateBy { it.itemCode }
    dto.lines.forEach {
      if (!requisitionLines.containsKey(it.itemCode)) throw IllegalArgumentException("RECEIPT_ITEM_NOT_ON_ORDER: ${it.itemCode}")
    }
    val receipt =
      receiptRepository.save(
        GoodsReceipt(
          hospitalId = hospitalId,
          poId = poId,
          number = nextNumber("GRN", receiptRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).size),
          receivedBy = actorId,
          notes = dto.notes,
        ).also { it.createdBy = actorId },
      )
    dto.lines.forEach { line ->
      receiptLineRepository.save(GoodsReceiptLine(grnId = receipt.id!!, itemCode = line.itemCode, quantity = line.quantity).also { it.createdBy = actorId })
      val requisitionLine = requisitionLines.getValue(line.itemCode)
      val stock =
        stockRepository.findByHospitalIdAndItemCode(hospitalId, line.itemCode)
          ?: StockItem(hospitalId = hospitalId, itemCode = line.itemCode, itemName = requisitionLine.itemName).also { it.createdBy = actorId }
      stock.quantityOnHand += line.quantity
      stock.updatedBy = actorId
      stockRepository.save(stock)
    }
    order.status = "RECEIVED"
    order.updatedBy = actorId
    orderRepository.save(order)
    publish(hospitalId, "ops.goods_received", "goods_receipt", receipt.id, actorId, mapOf("poId" to poId.toString(), "lines" to dto.lines.size))
    return ReceiptView(receipt.id, receipt.number, poId, dto.lines)
  }

  @Transactional(readOnly = true)
  fun requisitions(hospitalId: UUID): List<RequisitionView> = requisitionRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).map(::view)

  @Transactional(readOnly = true)
  fun orders(hospitalId: UUID): List<OrderView> = orderRepository.findByHospitalIdOrderByIssuedAtDesc(hospitalId).map(::orderView)

  @Transactional(readOnly = true)
  fun stock(hospitalId: UUID): List<StockView> =
    stockRepository.findByHospitalIdOrderByItemNameAsc(hospitalId).map { StockView(it.itemCode, it.itemName, it.quantityOnHand, it.reorderLevel) }

  @Transactional(readOnly = true)
  fun vendorPerformance(hospitalId: UUID): List<VendorPerformanceView> {
    val orders = orderRepository.findByHospitalIdOrderByIssuedAtDesc(hospitalId)
    val receipts = receiptRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
    return orders
      .groupBy { it.vendorId }
      .map { (vendorId, vendorOrders) ->
        val received = vendorOrders.filter { it.status == "RECEIVED" }
        val leadDays =
          received.mapNotNull { order ->
            receipts.firstOrNull { it.poId == order.id }?.let { Duration.between(order.issuedAt, it.createdAt).toDays().toDouble() }
          }
        VendorPerformanceView(
          vendorId = vendorId,
          orders = vendorOrders.size,
          received = received.size,
          totalQar = vendorOrders.fold(BigDecimal.ZERO) { acc, order -> acc.add(order.totalQar) },
          avgLeadDays = if (leadDays.isEmpty()) null else leadDays.average(),
        )
      }
  }

  private fun findRequisition(
    id: UUID,
    hospitalId: UUID,
  ): PurchaseRequisition = requisitionRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("REQUISITION_NOT_FOUND")

  private fun view(requisition: PurchaseRequisition): RequisitionView {
    val lines = requisition.id?.let { lineRepository.findByRequisitionId(it) } ?: emptyList()
    return RequisitionView(
      id = requisition.id,
      number = requisition.number,
      department = requisition.department,
      status = requisition.status,
      notes = requisition.notes,
      lines = lines.map { RequisitionLineDto(it.itemCode, it.itemName, it.quantity, it.unitPriceQar) },
      totalQar = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.unitPriceQar.multiply(BigDecimal.valueOf(line.quantity.toLong()))) },
    )
  }

  private fun orderView(order: PurchaseOrder) =
    OrderView(order.id, order.number, order.requisitionId, order.vendorId, order.status, order.totalQar, order.issuedAt)

  private fun nextNumber(
    prefix: String,
    existing: Int,
  ) = "%s-%06d".format(prefix, existing + 1)

  private fun publish(
    hospitalId: UUID,
    eventType: String,
    entityType: String,
    entityId: UUID?,
    actorId: UUID?,
    payload: Map<String, Any?>,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "ops-service",
        entityType = entityType,
        entityId = entityId,
        hospitalId = hospitalId,
        payload = payload,
        actorType = "user",
        actorId = actorId,
      ),
    )
  }
}

@Service
class CssdService(
  private val trayRepository: CssdTrayRepository,
  private val cycleRepository: CssdCycleRepository,
  private val issueRepository: CssdIssueRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createTray(
    hospitalId: UUID,
    dto: TrayCreateDto,
    actorId: UUID?,
  ): TrayView {
    if (dto.code.isBlank() || dto.name.isBlank()) throw IllegalArgumentException("TRAY_CODE_NAME_REQUIRED")
    if (trayRepository.findByHospitalIdAndCode(hospitalId, dto.code.trim()) != null) throw IllegalArgumentException("TRAY_CODE_EXISTS")
    val tray = trayRepository.save(CssdTray(hospitalId = hospitalId, code = dto.code.trim(), name = dto.name.trim()).also { it.createdBy = actorId })
    return trayView(tray)
  }

  @Transactional
  fun startCycle(
    trayId: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): CycleView {
    val tray = findTray(trayId, hospitalId)
    if (tray.status != "AVAILABLE") throw IllegalStateException("TRAY_NOT_AVAILABLE")
    tray.status = "IN_CYCLE"
    tray.updatedBy = actorId
    trayRepository.save(tray)
    val cycleNumber = cycleRepository.findByTrayIdOrderByCycleNumberAsc(trayId).size + 1
    val cycle = cycleRepository.save(CssdCycle(hospitalId = hospitalId, trayId = trayId, cycleNumber = cycleNumber).also { it.createdBy = actorId })
    return cycleView(cycle)
  }

  @Transactional
  fun completeCycle(
    cycleId: UUID,
    hospitalId: UUID,
    result: String,
    actorId: UUID?,
  ): CycleView {
    val cycle = cycleRepository.findByIdAndHospitalId(cycleId, hospitalId) ?: throw IllegalArgumentException("CYCLE_NOT_FOUND")
    if (cycle.status != "RUNNING") throw IllegalStateException("CYCLE_NOT_RUNNING")
    val outcome = result.trim().uppercase()
    if (outcome !in setOf("PASS", "FAIL")) throw IllegalArgumentException("CYCLE_RESULT_INVALID")
    cycle.status = if (outcome == "PASS") "COMPLETED" else "FAILED"
    cycle.result = outcome
    cycle.completedAt = Instant.now()
    cycle.updatedBy = actorId
    cycleRepository.save(cycle)
    val tray = findTray(cycle.trayId, hospitalId)
    tray.status = "AVAILABLE"
    tray.updatedBy = actorId
    trayRepository.save(tray)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "ops.sterilization_completed",
        occurredAt = Instant.now(),
        source = "ops-service",
        entityType = "cssd_cycle",
        entityId = cycle.id,
        hospitalId = hospitalId,
        payload = mapOf("trayId" to tray.id.toString(), "result" to outcome, "cycleNumber" to cycle.cycleNumber),
        actorType = "user",
        actorId = actorId,
      ),
    )
    return cycleView(cycle)
  }

  @Transactional
  fun issue(
    trayId: UUID,
    hospitalId: UUID,
    surgeryScheduleId: UUID?,
    actorId: UUID?,
  ): IssueView {
    val tray = findTray(trayId, hospitalId)
    if (tray.status != "AVAILABLE") throw IllegalStateException("TRAY_NOT_AVAILABLE")
    tray.status = "ISSUED"
    tray.updatedBy = actorId
    trayRepository.save(tray)
    val issue =
      issueRepository.save(
        CssdIssue(hospitalId = hospitalId, trayId = trayId, surgeryScheduleId = surgeryScheduleId, issuedBy = actorId).also { it.createdBy = actorId },
      )
    return issueView(issue)
  }

  @Transactional
  fun returnTray(
    trayId: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): IssueView {
    val issue = issueRepository.findByTrayIdAndReturnedAtIsNull(trayId) ?: throw IllegalStateException("TRAY_NOT_ISSUED")
    issue.returnedAt = Instant.now()
    issue.updatedBy = actorId
    issueRepository.save(issue)
    val tray = findTray(trayId, hospitalId)
    tray.status = "AVAILABLE"
    tray.updatedBy = actorId
    trayRepository.save(tray)
    return issueView(issue)
  }

  @Transactional(readOnly = true)
  fun trays(hospitalId: UUID): List<TrayView> = trayRepository.findByHospitalIdOrderByCodeAsc(hospitalId).map(::trayView)

  @Transactional(readOnly = true)
  fun traceability(
    trayId: UUID,
    hospitalId: UUID,
  ): TraceabilityView {
    val tray = findTray(trayId, hospitalId)
    return TraceabilityView(
      tray = trayView(tray),
      cycles = cycleRepository.findByTrayIdOrderByCycleNumberAsc(trayId).map(::cycleView),
      issues = issueRepository.findByTrayIdOrderByCreatedAtAsc(trayId).map(::issueView),
    )
  }

  private fun findTray(
    id: UUID,
    hospitalId: UUID,
  ): CssdTray = trayRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("TRAY_NOT_FOUND")

  private fun trayView(tray: CssdTray) = TrayView(tray.id, tray.code, tray.name, tray.status)

  private fun cycleView(cycle: CssdCycle) = CycleView(cycle.id, cycle.trayId, cycle.cycleNumber, cycle.status, cycle.result, cycle.completedAt)

  private fun issueView(issue: CssdIssue) = IssueView(issue.id, issue.trayId, issue.surgeryScheduleId, issue.createdAt, issue.returnedAt)
}

@Service
class FacilitiesService(
  private val assetRepository: AssetRepository,
  private val workOrderRepository: WorkOrderRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createAsset(
    hospitalId: UUID,
    dto: AssetCreateDto,
    actorId: UUID?,
  ): AssetView {
    if (dto.code.isBlank() || dto.name.isBlank()) throw IllegalArgumentException("ASSET_CODE_NAME_REQUIRED")
    if (assetRepository.findByHospitalIdAndCode(hospitalId, dto.code.trim()) != null) throw IllegalArgumentException("ASSET_CODE_EXISTS")
    val asset =
      assetRepository.save(
        Asset(
          hospitalId = hospitalId,
          code = dto.code.trim(),
          name = dto.name.trim(),
          category = dto.category?.trim() ?: "equipment",
          location = dto.location,
          nextPmOn = dto.nextPmOn,
        ).also { it.createdBy = actorId },
      )
    return assetView(asset)
  }

  @Transactional
  fun openWorkOrder(
    hospitalId: UUID,
    dto: WorkOrderCreateDto,
    actorId: UUID?,
  ): WorkOrderView {
    if (dto.description.isBlank()) throw IllegalArgumentException("WORK_ORDER_DESCRIPTION_REQUIRED")
    val asset = assetRepository.findByIdAndHospitalId(dto.assetId, hospitalId) ?: throw IllegalArgumentException("ASSET_NOT_FOUND")
    if (asset.status == "RETIRED") throw IllegalStateException("ASSET_RETIRED")
    asset.status = "UNDER_MAINTENANCE"
    asset.updatedBy = actorId
    assetRepository.save(asset)
    val order =
      workOrderRepository.save(
        WorkOrder(
          hospitalId = hospitalId,
          assetId = asset.id!!,
          number = "WO-%06d".format(workOrderRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).size + 1),
          type = dto.type?.trim()?.uppercase() ?: "CORRECTIVE",
          description = dto.description.trim(),
          downtimeMinutes = dto.downtimeMinutes ?: 0,
        ).also { it.createdBy = actorId },
      )
    publish(hospitalId, "ops.work_order_created", "work_order", order.id, actorId, mapOf("assetId" to asset.id.toString(), "type" to order.type))
    return workOrderView(order)
  }

  @Transactional
  fun completeWorkOrder(
    id: UUID,
    hospitalId: UUID,
    downtimeMinutes: Int?,
    actorId: UUID?,
  ): WorkOrderView {
    val order = workOrderRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("WORK_ORDER_NOT_FOUND")
    if (order.status != "OPEN") throw IllegalStateException("WORK_ORDER_NOT_OPEN")
    order.status = "COMPLETED"
    order.completedAt = Instant.now()
    downtimeMinutes?.let { order.downtimeMinutes = it }
    order.updatedBy = actorId
    workOrderRepository.save(order)
    val asset = assetRepository.findByIdAndHospitalId(order.assetId, hospitalId)
    asset?.let {
      it.status = "ACTIVE"
      it.updatedBy = actorId
      assetRepository.save(it)
    }
    publish(
      hospitalId,
      "ops.work_order_completed",
      "work_order",
      order.id,
      actorId,
      mapOf(
        "assetId" to order.assetId.toString(),
        "downtimeMinutes" to order.downtimeMinutes,
      ),
    )
    return workOrderView(order)
  }

  @Transactional(readOnly = true)
  fun assets(hospitalId: UUID): List<AssetView> = assetRepository.findByHospitalIdOrderByCodeAsc(hospitalId).map(::assetView)

  @Transactional(readOnly = true)
  fun workOrders(
    hospitalId: UUID,
    status: String?,
  ): List<WorkOrderView> =
    (
      if (status.isNullOrBlank()) {
        workOrderRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
      } else {
        workOrderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status.uppercase())
      }
    ).map(::workOrderView)

  private fun assetView(asset: Asset) = AssetView(asset.id, asset.code, asset.name, asset.category, asset.location, asset.status, asset.nextPmOn)

  private fun workOrderView(order: WorkOrder) =
    WorkOrderView(order.id, order.number, order.assetId, order.type, order.status, order.description, order.downtimeMinutes, order.completedAt)

  private fun publish(
    hospitalId: UUID,
    eventType: String,
    entityType: String,
    entityId: UUID?,
    actorId: UUID?,
    payload: Map<String, Any?>,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "ops-service",
        entityType = entityType,
        entityId = entityId,
        hospitalId = hospitalId,
        payload = payload,
        actorType = "user",
        actorId = actorId,
      ),
    )
  }
}

@Service
class EdService(
  private val encounterRepository: EdEncounterRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun create(
    hospitalId: UUID,
    dto: EdEncounterCreateDto,
    actorId: UUID?,
  ): EdEncounterView {
    if (dto.triageLevel !in 1..5) throw IllegalArgumentException("ED_TRIAGE_INVALID")
    if (dto.chiefComplaint.isBlank()) throw IllegalArgumentException("ED_COMPLAINT_REQUIRED")
    val resus = dto.resusActivated ?: (dto.triageLevel <= 2)
    val saved =
      encounterRepository.save(
        EdEncounter(
          hospitalId = hospitalId,
          patientId = dto.patientId,
          triageLevel = dto.triageLevel,
          chiefComplaint = dto.chiefComplaint.trim(),
          status = if (resus) "RESUS" else "WAITING",
          resusActivated = resus,
        ).also { it.createdBy = actorId },
      )
    return view(saved)
  }

  @Transactional
  fun transition(
    id: UUID,
    hospitalId: UUID,
    target: String,
    actorId: UUID?,
  ): EdEncounterView {
    val encounter = encounterRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("ED_ENCOUNTER_NOT_FOUND")
    val next = target.trim().uppercase()
    val allowed =
      when (encounter.status) {
        "WAITING" -> setOf("IN_TREATMENT", "RESUS", "DISCHARGED")
        "RESUS" -> setOf("IN_TREATMENT", "DISCHARGED")
        "IN_TREATMENT" -> setOf("ADMITTED", "DISCHARGED")
        else -> emptySet()
      }
    if (next !in allowed) throw IllegalStateException("ED_TRANSITION_INVALID")
    encounter.status = next
    encounter.resusActivated = encounter.resusActivated || next == "RESUS"
    if (next == "DISCHARGED") encounter.closedAt = Instant.now()
    encounter.updatedBy = actorId
    val saved = encounterRepository.save(encounter)
    if (next == "DISCHARGED") {
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "ops.ed_encounter_closed",
          occurredAt = Instant.now(),
          source = "ops-service",
          entityType = "ed_encounter",
          entityId = saved.id,
          hospitalId = hospitalId,
          payload = mapOf("patientId" to saved.patientId.toString(), "triageLevel" to saved.triageLevel),
          actorType = "user",
          actorId = actorId,
        ),
      )
    }
    return view(saved)
  }

  @Transactional(readOnly = true)
  fun list(hospitalId: UUID): List<EdEncounterView> = encounterRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId).map(::view)

  @Transactional(readOnly = true)
  fun dashboard(hospitalId: UUID): EdDashboardView {
    val all = encounterRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
    val active = all.filter { it.status != "DISCHARGED" }
    return EdDashboardView(
      waiting = all.count { it.status == "WAITING" },
      inTreatment = all.count { it.status == "IN_TREATMENT" },
      resus = all.count { it.status == "RESUS" },
      admitted = all.count { it.status == "ADMITTED" },
      discharged = all.count { it.status == "DISCHARGED" },
      byTriage = active.groupingBy { "ESI${it.triageLevel}" }.eachCount(),
    )
  }

  private fun view(encounter: EdEncounter) =
    EdEncounterView(
      encounter.id,
      encounter.patientId,
      encounter.triageLevel,
      encounter.chiefComplaint,
      encounter.status,
      encounter.resusActivated,
      encounter.closedAt,
    )
}
