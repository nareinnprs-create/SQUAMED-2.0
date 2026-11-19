package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Versioned amendment of a released lab result — the prior value is preserved, never edited in place. */
@Entity
@Table(name = "lab_result_amendment", schema = "laboratory")
class LabResultAmendment(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "lab_order_id", nullable = false)
  var labOrderId: UUID,
  @Column(name = "lab_test_request_id", nullable = false)
  var labTestRequestId: UUID,
  @Column(name = "old_value", columnDefinition = "text")
  var oldValue: String? = null,
  @Column(name = "new_value", columnDefinition = "text", nullable = false)
  var newValue: String,
  @Column(name = "reason", nullable = false, length = 400)
  var reason: String,
  @Column(name = "amended_by")
  var amendedBy: UUID? = null,
  @Column(name = "amended_at", nullable = false)
  var amendedAt: Instant = Instant.now(),
) : BaseEntity()
