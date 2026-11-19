package com.squamed.core.medsafety.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** One interaction rule from one provenance source (00-MASTER-PLAN §5 risk 2: dual-source). */
@Entity
@Table(name = "interaction_rule", schema = "medication")
class InteractionRule(
  @Column(name = "drug_a_code", nullable = false, length = 50)
  var drugACode: String,
  @Column(name = "drug_b_code", nullable = false, length = 50)
  var drugBCode: String,
  @Column(name = "severity", nullable = false, length = 10)
  var severity: String,
  @Column(name = "confidence", nullable = false)
  var confidence: Double,
  @Column(name = "source", nullable = false, length = 20)
  var source: String,
  @Column(name = "description", length = 500)
  var description: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
