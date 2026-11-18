package com.squamed.core.downtime.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 01.09 downtime pack v1 (CS-09): one row per downtime window - open when the system goes
 * down, closed on recovery; the printed pack and the re-entry tool reference the window. */
@Entity
@Table(name = "downtime_log", schema = "reception")
class DowntimeLog(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID? = null,
  @Column(name = "reason", nullable = false, length = 200)
  var reason: String,
  @Column(name = "started_at", nullable = false)
  var startedAt: Instant = Instant.now(),
  @Column(name = "ended_at")
  var endedAt: Instant? = null,
  @Column(name = "notes", columnDefinition = "text")
  var notes: String? = null,
) : BaseEntity()
