package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "drug", schema = "master_data")
class Drug(
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "generic_name", length = 255)
  var genericName: String? = null,
  @Column(name = "brand_name", length = 255)
  var brandName: String? = null,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "form", length = 50)
  var form: String? = null,
  @Column(name = "strength", length = 100)
  var strength: String? = null,
  @Column(name = "unit", length = 30)
  var unit: String? = null,
  @Column(name = "route", length = 50)
  var route: String? = null,
  @Column(name = "schedule", length = 30)
  var schedule: String? = null,
  @Column(name = "is_controlled", nullable = false)
  var isControlled: Boolean = false,
  @Column(name = "is_antibiotic", nullable = false)
  var isAntibiotic: Boolean = false,
  @Column(name = "requires_prescription", nullable = false)
  var requiresPrescription: Boolean = true,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
