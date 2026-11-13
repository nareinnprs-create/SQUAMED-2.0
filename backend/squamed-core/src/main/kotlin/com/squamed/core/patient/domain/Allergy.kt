package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "allergy", schema = "patient")
class Allergy(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "allergen", nullable = false, length = 255)
  var allergen: String,
  @Column(name = "reaction", length = 255)
  var reaction: String? = null,
  @Column(name = "severity", nullable = false, length = 20)
  var severity: String = "mild",
  @Column(name = "recorded_date")
  var recordedDate: LocalDate? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
  @Column(name = "notes")
  var notes: String? = null,
) : BaseEntity()
