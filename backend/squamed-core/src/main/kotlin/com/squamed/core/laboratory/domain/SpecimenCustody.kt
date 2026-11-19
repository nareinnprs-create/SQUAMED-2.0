package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Chain-of-custody: one row per specimen status change (who/when/what). */
@Entity
@Table(name = "specimen_custody", schema = "laboratory")
class SpecimenCustody(
  @Column(name = "lab_order_id", nullable = false)
  var labOrderId: UUID,
  @Column(name = "event", nullable = false, length = 30)
  var event: String,
  @Column(name = "actor_id")
  var actorId: UUID? = null,
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant? = null,
) : BaseEntity()
