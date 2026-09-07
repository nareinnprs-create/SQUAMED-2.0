package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "empi_score", schema = "patient")
class EmpiScore(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "matched_patient_id")
  var matchedPatientId: UUID? = null,
  @Column(name = "score", nullable = false, precision = 5, scale = 2)
  var score: BigDecimal,
  @Column(name = "attribute", length = 50)
  var attribute: String? = null,
  @Column(name = "rule", length = 255)
  var rule: String? = null,
) : BaseEntity()
