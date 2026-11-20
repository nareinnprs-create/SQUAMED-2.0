package com.squamed.core.radiology.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 02.05: critical imaging finding with an SLA clock; escalation is read-evaluated (02.03 parity).
@Entity
@Table(name = "critical_finding", schema = "radiology")
class RadiologyCriticalFinding(
  @Column(name = "report_id", nullable = false)
  var reportId: UUID,
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "severity", nullable = false, length = 10)
  var severity: String,
  @Column(name = "finding", nullable = false, columnDefinition = "TEXT")
  var finding: String,
  @Column(name = "sla_minutes", nullable = false)
  var slaMinutes: Int = 60,
  @Column(name = "status", nullable = false, length = 14)
  var status: String = "OPEN",
  @Column(name = "raised_by")
  var raisedBy: UUID? = null,
  @Column(name = "raised_at", nullable = false)
  var raisedAt: Instant = Instant.now(),
  @Column(name = "acknowledged_by")
  var acknowledgedBy: UUID? = null,
  @Column(name = "acknowledged_at")
  var acknowledgedAt: Instant? = null,
  @Column(name = "ack_note", length = 400)
  var ackNote: String? = null,
) : BaseEntity()
