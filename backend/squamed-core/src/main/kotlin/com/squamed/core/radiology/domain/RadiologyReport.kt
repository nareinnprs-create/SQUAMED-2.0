package com.squamed.core.radiology.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 02.05: structured imaging report; amendments are new signed versions pointing at the original.
@Entity
@Table(name = "report", schema = "radiology")
class RadiologyReport(
  @Column(name = "study_id", nullable = false)
  var studyId: UUID,
  @Column(name = "order_id", nullable = false)
  var orderId: UUID,
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "report_version", nullable = false)
  var reportVersion: Int = 1,
  @Column(name = "template_code", length = 40)
  var templateCode: String? = null,
  @Column(name = "findings", nullable = false, columnDefinition = "TEXT")
  var findings: String,
  @Column(name = "impression", nullable = false, columnDefinition = "TEXT")
  var impression: String,
  @Column(name = "icd_codes", length = 200)
  var icdCodes: String? = null,
  @Column(name = "snomed_codes", length = 200)
  var snomedCodes: String? = null,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "draft",
  @Column(name = "signed_by")
  var signedBy: UUID? = null,
  @Column(name = "signed_at")
  var signedAt: Instant? = null,
  @Column(name = "amends_report_id")
  var amendsReportId: UUID? = null,
  @Column(name = "amendment_reason", length = 400)
  var amendmentReason: String? = null,
) : BaseEntity()
