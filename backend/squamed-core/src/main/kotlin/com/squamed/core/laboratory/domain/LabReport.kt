package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_report", schema = "laboratory")
class LabReport(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "lab_order_id", nullable = false)
  var labOrderId: UUID,
  @Column(name = "report_version", nullable = false)
  var reportVersion: Int,
  @Column(name = "storage_key", nullable = false, length = 400)
  var storageKey: String,
  @Column(name = "published_by")
  var publishedBy: UUID? = null,
  @Column(name = "published_at", nullable = false)
  var publishedAt: Instant = Instant.now(),
  /** COUNTER | APP (null = not yet delivered) */
  @Column(name = "delivered_via", length = 20)
  var deliveredVia: String? = null,
  @Column(name = "delivered_to")
  var deliveredTo: UUID? = null,
  @Column(name = "delivered_at")
  var deliveredAt: Instant? = null,
) : BaseEntity()
