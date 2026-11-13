package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "medical_history", schema = "patient")
class MedicalHistory(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "condition_name", nullable = false, length = 255)
  var conditionName: String,
  @Column(name = "diagnosis_code", length = 50)
  var diagnosisCode: String? = null,
  @Column(name = "diagnosis_system", length = 20)
  var diagnosisSystem: String = "ICD-10",
  @Column(name = "onset_date")
  var onsetDate: LocalDate? = null,
  @Column(name = "resolved_date")
  var resolvedDate: LocalDate? = null,
  @Column(name = "notes")
  var notes: String? = null,
) : BaseEntity()
