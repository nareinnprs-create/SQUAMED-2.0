package com.squamed.core.medsafety.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Audited override of a safety finding; the reason is mandatory (§5 rule 4, KPI-tracked). */
@Entity
@Table(name = "safety_override", schema = "medication")
class SafetyOverride(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "drug_code", length = 50)
  var drugCode: String? = null,
  @Column(name = "finding_type", nullable = false, length = 20)
  var findingType: String,
  @Column(name = "severity", nullable = false, length = 10)
  var severity: String,
  @Column(name = "reason", nullable = false, length = 400)
  var reason: String,
  @Column(name = "overridden_by")
  var overriddenBy: UUID? = null,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant = Instant.now(),
) : BaseEntity()
