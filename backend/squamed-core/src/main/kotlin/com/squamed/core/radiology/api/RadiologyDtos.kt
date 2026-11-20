package com.squamed.core.radiology.api

import com.squamed.core.radiology.domain.RadOrder
import com.squamed.core.radiology.domain.RadiologyCriticalFinding
import com.squamed.core.radiology.domain.RadiologyReport
import com.squamed.core.radiology.domain.RadiologyStudy
import java.time.Instant
import java.util.UUID

data class RadOrderRequestDto(
  val patientId: UUID,
  val encounterId: UUID? = null,
  val hospitalId: UUID? = null,
  val modality: String,
  val bodyPart: String,
  val priority: String? = null,
  val clinicalNote: String? = null,
)

data class StudyCompleteDto(
  val studyInstanceUid: String,
  val orthancStudyId: String? = null,
  val seriesCount: Int? = null,
)

data class RadiologyReportDto(
  val templateCode: String? = null,
  val findings: String,
  val impression: String,
  val icdCodes: List<String> = emptyList(),
  val snomedCodes: List<String> = emptyList(),
)

data class ReportAmendmentDto(
  val findings: String,
  val impression: String,
  val icdCodes: List<String> = emptyList(),
  val snomedCodes: List<String> = emptyList(),
  val reason: String,
)

data class CriticalFindingRequestDto(
  val severity: String,
  val finding: String,
  val slaMinutes: Int? = null,
)

data class CriticalAckDto(
  val note: String? = null,
)

data class StudyViewerTokenView(
  val token: String,
  val expiresInSeconds: Long,
  val viewerUrl: String?,
)

data class MwlRowView(
  val accession: String,
  val modality: String,
  val bodyPart: String,
  val priority: String,
  val patientId: UUID,
  val patientName: String,
  val status: String,
)

data class RadiologyStudyView(
  val id: UUID?,
  val studyInstanceUid: String,
  val orthancStudyId: String?,
  val seriesCount: Int?,
  val completedAt: Instant?,
) {
  companion object {
    fun from(entity: RadiologyStudy) =
      RadiologyStudyView(
        id = entity.id,
        studyInstanceUid = entity.studyInstanceUid,
        orthancStudyId = entity.orthancStudyId,
        seriesCount = entity.seriesCount,
        completedAt = entity.completedAt,
      )
  }
}

data class RadiologyReportView(
  val id: UUID?,
  val orderId: UUID,
  val reportVersion: Int,
  val templateCode: String?,
  val findings: String,
  val impression: String,
  val icdCodes: List<String>,
  val snomedCodes: List<String>,
  val status: String,
  val signedBy: UUID?,
  val signedAt: Instant?,
  val amendsReportId: UUID?,
  val amendmentReason: String?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: RadiologyReport) =
      RadiologyReportView(
        id = entity.id,
        orderId = entity.orderId,
        reportVersion = entity.reportVersion,
        templateCode = entity.templateCode,
        findings = entity.findings,
        impression = entity.impression,
        icdCodes =
          entity.icdCodes
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: emptyList(),
        snomedCodes =
          entity.snomedCodes
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() } ?: emptyList(),
        status = entity.status,
        signedBy = entity.signedBy,
        signedAt = entity.signedAt,
        amendsReportId = entity.amendsReportId,
        amendmentReason = entity.amendmentReason,
        createdAt = entity.createdAt,
      )
  }
}

data class RadOrderView(
  val id: UUID?,
  val hospitalId: UUID,
  val patientId: UUID,
  val encounterId: UUID?,
  val accession: String,
  val modality: String,
  val bodyPart: String,
  val priority: String,
  val status: String,
  val clinicalNote: String?,
  val orderedAt: Instant?,
  val study: RadiologyStudyView? = null,
  val reports: List<RadiologyReportView> = emptyList(),
) {
  companion object {
    fun from(
      entity: RadOrder,
      study: RadiologyStudy? = null,
      reports: List<RadiologyReport> = emptyList(),
    ) = RadOrderView(
      id = entity.id,
      hospitalId = entity.hospitalId,
      patientId = entity.patientId,
      encounterId = entity.encounterId,
      accession = entity.accession,
      modality = entity.modality,
      bodyPart = entity.bodyPart,
      priority = entity.priority,
      status = entity.status,
      clinicalNote = entity.clinicalNote,
      orderedAt = entity.orderedAt,
      study = study?.let { RadiologyStudyView.from(it) },
      reports = reports.map { RadiologyReportView.from(it) },
    )
  }
}

// 02.05 critical findings: escalation is read-evaluated like 02.03 (>=2x SLA -> cmo, >=SLA -> dept head).
data class CriticalFindingView(
  val id: UUID?,
  val reportId: UUID,
  val severity: String,
  val finding: String,
  val slaMinutes: Int,
  val status: String,
  val raisedAt: Instant?,
  val acknowledgedBy: UUID?,
  val acknowledgedAt: Instant?,
  val ackNote: String?,
  val escalationLevel: Int,
  val escalationRole: String,
) {
  companion object {
    fun from(
      entity: RadiologyCriticalFinding,
      now: Instant = Instant.now(),
    ): CriticalFindingView {
      val elapsedMinutes =
        java.time.Duration
          .between(entity.raisedAt, now)
          .toMinutes()
          .toDouble()
      val level =
        when {
          entity.status != "OPEN" -> 0
          elapsedMinutes >= entity.slaMinutes * 2.0 -> 2
          elapsedMinutes >= entity.slaMinutes -> 1
          else -> 0
        }
      return CriticalFindingView(
        id = entity.id,
        reportId = entity.reportId,
        severity = entity.severity,
        finding = entity.finding,
        slaMinutes = entity.slaMinutes,
        status = entity.status,
        raisedAt = entity.raisedAt,
        acknowledgedBy = entity.acknowledgedBy,
        acknowledgedAt = entity.acknowledgedAt,
        ackNote = entity.ackNote,
        escalationLevel = level,
        escalationRole =
          when (level) {
            2 -> "cmo"
            1 -> "dept_head"
            else -> "none"
          },
      )
    }
  }
}

// 02.05 Patient 360 attach: latest imaging per order with its signed report summary.
data class ImagingSummaryView(
  val orderId: UUID?,
  val accession: String,
  val modality: String,
  val bodyPart: String,
  val status: String,
  val studyInstanceUid: String?,
  val signedReportVersion: Int?,
  val impression: String?,
  val orderedAt: Instant?,
)
