package com.squamed.core.medsafety.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** Dose ceiling for one drug: pediatric weight-based and/or adult daily cap, renal eGFR flag. */
@Entity
@Table(name = "dose_rule", schema = "medication")
class DoseRule(
  @Column(name = "drug_code", nullable = false, length = 50)
  var drugCode: String,
  @Column(name = "max_daily_mg_per_kg")
  var maxDailyMgPerKg: Double? = null,
  @Column(name = "adult_max_daily_mg")
  var adultMaxDailyMg: Double? = null,
  @Column(name = "renal_flag", nullable = false)
  var renalFlag: Boolean = false,
  @Column(name = "renal_egfr_threshold")
  var renalEgfrThreshold: Double? = null,
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
