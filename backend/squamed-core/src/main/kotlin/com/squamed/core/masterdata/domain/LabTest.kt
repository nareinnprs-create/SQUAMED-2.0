package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "lab_test", schema = "master_data")
class LabTest(
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "specimen_type", length = 50)
  var specimenType: String? = null,
  @Column(name = "unit", length = 30)
  var unit: String? = null,
  @Column(name = "reference_range")
  var referenceRange: String? = null,
  @Column(name = "is_urgent", nullable = false)
  var isUrgent: Boolean = false,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
  @Column(name = "critical_low")
  var criticalLow: Double? = null,
  @Column(name = "critical_high")
  var criticalHigh: Double? = null,
  @Column(name = "critical_sla_minutes", nullable = false)
  var criticalSlaMinutes: Int = 60,
) : BaseEntity()
