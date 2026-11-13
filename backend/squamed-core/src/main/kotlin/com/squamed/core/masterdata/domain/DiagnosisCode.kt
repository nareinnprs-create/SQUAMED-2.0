package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "diagnosis_code", schema = "master_data")
class DiagnosisCode(
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "system", nullable = false, length = 50)
  var system: String = "ICD-10",
  @Column(name = "chapter", length = 100)
  var chapter: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
