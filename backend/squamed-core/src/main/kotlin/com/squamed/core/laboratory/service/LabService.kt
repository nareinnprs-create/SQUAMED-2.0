package com.squamed.core.laboratory.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.laboratory.api.CreateLabOrderDto
import com.squamed.core.laboratory.api.LabOrderDetailView
import com.squamed.core.laboratory.api.LabOrderView
import com.squamed.core.laboratory.api.LabTestRequestView
import com.squamed.core.laboratory.api.LabTestResultInput
import com.squamed.core.laboratory.domain.LabOrder
import com.squamed.core.laboratory.domain.LabTestRequest
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.laboratory.repository.LabTestRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Year
import java.util.UUID

@Service
class LabService(
  private val labOrderRepository: LabOrderRepository,
  private val labTestRequestRepository: LabTestRequestRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createOrder(
    patientId: UUID,
    encounterId: UUID? = null,
    dto: CreateLabOrderDto,
  ): LabOrderDetailView {
    val now = Instant.now()
    val saved =
      labOrderRepository.save(
        LabOrder(
          patientId = patientId,
          encounterId = encounterId,
          orderNumber = generateOrderNumber(),
          priority = dto.priority ?: "routine",
          status = "ordered",
          resultStatus = "pending",
          clinicalNote = dto.clinicalNote,
          orderedBy = dto.orderedBy,
          orderedAt = now,
        ),
      )
    val orderId = saved.id ?: throw IllegalStateException("LAB_ORDER_SAVE_FAILED")
    dto.tests.forEach { test ->
      labTestRequestRepository.save(
        LabTestRequest(
          labOrderId = orderId,
          testCode = test.testCode,
          testName = test.testName,
          status = "ordered",
        ),
      )
    }
    return getOrderDetail(orderId)
  }

  @Transactional(readOnly = true)
  fun listOrders(
    patientId: UUID? = null,
    status: String? = null,
    resultStatus: String? = null,
  ): List<LabOrderView> {
    var orders =
      if (patientId != null) {
        labOrderRepository.findByPatientId(patientId)
      } else {
        labOrderRepository.findAll()
      }
    if (status != null) orders = orders.filter { it.status == status }
    if (resultStatus != null) orders = orders.filter { it.resultStatus == resultStatus }
    return orders
      .sortedByDescending { it.orderedAt ?: Instant.EPOCH }
      .map { LabOrderView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getOrder(orderId: UUID): LabOrderDetailView = getOrderDetail(orderId)

  @Transactional
  fun collectSpecimen(
    orderId: UUID,
    collectedBy: UUID? = null,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "ordered") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    labOrder.status = "collected"
    labOrder.specimenTakenAt = Instant.now()
    labOrder.collectedBy = collectedBy
    return getOrderDetail(orderId)
  }

  @Transactional
  fun receiveSpecimen(orderId: UUID): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "ordered" && labOrder.status != "collected") {
      throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    }
    labOrder.status = "received"
    labOrder.receivedAt = Instant.now()
    return getOrderDetail(orderId)
  }

  @Transactional
  fun startProcessing(orderId: UUID): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "received") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    labOrder.status = "processing"
    labOrder.startedAt = Instant.now()
    return getOrderDetail(orderId)
  }

  @Transactional
  fun enterResults(
    orderId: UUID,
    results: List<LabTestResultInput>,
    enteredBy: UUID? = null,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "processing") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    val now = Instant.now()
    results.forEach { result ->
      val request =
        labTestRequestRepository
          .findById(result.testRequestId)
          .orElseThrow { IllegalArgumentException("LAB_TEST_REQUEST_NOT_FOUND") }
      if (request.labOrderId != orderId) throw IllegalArgumentException("LAB_TEST_REQUEST_NOT_FOUND")
      request.resultValue = result.resultValue
      if (result.unit != null) request.unit = result.unit
      if (result.referenceRange != null) request.referenceRange = result.referenceRange
      request.isAbnormal = computeIsAbnormal(request.resultValue, request.referenceRange, result.isAbnormal)
      request.status = "resulted"
      request.resultEnteredBy = enteredBy
      request.resultEnteredAt = now
    }
    labOrder.status = "resulted"
    return getOrderDetail(orderId)
  }

  @Transactional
  fun verifyOrder(
    orderId: UUID,
    verifierId: UUID,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "resulted") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    labOrder.resultStatus = "verified"
    labOrder.verifiedAt = Instant.now()
    labOrder.verifiedBy = verifierId
    return getOrderDetail(orderId)
  }

  @Transactional
  fun releaseOrder(
    orderId: UUID,
    releaseorId: UUID,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.resultStatus != "verified") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    labOrder.resultStatus = "released"
    labOrder.releasedAt = Instant.now()
    labOrder.releasedBy = releaseorId
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "lab.result.released",
        occurredAt = Instant.now(),
        source = "lab-service",
        entityType = "lab-order",
        entityId = orderId,
        hospitalId = labOrder.hospitalId,
        payload =
          mapOf(
            "orderNumber" to labOrder.orderNumber,
            "resultStatus" to labOrder.resultStatus,
          ),
        recipientIds = emptyList(),
      ),
    )
    return getOrderDetail(orderId)
  }

  @Transactional
  fun rejectOrder(
    orderId: UUID,
    reason: String,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    labOrder.status = "rejected"
    labOrder.rejectedReason = reason
    return getOrderDetail(orderId)
  }

  private fun getOrderDetail(orderId: UUID): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    val tests = labTestRequestRepository.findByLabOrderId(orderId).map { LabTestRequestView.from(it) }
    return LabOrderDetailView(order = LabOrderView.from(labOrder), tests = tests)
  }

  private fun findOrder(orderId: UUID): LabOrder = labOrderRepository.findById(orderId).orElseThrow { IllegalArgumentException("LAB_ORDER_NOT_FOUND") }

  private fun generateOrderNumber(): String {
    val year = Year.now().value
    val latest = labOrderRepository.findTopByOrderByOrderNumberDesc()
    val next =
      latest?.let { order ->
        val parts = order.orderNumber.split("-")
        if (parts.size < 3) return@let 1
        if (parts[1].toIntOrNull() != year) return@let 1
        (parts[2].toIntOrNull() ?: 0) + 1
      } ?: 1
    return String.format("LO-%d-%04d", year, next)
  }

  private fun computeIsAbnormal(
    resultValue: String?,
    referenceRange: String?,
    fallback: Boolean?,
  ): Boolean {
    val value = resultValue?.trim()?.toDoubleOrNull()
    val range = referenceRange?.trim()?.split("-")
    if (value != null && range != null && range.size == 2) {
      val lower = range[0].trim().toDoubleOrNull()
      val upper = range[1].trim().toDoubleOrNull()
      if (lower != null && upper != null) {
        return value < lower || value > upper
      }
    }
    return fallback ?: false
  }
}
