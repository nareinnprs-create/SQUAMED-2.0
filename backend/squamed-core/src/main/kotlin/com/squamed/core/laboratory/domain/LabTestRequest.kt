package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_test_request", schema = "laboratory")
class LabTestRequest(
  @Column(name = "lab_order_id", nullable = false)
  var labOrderId: UUID,
  @Column(name = "test_code", nullable = false, length = 50)
  var testCode: String,
  @Column(name = "test_name", nullable = false, length = 255)
  var testName: String,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "ordered",
  @Column(name = "result_value")
  var resultValue: String? = null,
  @Column(name = "unit", length = 30)
  var unit: String? = null,
  @Column(name = "reference_range")
  var referenceRange: String? = null,
  @Column(name = "is_abnormal", nullable = false)
  var isAbnormal: Boolean = false,
  @Column(name = "notes")
  var notes: String? = null,
  @Column(name = "result_entered_by")
  var resultEnteredBy: UUID? = null,
  @Column(name = "result_entered_at")
  var resultEnteredAt: Instant? = null,
  @Column(name = "prior_value")
  var priorValue: String? = null,
  @Column(name = "delta_percent")
  var deltaPercent: Double? = null,
  @Column(name = "entered_via", nullable = false, length = 20)
  var enteredVia: String = "MANUAL",
  @Column(name = "is_delta_blocked", nullable = false)
  var isDeltaBlocked: Boolean = false,
) : BaseEntity()
