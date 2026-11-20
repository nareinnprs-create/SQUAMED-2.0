package com.squamed.core.laboratory.service

import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.laboratory.api.AmendResultDto
import com.squamed.core.laboratory.api.CreateLabOrderDto
import com.squamed.core.laboratory.api.CreateQcRunDto
import com.squamed.core.laboratory.api.CreateReferenceRangeDto
import com.squamed.core.laboratory.api.CriticalResultAckView
import com.squamed.core.laboratory.api.CriticalResultView
import com.squamed.core.laboratory.api.DeliverReportDto
import com.squamed.core.laboratory.api.LabOrderDetailView
import com.squamed.core.laboratory.api.LabOrderView
import com.squamed.core.laboratory.api.LabReportDownload
import com.squamed.core.laboratory.api.LabReportView
import com.squamed.core.laboratory.api.LabResultAmendmentView
import com.squamed.core.laboratory.api.LabTestRequestView
import com.squamed.core.laboratory.api.LabTestResultInput
import com.squamed.core.laboratory.api.QcRunView
import com.squamed.core.laboratory.api.ReferenceRangeView
import com.squamed.core.laboratory.api.SpecimenCustodyView
import com.squamed.core.laboratory.api.TatRowView
import com.squamed.core.laboratory.domain.CriticalResult
import com.squamed.core.laboratory.domain.CriticalResultAck
import com.squamed.core.laboratory.domain.LabOrder
import com.squamed.core.laboratory.domain.LabReferenceRange
import com.squamed.core.laboratory.domain.LabReport
import com.squamed.core.laboratory.domain.LabResultAmendment
import com.squamed.core.laboratory.domain.LabTestRequest
import com.squamed.core.laboratory.domain.QcRun
import com.squamed.core.laboratory.domain.SpecimenCustody
import com.squamed.core.laboratory.repository.CriticalResultAckRepository
import com.squamed.core.laboratory.repository.CriticalResultRepository
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.laboratory.repository.LabReferenceRangeRepository
import com.squamed.core.laboratory.repository.LabReportRepository
import com.squamed.core.laboratory.repository.LabResultAmendmentRepository
import com.squamed.core.laboratory.repository.LabTestRequestRepository
import com.squamed.core.laboratory.repository.QcRunRepository
import com.squamed.core.laboratory.repository.SpecimenCustodyRepository
import com.squamed.core.masterdata.repository.LabTestRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.Year
import java.util.UUID

@Service
class LabService(
  private val labOrderRepository: LabOrderRepository,
  private val labTestRequestRepository: LabTestRequestRepository,
  private val specimenCustodyRepository: SpecimenCustodyRepository,
  private val labTestRepository: LabTestRepository,
  private val criticalResultRepository: CriticalResultRepository,
  private val criticalResultAckRepository: CriticalResultAckRepository,
  private val qcRunRepository: QcRunRepository,
  private val labReferenceRangeRepository: LabReferenceRangeRepository,
  private val labReportRepository: LabReportRepository,
  private val labResultAmendmentRepository: LabResultAmendmentRepository,
  private val labReportStorage: LabReportStorage,
  private val patientRecordRepository: PatientRecordRepository,
  private val hospitalRepository: HospitalRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  /** Delta % change that blocks verification without an explicit override note (02.02 deviation closed in 02.03). */
  private val deltaBlockThresholdPct = 100.0

  @Transactional
  fun createOrder(
    patientId: UUID,
    encounterId: UUID? = null,
    dto: CreateLabOrderDto,
    hospitalId: UUID,
  ): LabOrderDetailView {
    val now = Instant.now()
    val saved =
      labOrderRepository.save(
        LabOrder(
          hospitalId = hospitalId,
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
    hospitalId: UUID? = null,
  ): List<LabOrderView> {
    var orders =
      if (patientId != null) {
        labOrderRepository.findByPatientId(hospitalId, patientId)
      } else {
        labOrderRepository.findAllForHospital(hospitalId)
      }
    if (status != null) orders = orders.filter { it.status == status }
    if (resultStatus != null) orders = orders.filter { it.resultStatus == resultStatus }
    return orders
      .sortedByDescending { it.orderedAt ?: Instant.EPOCH }
      .map { LabOrderView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getOrder(
    orderId: UUID,
    hospitalId: UUID?,
  ): LabOrderDetailView = getOrderDetail(orderId, hospitalId)

  /** Phlebotomy worklist: uncollected orders, priority-ranked (chunk-02 item 1). */
  @Transactional(readOnly = true)
  fun phlebotomyWorklist(hospitalId: UUID?): List<LabOrderView> =
    labOrderRepository
      .findAllForHospital(hospitalId)
      .filter { it.status == "ordered" }
      .sortedWith(compareBy({ priorityRank(it.priority) }, { it.orderedAt ?: Instant.EPOCH }))
      .map { LabOrderView.from(it) }

  private fun priorityRank(priority: String): Int =
    when (priority) {
      "emergency" -> 0
      "urgent" -> 1
      else -> 2
    }

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
    // Specimen label: the order number doubles as the barcode (accession id is stamped at receive).
    labOrder.barcodeId = labOrder.orderNumber
    custody(labOrder, "COLLECTED", collectedBy)
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
    // Accessioning: the lab registers the specimen into its accession register.
    if (labOrder.accessionId == null) labOrder.accessionId = "ACC-${labOrder.orderNumber}"
    custody(labOrder, "RECEIVED", null)
    return getOrderDetail(orderId)
  }

  @Transactional
  fun startProcessing(orderId: UUID): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "received") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    labOrder.status = "processing"
    labOrder.startedAt = Instant.now()
    custody(labOrder, "PROCESS_STARTED", null)
    return getOrderDetail(orderId)
  }

  @Transactional
  fun enterResults(
    orderId: UUID,
    results: List<LabTestResultInput>,
    enteredBy: UUID? = null,
    enteredVia: String = "MANUAL",
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
      // 02.04 age/sex reference range: manual entry wins, then structured range, then prior string.
      val effectiveRange = result.referenceRange ?: resolveStructuredRange(labOrder, request.testCode)?.rangeText
      if (effectiveRange != null) request.referenceRange = effectiveRange
      request.isAbnormal = computeIsAbnormal(request.resultValue, effectiveRange ?: request.referenceRange, result.isAbnormal)
      request.enteredVia = enteredVia
      applyDeltaCheck(labOrder, request, result.resultValue)
      detectCriticalResult(labOrder, request)
      request.status = "resulted"
      request.resultEnteredBy = enteredBy
      request.resultEnteredAt = now
    }
    labOrder.status = "resulted"
    custody(labOrder, "RESULTS_ENTERED", enteredBy, "via $enteredVia")
    return getOrderDetail(orderId)
  }

  /** Delta check: record % change vs the most recent prior result for the same patient + test code. */
  private fun applyDeltaCheck(
    labOrder: LabOrder,
    request: LabTestRequest,
    newValue: String?,
  ) {
    val prior =
      labTestRequestRepository
        .findPriorResults(labOrder.patientId, request.testCode, labOrder.id!!, PageRequest.of(0, 1))
        .firstOrNull() ?: return
    val priorValue = prior.resultValue?.trim()?.toDoubleOrNull() ?: return
    val new = newValue?.trim()?.toDoubleOrNull() ?: return
    if (priorValue == 0.0) return
    request.priorValue = prior.resultValue
    request.deltaPercent = Math.abs(new - priorValue) / Math.abs(priorValue) * 100.0
    if (request.deltaPercent!! >= deltaBlockThresholdPct) request.isDeltaBlocked = true
  }

  @Transactional
  fun verifyOrder(
    orderId: UUID,
    verifierId: UUID,
    overrideNote: String? = null,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.status != "resulted") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    val deltaBlocked = labTestRequestRepository.findByLabOrderId(orderId).any { it.isDeltaBlocked }
    if (deltaBlocked && overrideNote == null) throw IllegalArgumentException("DELTA_THRESHOLD_BLOCK")
    labOrder.resultStatus = "verified"
    labOrder.verifiedAt = Instant.now()
    labOrder.verifiedBy = verifierId
    // Delta-block override is audited in the custody trail.
    custody(labOrder, "VERIFIED", verifierId, overrideNote?.let { "delta override: $it" })
    return getOrderDetail(orderId)
  }

  @Transactional
  fun releaseOrder(
    orderId: UUID,
    releaseorId: UUID,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    if (labOrder.resultStatus != "verified") throw IllegalArgumentException("LAB_ORDER_WRONG_STATUS")
    // QC hard-block: a REJECTED latest QC run for any test on the order blocks release.
    labTestRequestRepository.findByLabOrderId(orderId).forEach { request ->
      val latestQc =
        qcRunRepository
          .findRecentForTest(labOrder.hospitalId, request.testCode, PageRequest.of(0, 10))
          .maxWithOrNull(compareBy({ it.runAt }, { it.createdAt }))
      if (latestQc?.status == "REJECTED") throw IllegalArgumentException("QC_BLOCK")
    }
    // No open critical result on the order may release unacknowledged.
    if (criticalResultRepository.existsByLabOrderIdAndStatus(orderId, "OPEN")) {
      throw IllegalArgumentException("CRITICAL_RESULT_UNACKED")
    }
    labOrder.resultStatus = "released"
    labOrder.releasedAt = Instant.now()
    labOrder.releasedBy = releaseorId
    custody(labOrder, "RELEASED", releaseorId)
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
    // 02.08: the released order is a performed billable service; chunk-3 charge capture consumes it.
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "billing.chargeable_performed",
        occurredAt = Instant.now(),
        source = "lab-service",
        entityType = "lab-order",
        entityId = orderId,
        hospitalId = labOrder.hospitalId,
        payload =
          mapOf(
            "orderNumber" to labOrder.orderNumber,
            "patientId" to labOrder.patientId.toString(),
            "chargeCategory" to "LAB",
            "tests" to
              labTestRequestRepository.findByLabOrderId(orderId).map {
                mapOf("testCode" to it.testCode, "testName" to it.testName, "quantity" to 1)
              },
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
    rejectedBy: UUID? = null,
    recollect: Boolean = false,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    labOrder.status = "rejected"
    labOrder.rejectedReason = reason
    labOrder.rejectedBy = rejectedBy
    labOrder.rejectedAt = Instant.now()
    custody(labOrder, "REJECTED", rejectedBy, reason)
    // Reject-and-recollect: never edit-in-place — a fresh successor order re-runs the same tests.
    if (recollect) {
      val successor =
        labOrderRepository.save(
          LabOrder(
            hospitalId = labOrder.hospitalId,
            patientId = labOrder.patientId,
            encounterId = labOrder.encounterId,
            orderNumber = generateOrderNumber(),
            priority = labOrder.priority,
            status = "ordered",
            resultStatus = "pending",
            clinicalNote = "Recollect of ${labOrder.orderNumber}: $reason",
            orderedBy = rejectedBy,
            orderedAt = Instant.now(),
            recollectOf = labOrder.id,
          ),
        )
      labTestRequestRepository.findByLabOrderId(orderId).forEach { original ->
        labTestRequestRepository.save(
          LabTestRequest(
            labOrderId = successor.id!!,
            testCode = original.testCode,
            testName = original.testName,
            status = "ordered",
          ),
        )
      }
      return getOrderDetail(successor.id!!)
    }
    return getOrderDetail(orderId)
  }

  /**
   * Critical result detection (02.03): numeric result crossing the active master-test critical
   * thresholds opens a critical result row. Missing master row or null thresholds → no row.
   */
  private fun detectCriticalResult(
    labOrder: LabOrder,
    request: LabTestRequest,
  ) {
    val value = request.resultValue?.trim()?.toDoubleOrNull() ?: return
    val master = labTestRepository.findByCode(request.testCode).firstOrNull { it.isActive } ?: return
    val criticality =
      when {
        master.criticalLow != null && value < master.criticalLow!! -> "CRITICAL_LOW"
        master.criticalHigh != null && value > master.criticalHigh!! -> "CRITICAL_HIGH"
        else -> return
      }
    criticalResultRepository.save(
      CriticalResult(
        hospitalId = labOrder.hospitalId ?: throw IllegalStateException("LAB_ORDER_HOSPITAL_REQUIRED"),
        labOrderId = labOrder.id!!,
        labTestRequestId = request.id!!,
        patientId = labOrder.patientId,
        testCode = request.testCode,
        testName = request.testName,
        resultValue = request.resultValue,
        criticality = criticality,
        slaMinutes = master.criticalSlaMinutes,
      ),
    )
  }

  /** Escalation read-evaluated from SLA: >=2x sla → level 2 (CMO), >=sla → level 1 (dept head), else 0. */
  @Transactional(readOnly = true)
  fun listCriticalResults(
    hospitalId: UUID?,
    status: String? = null,
  ): List<CriticalResultView> {
    var results = criticalResultRepository.findAllForHospital(hospitalId)
    if (status != null) results = results.filter { it.status == status }
    return results.map { CriticalResultView.from(it) }
  }

  @Transactional(readOnly = true)
  fun listCriticalResultAcks(
    criticalResultId: UUID,
    hospitalId: UUID?,
  ): List<CriticalResultAckView> {
    val critical = findCriticalResult(criticalResultId, hospitalId)
    return criticalResultAckRepository.findByCriticalResultIdOrderByOccurredAtAsc(critical.id!!).map { CriticalResultAckView.from(it) }
  }

  /** Ack an open critical result: appends the ack trail row and flips status to ACKNOWLEDGED. */
  @Transactional
  fun acknowledgeCriticalResult(
    criticalResultId: UUID,
    hospitalId: UUID?,
    actorId: UUID?,
    note: String? = null,
  ): CriticalResultView {
    val critical = findCriticalResult(criticalResultId, hospitalId)
    if (critical.status != "OPEN") throw IllegalArgumentException("CRITICAL_RESULT_ALREADY_ACKNOWLEDGED")
    critical.status = "ACKNOWLEDGED"
    critical.ackedBy = actorId
    critical.ackedAt = Instant.now()
    criticalResultAckRepository.save(
      CriticalResultAck(
        criticalResultId = critical.id!!,
        actorId = actorId,
        note = note?.take(400),
        occurredAt = Instant.now(),
      ),
    )
    return CriticalResultView.from(critical)
  }

  private fun findCriticalResult(
    criticalResultId: UUID,
    hospitalId: UUID?,
  ): CriticalResult {
    val critical =
      criticalResultRepository.findById(criticalResultId).orElseThrow { IllegalArgumentException("CRITICAL_RESULT_NOT_FOUND") }
    requireSameHospital(hospitalId, critical.hospitalId)
    return critical
  }

  /**
   * Westgard QC run (02.03): rules evaluated against the last 10 runs for the same
   * hospital + test code. 1_3S/2_2S/R_4S/4_1S/10X reject, 1_2S warns.
   */
  @Transactional
  fun createQcRun(
    dto: CreateQcRunDto,
    hospitalId: UUID?,
    runBy: UUID?,
  ): QcRunView {
    if (dto.targetSd <= 0.0) throw IllegalArgumentException("QC_TARGET_SD_INVALID")
    val z = (dto.observedValue - dto.targetMean) / dto.targetSd
    val historyZ =
      qcRunRepository
        .findRecentForTest(hospitalId, dto.testCode, PageRequest.of(0, 10))
        .sortedBy { it.runAt }
        .filter { it.targetSd > 0.0 }
        .map { (it.observedValue - it.targetMean) / it.targetSd } + z
    val rules = westgardRules(z, historyZ)
    val status =
      when {
        rules.any { it != "1_2S" } -> "REJECTED"
        rules.isNotEmpty() -> "WARNING"
        else -> "ACCEPTED"
      }
    val run =
      qcRunRepository.save(
        QcRun(
          hospitalId = hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED"),
          instrument = dto.instrument,
          testCode = dto.testCode,
          level = dto.level,
          observedValue = dto.observedValue,
          targetMean = dto.targetMean,
          targetSd = dto.targetSd,
          westgardRules = rules.joinToString(","),
          status = status,
          note = dto.note?.take(400),
          runBy = runBy,
        ),
      )
    return QcRunView.from(run)
  }

  @Transactional(readOnly = true)
  fun listQcRuns(hospitalId: UUID?): List<QcRunView> = qcRunRepository.findAllForHospital(hospitalId).map { QcRunView.from(it) }

  /** Westgard multi-rule on the z history (chronological, current run last). */
  private fun westgardRules(
    z: Double,
    historyZ: List<Double>,
  ): List<String> {
    val rules = mutableListOf<String>()
    val abs = kotlin.math.abs(z)
    if (abs >= 3.0) {
      rules.add("1_3S")
    } else if (abs >= 2.0) {
      rules.add("1_2S")
    }
    val previous = historyZ.dropLast(1).lastOrNull()
    if (previous != null && abs >= 2.0 && kotlin.math.abs(previous) >= 2.0) {
      if ((z > 0) == (previous > 0)) rules.add("2_2S") else rules.add("R_4S")
    }
    val last4 = historyZ.takeLast(4)
    if (last4.size == 4 && last4.all { kotlin.math.abs(it) >= 1.0 && (it > 0) == (z > 0) }) rules.add("4_1S")
    val last10 = historyZ.takeLast(10)
    if (last10.size == 10 && last10.all { (it > 0) == (z > 0) }) rules.add("10X")
    return rules
  }

  private fun custody(
    labOrder: LabOrder,
    event: String,
    actorId: UUID?,
    note: String? = null,
  ) {
    specimenCustodyRepository.save(
      SpecimenCustody(
        labOrderId = labOrder.id!!,
        event = event,
        actorId = actorId,
        note = note?.take(400),
        occurredAt = Instant.now(),
      ),
    )
  }

  private fun getOrderDetail(
    orderId: UUID,
    hospitalId: UUID? = null,
  ): LabOrderDetailView {
    val labOrder = findOrder(orderId)
    // 00.04 tenant sweep (U8): null hospitalId means internal/system caller (mutations, createOrder) — unguarded.
    requireSameHospital(hospitalId, labOrder.hospitalId)
    val tests = labTestRequestRepository.findByLabOrderId(orderId).map { LabTestRequestView.from(it) }
    val custody = specimenCustodyRepository.findByLabOrderIdOrderByOccurredAtAsc(orderId).map { SpecimenCustodyView.from(it) }
    return LabOrderDetailView(order = LabOrderView.from(labOrder), tests = tests, custody = custody)
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

  /**
   * Structured age/sex reference range (02.04): active ranges for the hospital + test code,
   * filtered to the patient's age window and sex (exact sex preferred, then ANY; most specific
   * age span wins). No patient, unmapped gender, or no matching row → null (string fallback).
   */
  private fun resolveStructuredRange(
    labOrder: LabOrder,
    testCode: String,
  ): LabReferenceRange? {
    val hospitalId = labOrder.hospitalId ?: return null
    val patient = patientRecordRepository.findById(labOrder.patientId).orElse(null) ?: return null
    val age = Period.between(patient.dateOfBirth, LocalDate.now()).years
    val sex =
      when (patient.gender.lowercase()) {
        "male" -> "M"
        "female" -> "F"
        else -> return null
      }
    return labReferenceRangeRepository
      .findByHospitalIdAndTestCodeAndIsActiveTrue(hospitalId, testCode)
      .filter { age >= it.ageMinYears && age <= it.ageMaxYears && (it.sex == sex || it.sex == "ANY") }
      .minWithOrNull(compareBy({ it.sex != sex }, { it.ageMaxYears - it.ageMinYears }))
  }

  /**
   * Publish a branded PDF report version (02.04): renders the current released state, stores it
   * in object storage and records the version. Amendments afterwards require a re-publish (v+1).
   */
  @Transactional
  fun publishReport(
    orderId: UUID,
    publishedBy: UUID?,
  ): LabReportView {
    val labOrder = findOrder(orderId)
    if (labOrder.resultStatus != "released") throw IllegalArgumentException("LAB_ORDER_NOT_RELEASED")
    val hospitalId = labOrder.hospitalId ?: throw IllegalStateException("LAB_ORDER_HOSPITAL_REQUIRED")
    val nextVersion = (labReportRepository.findTopByLabOrderIdOrderByReportVersionDesc(orderId)?.reportVersion ?: 0) + 1
    val patient = patientRecordRepository.findById(labOrder.patientId).orElse(null)
    val hospitalName = hospitalRepository.findById(hospitalId).orElse(null)?.name ?: ""
    val tests = labTestRequestRepository.findByLabOrderId(orderId)
    val amendments = labResultAmendmentRepository.findByLabOrderIdOrderByAmendedAtAsc(orderId)
    val patientLines =
      listOf(
        "Patient: ${patient?.let { "${it.firstName} ${it.lastName}" } ?: labOrder.patientId}",
        "MRN: ${patient?.mrn ?: "-"}   DOB: ${patient?.dateOfBirth ?: "-"}   Sex: ${patient?.gender ?: "-"}",
        "Ordered: ${labOrder.orderedAt}   Released: ${labOrder.releasedAt}",
      )
    val rows =
      tests.map {
        LabReportRow(
          test = it.testName,
          result = it.resultValue ?: "-",
          unit = it.unit ?: "",
          reference = it.referenceRange ?: "",
          flag = if (it.isAbnormal) "ABNORMAL" else "",
        )
      }
    val amendmentLines = amendments.map { "AMENDED ${it.amendedAt}: ${it.oldValue ?: "-"} -> ${it.newValue} (${it.reason})" }
    val pdf =
      LabReportPdf.render(
        hospitalName = hospitalName,
        orderNumber = labOrder.orderNumber,
        reportVersion = nextVersion,
        patientLines = patientLines,
        rows = rows,
        amendmentLines = amendmentLines,
        footer = "Report v$nextVersion - ${labOrder.orderNumber} - Squamed LIS",
      )
    val report =
      labReportRepository.save(
        LabReport(
          hospitalId = hospitalId,
          labOrderId = orderId,
          reportVersion = nextVersion,
          storageKey = labReportStorage.upload(hospitalId, orderId, nextVersion, pdf),
          publishedBy = publishedBy,
        ),
      )
    custody(labOrder, "REPORT_PUBLISHED", publishedBy, "v$nextVersion")
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "lab.report.published",
        occurredAt = Instant.now(),
        source = "lab-service",
        entityType = "lab-report",
        entityId = report.id,
        hospitalId = hospitalId,
        payload =
          mapOf(
            "orderNumber" to labOrder.orderNumber,
            "reportVersion" to nextVersion,
          ),
        // Notification fan-out (counter/app delivery) stays with the chunk-00 notifier (00.12).
        recipientIds = listOfNotNull(labOrder.orderedBy),
      ),
    )
    return LabReportView.from(report)
  }

  /** Latest report bytes for download (02.04). */
  @Transactional(readOnly = true)
  fun getReportBytes(
    orderId: UUID,
    hospitalId: UUID?,
  ): LabReportDownload {
    val labOrder = findOrder(orderId)
    requireSameHospital(hospitalId, labOrder.hospitalId)
    val report =
      labReportRepository.findTopByLabOrderIdOrderByReportVersionDesc(orderId)
        ?: throw IllegalArgumentException("LAB_REPORT_NOT_FOUND")
    return LabReportDownload(
      orderNumber = labOrder.orderNumber,
      reportVersion = report.reportVersion,
      bytes = labReportStorage.download(report.storageKey).first,
    )
  }

  /** Report version history for an order (02.04). */
  @Transactional(readOnly = true)
  fun listReports(
    orderId: UUID,
    hospitalId: UUID?,
  ): List<LabReportView> {
    val labOrder = findOrder(orderId)
    requireSameHospital(hospitalId, labOrder.hospitalId)
    return labReportRepository.findAllByLabOrderIdOrderByReportVersionDesc(orderId).map { LabReportView.from(it) }
  }

  /** Stamp one delivery (counter pickup or app) on a report — a report delivers once (02.04). */
  @Transactional
  fun deliverReport(
    reportId: UUID,
    dto: DeliverReportDto,
    deliveredBy: UUID?,
    hospitalId: UUID?,
  ): LabReportView {
    if (dto.via !in setOf("COUNTER", "APP")) throw IllegalArgumentException("DELIVERY_VIA_INVALID")
    val report = labReportRepository.findById(reportId).orElseThrow { IllegalArgumentException("LAB_REPORT_NOT_FOUND") }
    requireSameHospital(hospitalId, report.hospitalId)
    if (report.deliveredVia != null) throw IllegalArgumentException("REPORT_ALREADY_DELIVERED")
    report.deliveredVia = dto.via
    report.deliveredTo = dto.deliveredTo
    report.deliveredAt = Instant.now()
    val labOrder = findOrder(report.labOrderId)
    custody(labOrder, "REPORT_DELIVERED", deliveredBy, "via ${dto.via}")
    return LabReportView.from(report)
  }

  /** Amend a released result (02.04): the old value is preserved in a versioned amendment row. */
  @Transactional
  fun amendResult(
    orderId: UUID,
    testRequestId: UUID,
    dto: AmendResultDto,
    amendedBy: UUID?,
  ): LabOrderDetailView {
    if (dto.reason.isBlank()) throw IllegalArgumentException("AMENDMENT_REASON_REQUIRED")
    val labOrder = findOrder(orderId)
    if (labOrder.resultStatus != "released") throw IllegalArgumentException("LAB_ORDER_NOT_RELEASED")
    val request =
      labTestRequestRepository.findById(testRequestId).orElseThrow { IllegalArgumentException("LAB_TEST_REQUEST_NOT_FOUND") }
    if (request.labOrderId != orderId) throw IllegalArgumentException("LAB_TEST_REQUEST_NOT_FOUND")
    labResultAmendmentRepository.save(
      LabResultAmendment(
        hospitalId = labOrder.hospitalId ?: throw IllegalStateException("LAB_ORDER_HOSPITAL_REQUIRED"),
        labOrderId = orderId,
        labTestRequestId = testRequestId,
        oldValue = request.resultValue,
        newValue = dto.newValue,
        reason = dto.reason.take(400),
        amendedBy = amendedBy,
      ),
    )
    request.resultValue = dto.newValue
    request.isAbnormal = computeIsAbnormal(dto.newValue, request.referenceRange, null)
    // The amended value may open (or close) a critical result — re-run detection.
    detectCriticalResult(labOrder, request)
    custody(labOrder, "AMENDED", amendedBy, dto.reason.take(400))
    return getOrderDetail(orderId)
  }

  @Transactional(readOnly = true)
  fun listAmendments(
    orderId: UUID,
    hospitalId: UUID?,
  ): List<LabResultAmendmentView> {
    val labOrder = findOrder(orderId)
    requireSameHospital(hospitalId, labOrder.hospitalId)
    return labResultAmendmentRepository.findByLabOrderIdOrderByAmendedAtAsc(orderId).map { LabResultAmendmentView.from(it) }
  }

  @Transactional
  fun createReferenceRange(
    dto: CreateReferenceRangeDto,
    hospitalId: UUID,
  ): ReferenceRangeView {
    if (dto.sex !in setOf("M", "F", "ANY")) throw IllegalArgumentException("REFERENCE_RANGE_SEX_INVALID")
    if (dto.ageMinYears > dto.ageMaxYears) throw IllegalArgumentException("REFERENCE_RANGE_AGE_INVALID")
    if (dto.rangeText.isBlank()) throw IllegalArgumentException("REFERENCE_RANGE_TEXT_REQUIRED")
    val saved =
      labReferenceRangeRepository.save(
        LabReferenceRange(
          hospitalId = hospitalId,
          testCode = dto.testCode,
          sex = dto.sex,
          ageMinYears = dto.ageMinYears,
          ageMaxYears = dto.ageMaxYears,
          rangeText = dto.rangeText,
        ),
      )
    return ReferenceRangeView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listReferenceRanges(
    testCode: String?,
    hospitalId: UUID?,
  ): List<ReferenceRangeView> {
    var ranges = labReferenceRangeRepository.findAllForHospital(hospitalId)
    if (testCode != null) ranges = ranges.filter { it.testCode == testCode }
    return ranges.sortedBy { it.testCode }.map { ReferenceRangeView.from(it) }
  }

  /** TAT analytics (02.04): turn-around minutes (orderedAt -> releasedAt) per test code over a window. */
  @Transactional(readOnly = true)
  fun tatAnalytics(
    hospitalId: UUID?,
    days: Int = 30,
  ): List<TatRowView> {
    val since = Instant.now().minus(Duration.ofDays(days.toLong()))
    val released =
      labOrderRepository
        .findAllForHospital(hospitalId)
        .filter { it.resultStatus == "released" && it.releasedAt?.isAfter(since) == true }
    val requestsByOrder = labTestRequestRepository.findByLabOrderIdIn(released.map { it.id!! }).groupBy { it.labOrderId }

    fun tat(labOrder: LabOrder): Long = Duration.between(labOrder.orderedAt ?: Instant.EPOCH, labOrder.releasedAt ?: Instant.now()).toMinutes()

    fun row(
      testCode: String,
      minutes: List<Long>,
    ): TatRowView =
      TatRowView(
        testCode = testCode,
        count = minutes.size,
        avgMinutes = if (minutes.isEmpty()) 0.0 else minutes.sum().toDouble() / minutes.size,
        minMinutes = minutes.minOrNull() ?: 0,
        maxMinutes = minutes.maxOrNull() ?: 0,
      )
    val rows = mutableListOf(row("ALL", released.map { tat(it) }))
    val byTest = mutableMapOf<String, MutableList<Long>>()
    released.forEach { order ->
      requestsByOrder[order.id!!].orEmpty().forEach { request ->
        byTest.getOrPut(request.testCode) { mutableListOf() }.add(tat(order))
      }
    }
    byTest.toSortedMap().forEach { (testCode, minutes) -> rows.add(row(testCode, minutes)) }
    return rows
  }
}
