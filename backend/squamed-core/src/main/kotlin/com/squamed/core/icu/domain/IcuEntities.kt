package com.squamed.core.icu.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "icu_vitals_entry", schema = "icu")
class IcuVitalsEntry(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "systolic") var systolic: Int? = null,
  @Column(name = "diastolic") var diastolic: Int? = null,
  @Column(name = "heart_rate") var heartRate: Int? = null,
  @Column(name = "respiratory_rate") var respiratoryRate: Int? = null,
  @Column(name = "temperature") var temperature: Double? = null,
  @Column(name = "oxygen_saturation") var oxygenSaturation: Int? = null,
  @Column(name = "map") var map: Int? = null,
  @Column(name = "etco2") var etco2: Int? = null,
  @Column(name = "gcs") var gcs: Int? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "icu_fluid_balance", schema = "icu")
class IcuFluidBalance(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "input_ml") var inputMl: Double? = null,
  @Column(name = "output_ml") var outputMl: Double? = null,
  @Column(name = "input_type", length = 30) var inputType: String? = null,
  @Column(name = "output_type", length = 30) var outputType: String? = null,
  @Column(name = "balance_at", nullable = false) var balanceAt: Instant = Instant.now(),
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "icu_ventilation_record", schema = "icu")
class IcuVentilationRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "mode", nullable = false, length = 50) var mode: String,
  @Column(name = "fio2_percent") var fio2Percent: Int? = null,
  @Column(name = "peep") var peep: Double? = null,
  @Column(name = "tidal_volume_ml") var tidalVolumeMl: Int? = null,
  @Column(name = "respiratory_rate_set") var respiratoryRateSet: Int? = null,
  @Column(name = "oxygen_saturation") var oxygenSaturation: Int? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "active",
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "icu_infusion_record", schema = "icu")
class IcuInfusionRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "drug_name", nullable = false, length = 255) var drugName: String,
  @Column(name = "dose", length = 100) var dose: String? = null,
  @Column(name = "rate_ml_per_hour") var rateMlPerHour: Double? = null,
  @Column(name = "route", length = 30) var route: String? = null,
  @Column(name = "started_at", nullable = false) var startedAt: Instant = Instant.now(),
  @Column(name = "stopped_at") var stoppedAt: Instant? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "active",
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "icu_critical_alert", schema = "icu")
class IcuCriticalAlert(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "alert_type", nullable = false, length = 50) var alertType: String,
  @Column(name = "severity", nullable = false, length = 20) var severity: String = "critical",
  @Column(name = "message", length = 500) var message: String? = null,
  @Column(name = "payload") var payload: String? = null,
  @Column(name = "triggered_at", nullable = false) var triggeredAt: Instant = Instant.now(),
  @Column(name = "acknowledged_by") var acknowledgedBy: UUID? = null,
  @Column(name = "acknowledged_at") var acknowledgedAt: Instant? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "open",
) : BaseEntity()

// 04.05 severity scores (informational): SOFA component points + total.
@Entity
@Table(name = "severity_score", schema = "icu")
class IcuSeverityScore(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "score_type", nullable = false, length = 10) var scoreType: String = "SOFA",
  @Column(name = "respiration", nullable = false) var respiration: Int = 0,
  @Column(name = "coagulation", nullable = false) var coagulation: Int = 0,
  @Column(name = "liver", nullable = false) var liver: Int = 0,
  @Column(name = "cardiovascular", nullable = false) var cardiovascular: Int = 0,
  @Column(name = "cns", nullable = false) var cns: Int = 0,
  @Column(name = "renal", nullable = false) var renal: Int = 0,
  @Column(name = "total", nullable = false) var total: Int = 0,
  @Column(name = "inputs_json", columnDefinition = "TEXT") var inputsJson: String? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()
