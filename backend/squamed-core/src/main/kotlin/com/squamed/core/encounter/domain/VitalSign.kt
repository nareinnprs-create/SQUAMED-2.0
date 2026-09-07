package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "vital_sign", schema = "clinical")
class VitalSign(
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "recorded_at")
  var recordedAt: Instant? = null,
  @Column(name = "recorded_by")
  var recordedBy: UUID? = null,
  @Column(name = "blood_pressure_systolic")
  var bloodPressureSystolic: Int? = null,
  @Column(name = "blood_pressure_diastolic")
  var bloodPressureDiastolic: Int? = null,
  @Column(name = "heart_rate")
  var heartRate: Int? = null,
  @Column(name = "respiratory_rate")
  var respiratoryRate: Int? = null,
  @Column(name = "temperature_c")
  var temperatureC: BigDecimal? = null,
  @Column(name = "spo2_percent")
  var spo2Percent: Int? = null,
  @Column(name = "height_cm")
  var heightCm: BigDecimal? = null,
  @Column(name = "weight_kg")
  var weightKg: BigDecimal? = null,
  @Column(name = "bmi")
  var bmi: BigDecimal? = null,
  @Column(name = "pain_score")
  var painScore: Int? = null,
  @Column(name = "notes")
  var notes: String? = null,
) : BaseEntity()
