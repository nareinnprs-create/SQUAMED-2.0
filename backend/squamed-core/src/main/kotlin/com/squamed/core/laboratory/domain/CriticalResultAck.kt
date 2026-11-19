package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "critical_result_ack", schema = "laboratory")
class CriticalResultAck(
  @Column(name = "critical_result_id", nullable = false)
  var criticalResultId: UUID,
  @Column(name = "actor_id")
  var actorId: UUID? = null,
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant = Instant.now(),
) : BaseEntity()
