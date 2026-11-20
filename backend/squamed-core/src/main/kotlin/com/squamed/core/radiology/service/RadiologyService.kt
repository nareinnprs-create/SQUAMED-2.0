package com.squamed.core.radiology.service

import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.identity.security.JwtService
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.radiology.api.CriticalAckDto
import com.squamed.core.radiology.api.CriticalFindingRequestDto
import com.squamed.core.radiology.api.CriticalFindingView
import com.squamed.core.radiology.api.ImagingSummaryView
import com.squamed.core.radiology.api.MwlRowView
import com.squamed.core.radiology.api.RadOrderRequestDto
import com.squamed.core.radiology.api.RadOrderView
import com.squamed.core.radiology.api.RadiologyReportDto
import com.squamed.core.radiology.api.RadiologyReportView
import com.squamed.core.radiology.api.ReportAmendmentDto
import com.squamed.core.radiology.api.StudyCompleteDto
import com.squamed.core.radiology.api.StudyViewerTokenView
import com.squamed.core.radiology.domain.RadOrder
import com.squamed.core.radiology.domain.RadiologyCriticalFinding
import com.squamed.core.radiology.domain.RadiologyReport
import com.squamed.core.radiology.domain.RadiologyStudy
import com.squamed.core.radiology.repository.RadOrderRepository
import com.squamed.core.radiology.repository.RadiologyCriticalFindingRepository
import com.squamed.core.radiology.repository.RadiologyReportRepository
import com.squamed.core.radiology.repository.RadiologyStudyRepository
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// 02.05 RIS-lite: orders -> modality worklist feed -> study completion (Orthanc refs) ->
// radiologist worklist -> structured report (ICD/SNOMED) -> sign/amend -> critical findings.
@Service
class RadiologyService(
  private val radOrderRepository: RadOrderRepository,
  private val studyRepository: RadiologyStudyRepository,
  private val reportRepository: RadiologyReportRepository,
  private val criticalFindingRepository: RadiologyCriticalFindingRepository,
  private val patientRecordRepository: PatientRecordRepository,
  private val tenantResolver: TenantResolver,
  private val eventPublisher: DomainEventPublisher,
  private val jwtService: JwtService,
  private val entityManager: EntityManager,
  @Value("\${squamed.radiology.ohif-url:}") private val ohifUrl: String,
) {
  private val accessionDate = DateTimeFormatter.ofPattern("yyyyMMdd")

  // Concurrency-safe accession (DB sequence), e.g. RAD-20261120-0007.
  private fun nextAccession(): String {
    val seq = (entityManager.createNativeQuery("select nextval('radiology.accession_seq')").singleResult as Number).toLong()
    return "RAD-${LocalDate.now().format(accessionDate)}-%04d".format(seq % 10000)
  }

  @Transactional
  fun createOrder(
    dto: RadOrderRequestDto,
    principal: SquamedPrincipal,
  ): RadOrderView {
    val modality = dto.modality.trim().uppercase()
    if (modality.isEmpty()) throw IllegalArgumentException("RADIOLOGY_MODALITY_REQUIRED")
    if (dto.bodyPart.isBlank()) throw IllegalArgumentException("RADIOLOGY_BODY_PART_REQUIRED")
    val saved =
      radOrderRepository.save(
        RadOrder(
          hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
          patientId = dto.patientId,
          encounterId = dto.encounterId,
          accession = nextAccession(),
          modality = modality,
          bodyPart = dto.bodyPart.trim(),
          priority = dto.priority ?: "routine",
          clinicalNote = dto.clinicalNote,
          orderedBy = principal.userId,
        ),
      )
    return RadOrderView.from(saved)
  }

  // Modality worklist feed: Orthanc's MWL channel consumes scheduled/ordered rows for a modality.
  @Transactional(readOnly = true)
  fun mwl(
    hospitalId: UUID?,
    modality: String,
  ): List<MwlRowView> =
    radOrderRepository
      .findMwlRows(hospitalId, modality.trim().uppercase())
      .filter { it.status == "ordered" || it.status == "scheduled" || it.status == "in_progress" }
      .map { order ->
        val patient = patientRecordRepository.findById(order.patientId).orElse(null)
        MwlRowView(
          accession = order.accession,
          modality = order.modality,
          bodyPart = order.bodyPart,
          priority = order.priority,
          patientId = order.patientId,
          patientName = listOfNotNull(patient?.firstName, patient?.lastName).joinToString(" ").ifBlank { "Unknown" },
          status = order.status,
        )
      }

  // Radiologist worklist: every order with its study + report state.
  @Transactional(readOnly = true)
  fun worklist(
    hospitalId: UUID?,
    status: String?,
  ): List<RadOrderView> =
    radOrderRepository
      .findAllForHospital(hospitalId, status)
      .sortedByDescending { it.orderedAt }
      .map { order ->
        RadOrderView.from(
          order,
          studyRepository.findByOrderId(order.id!!),
          reportRepository.findByOrderIdOrderByReportVersionDesc(order.id!!),
        )
      }

  @Transactional
  fun completeStudy(
    orderId: UUID,
    dto: StudyCompleteDto,
    principal: SquamedPrincipal,
  ): RadOrderView {
    val order = radOrderRepository.findById(orderId).orElseThrow { IllegalArgumentException("RAD_ORDER_NOT_FOUND") }
    if (order.status == "completed" || order.status == "reported" || order.status == "signed") {
      throw IllegalArgumentException("RAD_ORDER_ALREADY_COMPLETED")
    }
    if (dto.studyInstanceUid.isBlank()) throw IllegalArgumentException("STUDY_UID_REQUIRED")
    val now = Instant.now()
    val study =
      studyRepository.save(
        RadiologyStudy(
          orderId = order.id!!,
          hospitalId = order.hospitalId,
          studyInstanceUid = dto.studyInstanceUid.trim(),
          orthancStudyId = dto.orthancStudyId,
          seriesCount = dto.seriesCount,
          completedAt = now,
          completedBy = principal.userId,
        ),
      )
    order.status = "completed"
    val saved = radOrderRepository.save(order)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "radiology.study_completed",
        occurredAt = now,
        source = "radiology-service",
        entityType = "radiology-study",
        entityId = study.id,
        hospitalId = order.hospitalId,
        payload =
          mapOf(
            "orderId" to order.id.toString(),
            "accession" to order.accession,
            "modality" to order.modality,
            "studyInstanceUid" to study.studyInstanceUid,
          ),
        recipientIds = emptyList(),
      ),
    )
    // 02.08 hook: a completed study is a performed billable service (imaging leg).
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "billing.chargeable_performed",
        occurredAt = now,
        source = "radiology-service",
        entityType = "radiology-study",
        entityId = study.id,
        hospitalId = order.hospitalId,
        payload =
          mapOf(
            "orderNumber" to order.accession,
            "patientId" to order.patientId.toString(),
            "chargeCategory" to "IMAGING",
            "tests" to listOf(mapOf("testCode" to order.modality, "testName" to order.bodyPart, "quantity" to 1)),
          ),
        recipientIds = emptyList(),
      ),
    )
    return RadOrderView.from(saved, study)
  }

  @Transactional
  fun draftReport(
    orderId: UUID,
    dto: RadiologyReportDto,
    principal: SquamedPrincipal,
  ): RadiologyReportView {
    val order = radOrderRepository.findById(orderId).orElseThrow { IllegalArgumentException("RAD_ORDER_NOT_FOUND") }
    val study = studyRepository.findByOrderId(orderId) ?: throw IllegalArgumentException("RAD_STUDY_NOT_COMPLETED")
    if (reportRepository.findByOrderIdOrderByReportVersionDesc(orderId).any { it.status == "draft" }) {
      throw IllegalArgumentException("RAD_REPORT_DRAFT_EXISTS")
    }
    if (dto.findings.isBlank()) throw IllegalArgumentException("REPORT_FINDINGS_REQUIRED")
    if (dto.impression.isBlank()) throw IllegalArgumentException("REPORT_IMPRESSION_REQUIRED")
    val report =
      reportRepository.save(
        RadiologyReport(
          studyId = study.id!!,
          orderId = orderId,
          hospitalId = order.hospitalId,
          templateCode = dto.templateCode,
          findings = dto.findings.trim(),
          impression = dto.impression.trim(),
          icdCodes = dto.icdCodes.joinToString(",").ifEmpty { null },
          snomedCodes = dto.snomedCodes.joinToString(",").ifEmpty { null },
        ),
      )
    order.status = "reported"
    radOrderRepository.save(order)
    return RadiologyReportView.from(report)
  }

  @Transactional
  fun updateDraft(
    reportId: UUID,
    dto: RadiologyReportDto,
    principal: SquamedPrincipal,
  ): RadiologyReportView {
    val report = reportRepository.findById(reportId).orElseThrow { IllegalArgumentException("RAD_REPORT_NOT_FOUND") }
    if (report.status != "draft") throw IllegalArgumentException("RAD_REPORT_ALREADY_SIGNED")
    if (dto.findings.isBlank()) throw IllegalArgumentException("REPORT_FINDINGS_REQUIRED")
    if (dto.impression.isBlank()) throw IllegalArgumentException("REPORT_IMPRESSION_REQUIRED")
    report.templateCode = dto.templateCode
    report.findings = dto.findings.trim()
    report.impression = dto.impression.trim()
    report.icdCodes = dto.icdCodes.joinToString(",").ifEmpty { null }
    report.snomedCodes = dto.snomedCodes.joinToString(",").ifEmpty { null }
    return RadiologyReportView.from(reportRepository.save(report))
  }

  @Transactional
  fun signReport(
    reportId: UUID,
    principal: SquamedPrincipal,
  ): RadiologyReportView {
    val report = reportRepository.findById(reportId).orElseThrow { IllegalArgumentException("RAD_REPORT_NOT_FOUND") }
    if (report.status != "draft") throw IllegalArgumentException("RAD_REPORT_ALREADY_SIGNED")
    val now = Instant.now()
    report.status = "signed"
    report.signedBy = principal.userId
    report.signedAt = now
    val saved = reportRepository.save(report)
    radOrderRepository.findById(report.orderId).ifPresent {
      it.status = "signed"
      radOrderRepository.save(it)
    }
    publishSigned(saved, now)
    return RadiologyReportView.from(saved)
  }

  // Amendments are new signed versions pointing at the original; reason is mandatory.
  @Transactional
  fun amendReport(
    reportId: UUID,
    dto: ReportAmendmentDto,
    principal: SquamedPrincipal,
  ): RadiologyReportView {
    val original = reportRepository.findById(reportId).orElseThrow { IllegalArgumentException("RAD_REPORT_NOT_FOUND") }
    if (original.status != "signed") throw IllegalArgumentException("RAD_REPORT_NOT_SIGNED")
    if (dto.reason.isBlank()) throw IllegalArgumentException("AMENDMENT_REASON_REQUIRED")
    if (dto.findings.isBlank()) throw IllegalArgumentException("REPORT_FINDINGS_REQUIRED")
    if (dto.impression.isBlank()) throw IllegalArgumentException("REPORT_IMPRESSION_REQUIRED")
    val latestVersion = reportRepository.findByOrderIdOrderByReportVersionDesc(original.orderId).maxOf { it.reportVersion }
    val now = Instant.now()
    val amended =
      reportRepository.save(
        RadiologyReport(
          studyId = original.studyId,
          orderId = original.orderId,
          hospitalId = original.hospitalId,
          reportVersion = latestVersion + 1,
          templateCode = original.templateCode,
          findings = dto.findings.trim(),
          impression = dto.impression.trim(),
          icdCodes = dto.icdCodes.joinToString(",").ifEmpty { original.icdCodes },
          snomedCodes = dto.snomedCodes.joinToString(",").ifEmpty { original.snomedCodes },
          status = "signed",
          signedBy = principal.userId,
          signedAt = now,
          amendsReportId = original.id,
          amendmentReason = dto.reason.trim(),
        ),
      )
    publishSigned(amended, now)
    return RadiologyReportView.from(amended)
  }

  private fun publishSigned(
    report: RadiologyReport,
    now: Instant,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "radiology.report_signed",
        occurredAt = now,
        source = "radiology-service",
        entityType = "radiology-report",
        entityId = report.id,
        hospitalId = report.hospitalId,
        payload =
          mapOf(
            "orderId" to report.orderId.toString(),
            "reportVersion" to report.reportVersion.toString(),
            "signedBy" to report.signedBy?.toString(),
            "amendsReportId" to report.amendsReportId?.toString(),
          ),
        recipientIds = emptyList(),
      ),
    )
  }

  @Transactional
  fun raiseCriticalFinding(
    reportId: UUID,
    dto: CriticalFindingRequestDto,
    principal: SquamedPrincipal,
  ): CriticalFindingView {
    val report = reportRepository.findById(reportId).orElseThrow { IllegalArgumentException("RAD_REPORT_NOT_FOUND") }
    if (report.status != "signed") throw IllegalArgumentException("RAD_REPORT_NOT_SIGNED")
    if (dto.severity !in setOf("critical", "urgent")) throw IllegalArgumentException("CRITICAL_SEVERITY_INVALID")
    if (dto.finding.isBlank()) throw IllegalArgumentException("CRITICAL_FINDING_REQUIRED")
    val saved =
      criticalFindingRepository.save(
        RadiologyCriticalFinding(
          reportId = report.id!!,
          hospitalId = report.hospitalId,
          severity = dto.severity,
          finding = dto.finding.trim(),
          slaMinutes = dto.slaMinutes ?: 60,
          raisedBy = principal.userId,
          raisedAt = Instant.now(),
        ),
      )
    return CriticalFindingView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listCriticalFindings(
    hospitalId: UUID?,
    status: String?,
  ): List<CriticalFindingView> = criticalFindingRepository.findAllForHospital(hospitalId, status).map { CriticalFindingView.from(it) }

  @Transactional
  fun acknowledgeCriticalFinding(
    id: UUID,
    dto: CriticalAckDto,
    principal: SquamedPrincipal,
  ): CriticalFindingView {
    val finding = criticalFindingRepository.findById(id).orElseThrow { IllegalArgumentException("CRITICAL_FINDING_NOT_FOUND") }
    if (finding.status != "OPEN") throw IllegalArgumentException("CRITICAL_FINDING_ALREADY_ACKNOWLEDGED")
    finding.status = "ACKNOWLEDGED"
    finding.acknowledgedBy = principal.userId
    finding.acknowledgedAt = Instant.now()
    finding.ackNote = dto.note
    return CriticalFindingView.from(criticalFindingRepository.save(finding))
  }

  // OHIF embedding: short-lived token scoped to one study (typ=viewer, rejected by the API guard).
  @Transactional(readOnly = true)
  fun viewerToken(
    studyId: UUID,
    principal: SquamedPrincipal,
  ): StudyViewerTokenView {
    val study = studyRepository.findById(studyId).orElseThrow { IllegalArgumentException("RAD_STUDY_NOT_FOUND") }
    return StudyViewerTokenView(
      token = jwtService.generateStudyViewerToken(study.id!!, study.studyInstanceUid, principal.userId, study.hospitalId),
      expiresInSeconds = 300,
      viewerUrl = ohifUrl.ifBlank { null },
    )
  }

  // Patient 360 attach: imaging history with the latest signed report summary.
  @Transactional(readOnly = true)
  fun imagingForPatient(patientId: UUID): List<ImagingSummaryView> =
    radOrderRepository
      .findByPatientId(patientId)
      .sortedByDescending { it.orderedAt }
      .take(20)
      .map { order ->
        val study = studyRepository.findByOrderId(order.id!!)
        val signed = reportRepository.findByOrderIdOrderByReportVersionDesc(order.id!!).firstOrNull { it.status == "signed" }
        ImagingSummaryView(
          orderId = order.id,
          accession = order.accession,
          modality = order.modality,
          bodyPart = order.bodyPart,
          status = order.status,
          studyInstanceUid = study?.studyInstanceUid,
          signedReportVersion = signed?.reportVersion,
          impression = signed?.impression,
          orderedAt = order.orderedAt,
        )
      }
}
