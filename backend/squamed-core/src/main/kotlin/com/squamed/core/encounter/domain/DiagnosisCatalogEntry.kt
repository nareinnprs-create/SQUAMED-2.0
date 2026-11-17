package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** 01.04 coded-diagnosis catalog entry (global, hospital_id NULL; validation source for addDiagnosis). */
@Entity
@Table(name = "diagnosis_catalog", schema = "clinical")
class DiagnosisCatalogEntry(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "code", nullable = false, length = 20)
  var code: String,
  @Column(name = "system", nullable = false, length = 20)
  var system: String = "ICD-10",
  @Column(name = "display", nullable = false, length = 255)
  var display: String,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
