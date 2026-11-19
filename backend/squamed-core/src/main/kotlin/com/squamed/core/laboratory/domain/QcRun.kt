package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "qc_run", schema = "laboratory")
class QcRun(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "instrument", length = 100)
  var instrument: String? = null,
  @Column(name = "test_code", nullable = false, length = 50)
  var testCode: String,
  /** LOW | NORMAL | HIGH */
  @Column(name = "level", nullable = false, length = 10)
  var level: String,
  @Column(name = "observed_value", nullable = false)
  var observedValue: Double,
  @Column(name = "target_mean", nullable = false)
  var targetMean: Double,
  @Column(name = "target_sd", nullable = false)
  var targetSd: Double,
  /** Comma-separated Westgard rules that fired, e.g. "1_3S" or "1_2S". */
  @Column(name = "westgard_rules")
  var westgardRules: String? = null,
  /** ACCEPTED | WARNING | REJECTED */
  @Column(name = "status", nullable = false, length = 20)
  var status: String,
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "run_by")
  var runBy: UUID? = null,
  @Column(name = "run_at", nullable = false)
  var runAt: Instant = Instant.now(),
) : BaseEntity()
