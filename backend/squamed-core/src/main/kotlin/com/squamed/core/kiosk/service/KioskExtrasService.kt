package com.squamed.core.kiosk.service

import com.squamed.core.billing.api.PaymentCreateDto
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.billing.service.BillingService
import com.squamed.core.kiosk.domain.KioskCampus
import com.squamed.core.kiosk.domain.KioskMapEdge
import com.squamed.core.kiosk.domain.KioskMapNode
import com.squamed.core.kiosk.domain.KioskPaymentReceipt
import com.squamed.core.kiosk.domain.KioskReportCollection
import com.squamed.core.kiosk.repository.KioskCampusRepository
import com.squamed.core.kiosk.repository.KioskMapEdgeRepository
import com.squamed.core.kiosk.repository.KioskMapNodeRepository
import com.squamed.core.kiosk.repository.KioskPaymentReceiptRepository
import com.squamed.core.kiosk.repository.KioskReportCollectionRepository
import com.squamed.core.laboratory.repository.LabReportRepository
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.radiology.repository.RadiologyReportRepository
import com.squamed.core.receptionqueue.service.KioskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.PriorityQueue
import java.util.UUID

data class KioskInvoiceView(
  val invoiceId: UUID,
  val invoiceNumber: String,
  val totalQar: BigDecimal,
  val dueQar: BigDecimal,
  val issuedAt: String,
)

data class KioskPaymentLookupView(
  val patientId: UUID,
  val patientMrn: String?,
  val patientName: String,
  val invoices: List<KioskInvoiceView>,
)

data class KioskReportView(
  val reportType: String,
  val reportId: UUID,
  val title: String,
  val publishedAt: String?,
  val collected: Boolean,
)

data class KioskRouteView(
  val campusId: UUID,
  val from: String,
  val to: String,
  val path: List<String>,
  val nodeNames: List<String>,
  val distanceM: BigDecimal,
  val message: String,
)

// 07.06 full kiosk: payment + report collection + indoor routing. Wayfinding vendor map import is
// external (E12); this service routes over an admin-defined graph.
@Service
class KioskExtrasService(
  private val kioskService: KioskService,
  private val invoiceRepository: InvoiceRepository,
  private val billingService: BillingService,
  private val labReportRepository: LabReportRepository,
  private val radiologyReportRepository: RadiologyReportRepository,
  private val receiptRepository: KioskPaymentReceiptRepository,
  private val collectionRepository: KioskReportCollectionRepository,
  private val campusRepository: KioskCampusRepository,
  private val nodeRepository: KioskMapNodeRepository,
  private val edgeRepository: KioskMapEdgeRepository,
) {
  companion object {
    private val PAY_METHODS = setOf("CASH", "CARD")
    private val REPORT_TYPES = setOf("LAB", "RAD")
  }

  @Transactional(readOnly = true)
  fun paymentLookup(
    hospitalId: UUID,
    identifierType: String,
    identifier: String,
  ): KioskPaymentLookupView {
    val patient = kioskService.lookupPatient(identifierType, identifier, hospitalId)
    val patientId = checkNotNull(patient.id)
    val invoices =
      invoiceRepository
        .findAllForHospital(hospitalId)
        .filter { it.patientId == patientId && it.status != "cancelled" && it.amountDueQar.signum() > 0 }
        .sortedByDescending { it.issuedAt }
        .map {
          KioskInvoiceView(
            invoiceId = checkNotNull(it.id),
            invoiceNumber = it.invoiceNumber,
            totalQar = it.totalQar.setScale(2, RoundingMode.HALF_UP),
            dueQar = it.amountDueQar.setScale(2, RoundingMode.HALF_UP),
            issuedAt = it.issuedAt.toString(),
          )
        }
    return KioskPaymentLookupView(patientId, patient.mrn, fullName(patient), invoices)
  }

  @Transactional
  fun pay(
    hospitalId: UUID,
    identifierType: String,
    identifier: String,
    invoiceId: UUID,
    amountQar: BigDecimal,
    method: String,
    locale: String,
    actorId: UUID?,
  ): Map<String, Any?> {
    val patient = kioskService.lookupPatient(identifierType, identifier, hospitalId)
    val patientId = checkNotNull(patient.id)
    val invoice = invoiceRepository.findByIdAndHospitalId(invoiceId, hospitalId) ?: throw IllegalArgumentException("KIOSK_INVOICE_NOT_FOUND")
    // A kiosk must never reveal another patient's invoice: same error as a missing invoice.
    if (invoice.patientId != patientId || invoice.status == "cancelled") throw IllegalArgumentException("KIOSK_INVOICE_NOT_FOUND")
    val due = invoice.amountDueQar
    if (amountQar.signum() <= 0) throw IllegalArgumentException("KIOSK_AMOUNT_INVALID")
    if (amountQar > due) throw IllegalArgumentException("KIOSK_AMOUNT_EXCEEDS_DUE")
    val payMethod = method.trim().uppercase()
    if (payMethod !in PAY_METHODS) throw IllegalArgumentException("KIOSK_PAYMENT_METHOD_INVALID")
    val payment = billingService.recordPayment(invoiceId, PaymentCreateDto(payMethod, amountQar), actorId, hospitalId)
    val receiptNumber = nextNumber("KP", receiptRepository.findTopByOrderByReceiptNumberDesc()?.receiptNumber)
    receiptRepository.save(
      KioskPaymentReceipt(
        hospitalId = hospitalId,
        patientId = patientId,
        invoiceId = invoiceId,
        paymentId = checkNotNull(payment.id),
        amountQar = amountQar.setScale(2, RoundingMode.HALF_UP),
        method = payMethod,
        receiptNumber = receiptNumber,
      ).also { it.createdBy = actorId },
    )
    return mapOf(
      "receiptNumber" to receiptNumber,
      "invoiceId" to invoiceId,
      "amountQar" to amountQar.setScale(2, RoundingMode.HALF_UP),
      "method" to payMethod,
      "remainingDueQar" to (due - amountQar).setScale(2, RoundingMode.HALF_UP),
      "message" to text(locale, "payment"),
    )
  }

  @Transactional(readOnly = true)
  fun reportLookup(
    hospitalId: UUID,
    identifierType: String,
    identifier: String,
  ): Map<String, Any?> {
    val patient = kioskService.lookupPatient(identifierType, identifier, hospitalId)
    val patientId = checkNotNull(patient.id)
    val lab =
      labReportRepository.findForPatient(hospitalId, patientId).map {
        val reportId = checkNotNull(it.id)
        KioskReportView(
          reportType = "LAB",
          reportId = reportId,
          title = "Laboratory report v${it.reportVersion}",
          publishedAt = it.publishedAt.toString(),
          collected = collectionRepository.findByHospitalIdAndReportTypeAndReportId(hospitalId, "LAB", reportId) != null,
        )
      }
    val rad =
      radiologyReportRepository.findSignedForPatient(hospitalId, patientId).map {
        val reportId = checkNotNull(it.id)
        KioskReportView(
          reportType = "RAD",
          reportId = reportId,
          title = "Imaging report v${it.reportVersion}",
          publishedAt = it.signedAt?.toString(),
          collected = collectionRepository.findByHospitalIdAndReportTypeAndReportId(hospitalId, "RAD", reportId) != null,
        )
      }
    return mapOf(
      "patientId" to patientId,
      "patientMrn" to patient.mrn,
      "patientName" to fullName(patient),
      "reports" to (lab + rad).sortedByDescending { it.publishedAt ?: "" },
    )
  }

  @Transactional
  fun collectReport(
    hospitalId: UUID,
    identifierType: String,
    identifier: String,
    reportType: String,
    reportId: UUID,
    locale: String,
    actorId: UUID?,
  ): Map<String, Any?> {
    val patient = kioskService.lookupPatient(identifierType, identifier, hospitalId)
    val patientId = checkNotNull(patient.id)
    val type = reportType.trim().uppercase()
    if (type !in REPORT_TYPES) throw IllegalArgumentException("KIOSK_REPORT_TYPE_INVALID")
    val owned =
      when (type) {
        "LAB" -> labReportRepository.findForPatient(hospitalId, patientId).any { it.id == reportId }
        else -> radiologyReportRepository.findSignedForPatient(hospitalId, patientId).any { it.id == reportId }
      }
    if (!owned) throw IllegalArgumentException("KIOSK_REPORT_NOT_FOUND")
    if (collectionRepository.findByHospitalIdAndReportTypeAndReportId(hospitalId, type, reportId) != null) {
      throw IllegalStateException("KIOSK_REPORT_ALREADY_COLLECTED")
    }
    val receiptNumber = nextNumber("KR", collectionRepository.findTopByOrderByReceiptNumberDesc()?.receiptNumber)
    collectionRepository.save(
      KioskReportCollection(
        hospitalId = hospitalId,
        patientId = patientId,
        reportType = type,
        reportId = reportId,
        receiptNumber = receiptNumber,
      ).also { it.createdBy = actorId },
    )
    return mapOf(
      "receiptNumber" to receiptNumber,
      "reportType" to type,
      "reportId" to reportId,
      "message" to text(locale, "report"),
    )
  }

  // -- wayfinding -----------------------------------------------------------

  @Transactional
  fun createCampus(
    hospitalId: UUID,
    code: String,
    name: String,
    actorId: UUID?,
  ): KioskCampus {
    if (code.isBlank() || name.isBlank()) throw IllegalArgumentException("KIOSK_CAMPUS_FIELDS_REQUIRED")
    if (campusRepository.findByHospitalIdAndCode(hospitalId, code.trim().uppercase()) != null) throw IllegalArgumentException("KIOSK_CAMPUS_EXISTS")
    return campusRepository.save(
      KioskCampus(hospitalId = hospitalId, code = code.trim().uppercase(), name = name.trim()).also { it.createdBy = actorId },
    )
  }

  @Transactional(readOnly = true)
  fun campuses(hospitalId: UUID): List<KioskCampus> = campusRepository.findByHospitalIdOrderByCodeAsc(hospitalId)

  @Transactional
  fun addNode(
    hospitalId: UUID,
    campusId: UUID,
    code: String,
    name: String,
    nodeType: String,
    floor: String?,
    x: BigDecimal?,
    y: BigDecimal?,
    actorId: UUID?,
  ): KioskMapNode {
    val campus = campusRepository.findByIdAndHospitalId(campusId, hospitalId) ?: throw IllegalArgumentException("KIOSK_CAMPUS_NOT_FOUND")
    if (code.isBlank() || name.isBlank()) throw IllegalArgumentException("KIOSK_NODE_FIELDS_REQUIRED")
    if (nodeRepository.findByCampusIdAndCode(campusId, code.trim().uppercase()) != null) throw IllegalArgumentException("KIOSK_NODE_EXISTS")
    return nodeRepository.save(
      KioskMapNode(
        hospitalId = hospitalId,
        campusId = checkNotNull(campus.id),
        code = code.trim().uppercase(),
        name = name.trim(),
        nodeType = nodeType.trim().uppercase().ifBlank { "ROOM" },
        floor = floor?.trim(),
        x = x,
        y = y,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun addEdge(
    hospitalId: UUID,
    campusId: UUID,
    fromCode: String,
    toCode: String,
    distanceM: BigDecimal,
    accessible: Boolean,
    bidirectional: Boolean,
    actorId: UUID?,
  ): KioskMapEdge {
    campusRepository.findByIdAndHospitalId(campusId, hospitalId) ?: throw IllegalArgumentException("KIOSK_CAMPUS_NOT_FOUND")
    if (distanceM.signum() <= 0) throw IllegalArgumentException("KIOSK_EDGE_DISTANCE_INVALID")
    val from = nodeRepository.findByCampusIdAndCode(campusId, fromCode.trim().uppercase()) ?: throw IllegalArgumentException("KIOSK_NODE_NOT_FOUND")
    val to = nodeRepository.findByCampusIdAndCode(campusId, toCode.trim().uppercase()) ?: throw IllegalArgumentException("KIOSK_NODE_NOT_FOUND")
    if (from.id == to.id) throw IllegalArgumentException("KIOSK_EDGE_SELF_LOOP")
    return edgeRepository.save(
      KioskMapEdge(
        hospitalId = hospitalId,
        campusId = campusId,
        fromNodeId = checkNotNull(from.id),
        toNodeId = checkNotNull(to.id),
        distanceM = distanceM,
        accessible = accessible,
        bidirectional = bidirectional,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional(readOnly = true)
  fun route(
    hospitalId: UUID,
    campusId: UUID,
    fromCode: String,
    toCode: String,
    accessibleOnly: Boolean,
    locale: String,
  ): KioskRouteView {
    campusRepository.findByIdAndHospitalId(campusId, hospitalId) ?: throw IllegalArgumentException("KIOSK_CAMPUS_NOT_FOUND")
    val nodes = nodeRepository.findByCampusIdOrderByCodeAsc(campusId).associateBy { it.code }
    val start = nodes[fromCode.trim().uppercase()] ?: throw IllegalArgumentException("KIOSK_NODE_NOT_FOUND")
    val target = nodes[toCode.trim().uppercase()] ?: throw IllegalArgumentException("KIOSK_NODE_NOT_FOUND")
    val startId = checkNotNull(start.id)
    val targetId = checkNotNull(target.id)
    val adjacency = mutableMapOf<UUID, MutableList<Pair<UUID, BigDecimal>>>()
    edgeRepository.findByCampusIdOrderByIdAsc(campusId).forEach { edge ->
      if (accessibleOnly && !edge.accessible) return@forEach
      adjacency.getOrPut(edge.fromNodeId) { mutableListOf() } += edge.toNodeId to edge.distanceM
      if (edge.bidirectional) adjacency.getOrPut(edge.toNodeId) { mutableListOf() } += edge.fromNodeId to edge.distanceM
    }
    val distance = mutableMapOf<UUID, BigDecimal>()
    val previous = mutableMapOf<UUID, UUID>()
    val queue = PriorityQueue<Pair<UUID, BigDecimal>>(compareBy { it.second })
    distance[startId] = BigDecimal.ZERO
    queue += startId to BigDecimal.ZERO
    while (queue.isNotEmpty()) {
      val (node, cost) = queue.poll()
      if (node == targetId) break
      if (cost > (distance[node] ?: BigDecimal.valueOf(Long.MAX_VALUE))) continue
      adjacency[node].orEmpty().forEach { (next, weight) ->
        val candidate = cost + weight
        if (candidate < (distance[next] ?: BigDecimal.valueOf(Long.MAX_VALUE))) {
          distance[next] = candidate
          previous[next] = node
          queue += next to candidate
        }
      }
    }
    val total = distance[targetId] ?: throw IllegalArgumentException("KIOSK_ROUTE_NOT_FOUND")
    val ids = mutableListOf<UUID>()
    var cursor: UUID? = targetId
    while (cursor != null) {
      ids += cursor
      cursor = previous[cursor]
    }
    val pathIds = ids.reversed()
    if (pathIds.first() != startId) throw IllegalArgumentException("KIOSK_ROUTE_NOT_FOUND")
    val byId = nodes.values.associateBy { it.id }
    val path = pathIds.mapNotNull { byId[it]?.code }
    val names = pathIds.mapNotNull { byId[it]?.name }
    return KioskRouteView(
      campusId = campusId,
      from = start.code,
      to = target.code,
      path = path,
      nodeNames = names,
      distanceM = total.setScale(2, RoundingMode.HALF_UP),
      message = text(locale, "direction", target.name),
    )
  }

  private fun nextNumber(
    prefix: String,
    latest: String?,
  ): String {
    // shortcut: max+1 receipt number, upgrade to a DB sequence if kiosk volume needs concurrent receipts.
    val next = latest?.removePrefix("$prefix-")?.toIntOrNull()?.plus(1) ?: 1
    return String.format("%s-%06d", prefix, next)
  }

  private fun text(
    locale: String,
    key: String,
    arg: String? = null,
  ): String {
    val hi = locale.lowercase().startsWith("hi")
    return when (key) {
      "payment" -> if (hi) "भुगतान प्राप्त हुआ। कृपया अपनी रसीद लें।" else "Payment received. Please collect your receipt."
      "report" -> if (hi) "आपकी रिपोर्ट तैयार है। कृपया प्रिंट कॉपी लें।" else "Your report is ready. Please collect the printout."
      else -> if (hi) "कृपया $arg तक जाएँ।" else "Please proceed to $arg."
    }
  }

  private fun fullName(patient: PatientRecord): String =
    listOfNotNull(patient.firstName, patient.middleName, patient.lastName)
      .filter { it.isNotBlank() }
      .joinToString(" ")
}
