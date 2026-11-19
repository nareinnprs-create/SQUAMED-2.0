package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "critical_result", schema = "laboratory")
class CriticalResult(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "lab_order_id", nullable = false)
  var labOrderId: UUID,
  @Column(name = "lab_test_request_id", nullable = false)
  var labTestRequestId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "test_code", nullable = false, length = 50)
  var testCode: String,
  @Column(name = "test_name", nullable = false, length = 255)
  var testName: String,
  @Column(name = "result_value", length = 50)
  var resultValue: String? = null,
  /** CRITICAL_LOW | CRITICAL_HIGH */
  @Column(name = "criticality", nullable = false, length = 20)
  var criticality: String,
  /** OPEN | ACKNOWLEDGED */
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "OPEN",
  @Column(name = "sla_minutes", nullable = false)
  var slaMinutes: Int,
  @Column(name = "acked_by")
  var ackedBy: UUID? = null,
  @Column(name = "acked_at")
  var ackedAt: Instant? = null,
) : BaseEntity()
