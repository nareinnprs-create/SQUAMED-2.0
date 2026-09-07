package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "diagnosis", schema = "clinical")
class Diagnosis(
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "system", nullable = false, length = 20)
  var system: String = "ICD-10",
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "type", nullable = false, length = 20)
  var type: String = "working",
  @Column(name = "notes")
  var notes: String? = null,
) : BaseEntity()
