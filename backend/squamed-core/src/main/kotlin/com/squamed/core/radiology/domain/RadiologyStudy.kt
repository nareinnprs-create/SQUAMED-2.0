package com.squamed.core.radiology.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 02.05: a completed study as Orthanc reports it (PACS owns the pixels; we keep the refs).
@Entity
@Table(name = "study", schema = "radiology")
class RadiologyStudy(
  @Column(name = "order_id", nullable = false)
  var orderId: UUID,
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "study_instance_uid", nullable = false, length = 128)
  var studyInstanceUid: String,
  @Column(name = "orthanc_study_id", length = 64)
  var orthancStudyId: String? = null,
  @Column(name = "series_count")
  var seriesCount: Int? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
  @Column(name = "completed_by")
  var completedBy: UUID? = null,
) : BaseEntity()
