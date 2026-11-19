package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "lab_reference_range", schema = "laboratory")
class LabReferenceRange(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "test_code", nullable = false, length = 50)
  var testCode: String,
  /** M | F | ANY */
  @Column(name = "sex", nullable = false, length = 10)
  var sex: String,
  @Column(name = "age_min_years", nullable = false)
  var ageMinYears: Int = 0,
  @Column(name = "age_max_years", nullable = false)
  var ageMaxYears: Int = 200,
  @Column(name = "range_text", nullable = false, length = 100)
  var rangeText: String,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
