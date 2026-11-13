package com.squamed.core.anaesthesia.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "anaesthesia_evaluation", schema = "anaesthesia")
class AnaesthesiaEvaluation(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "asa_status", nullable = false, length = 5) var asaStatus: String,
  @Column(name = "airway_assessment") var airwayAssessment: String? = null,
  @Column(name = "allergies") var allergies: String? = null,
  @Column(name = "medical_conditions") var medicalConditions: String? = null,
  @Column(name = "medications") var medications: String? = null,
  @Column(name = "previous_anaesthesia_complications") var previousComplications: String? = null,
  @Column(name = "fasting_hours") var fastingHours: Int? = null,
  @Column(name = "lab_notes") var labNotes: String? = null,
  @Column(name = "risk_level", length = 20) var riskLevel: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "draft",
  @Column(name = "evaluated_by") var evaluatedBy: UUID? = null,
  @Column(name = "evaluated_at") var evaluatedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "anaesthesia_record", schema = "anaesthesia")
class AnaesthesiaRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "anaesthetist_id") var anaesthetistId: UUID? = null,
  @Column(name = "airway_device", length = 30) var airwayDevice: String? = null,
  @Column(name = "intubation_grade", length = 20) var intubationGrade: String? = null,
  @Column(name = "status", nullable = false, length = 30) var status: String = "planned",
  @Column(name = "started_at") var startedAt: Instant? = null,
  @Column(name = "ended_at") var endedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "intra_op_monitoring", schema = "anaesthesia")
class IntraOpMonitoring(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "anaesthesia_record_id", nullable = false) var anaesthesiaRecordId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "monitored_at", nullable = false) var monitoredAt: Instant = Instant.now(),
  @Column(name = "systolic") var systolic: Int? = null,
  @Column(name = "diastolic") var diastolic: Int? = null,
  @Column(name = "heart_rate", nullable = false) var heartRate: Int,
  @Column(name = "oxygen_saturation") var oxygenSaturation: Int? = null,
  @Column(name = "etco2") var etco2: Int? = null,
  @Column(name = "temperature") var temperature: Double? = null,
  @Column(name = "hourly_urine_ml") var hourlyUrineMl: Int? = null,
  @Column(name = "anaesthesia_depth", length = 30) var anaesthesiaDepth: String? = null,
  @Column(name = "notes") var notes: String? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "anaesthesia_medication", schema = "anaesthesia")
class AnaesthesiaMedication(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "anaesthesia_record_id", nullable = false) var anaesthesiaRecordId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "medication_name", nullable = false, length = 255) var medicationName: String,
  @Column(name = "dose", length = 50) var dose: String? = null,
  @Column(name = "route", length = 30) var route: String? = null,
  @Column(name = "given_at", nullable = false) var givenAt: Instant = Instant.now(),
  @Column(name = "given_by") var givenBy: UUID? = null,
  @Column(name = "notes") var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "recovery_record", schema = "anaesthesia")
class RecoveryRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "aldrete_score", nullable = false) var aldreteScore: Int,
  @Column(name = "discharge_criteria_met", nullable = false) var dischargeCriteriaMet: Boolean = false,
  @Column(name = "recovery_started_at", nullable = false) var recoveryStartedAt: Instant = Instant.now(),
  @Column(name = "recovery_ended_at") var recoveryEndedAt: Instant? = null,
  @Column(name = "observations") var observations: String? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "pacu_record", schema = "anaesthesia")
class PacuRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "nurse_assignment", length = 255) var nurseAssignment: String? = null,
  @Column(name = "pain_score") var painScore: Int? = null,
  @Column(name = "nausea_score") var nauseaScore: Int? = null,
  @Column(name = "bleeding_notes") var bleedingNotes: String? = null,
  @Column(name = "oxygen_saturation") var oxygenSaturation: Int? = null,
  @Column(name = "heart_rate") var heartRate: Int? = null,
  @Column(name = "systolic") var systolic: Int? = null,
  @Column(name = "diastolic") var diastolic: Int? = null,
  @Column(name = "notes") var notes: String? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()
