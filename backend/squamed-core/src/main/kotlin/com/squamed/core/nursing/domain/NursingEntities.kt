package com.squamed.core.nursing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "nursing_assessment", schema = "nursing")
class NursingAssessment(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "assessment_type", nullable = false, length = 40) var assessmentType: String,
  @Column(name = "findings") var findings: String? = null,
  @Column(name = "risk_level", length = 20) var riskLevel: String? = null,
  @Column(name = "assessed_by") var assessedBy: UUID? = null,
  @Column(name = "assessed_at", nullable = false) var assessedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "nursing_careplan", schema = "nursing")
class NursingCarePlan(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "title", nullable = false, length = 255) var title: String,
  @Column(name = "goal") var goal: String? = null,
  @Column(name = "interventions") var interventions: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "active",
  @Column(name = "completed_at") var completedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "nursing_note", schema = "nursing")
class NursingNote(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "note_type", nullable = false, length = 30) var noteType: String = "progress",
  @Column(name = "content", nullable = false) var content: String,
  @Column(name = "authored_by") var authoredBy: UUID? = null,
  @Column(name = "authored_at", nullable = false) var authoredAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "nursing_handover", schema = "nursing")
class NursingHandover(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "ward_id") var wardId: UUID? = null,
  @Column(name = "shift", nullable = false, length = 20) var shift: String,
  @Column(name = "situation") var situation: String? = null,
  @Column(name = "background") var background: String? = null,
  @Column(name = "assessment") var assessment: String? = null,
  @Column(name = "recommendation") var recommendation: String? = null,
  @Column(name = "authored_by") var authoredBy: UUID? = null,
  @Column(name = "authored_at", nullable = false) var authoredAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "vitals_entry", schema = "nursing")
class VitalsEntry(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "systolic") var systolic: Int? = null,
  @Column(name = "diastolic") var diastolic: Int? = null,
  @Column(name = "heart_rate") var heartRate: Int? = null,
  @Column(name = "respiratory_rate") var respiratoryRate: Int? = null,
  @Column(name = "temperature") var temperature: Double? = null,
  @Column(name = "oxygen_saturation") var oxygenSaturation: Int? = null,
  @Column(name = "gcs") var gcs: Int? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "medication_administration_record", schema = "nursing")
class MedicationAdministrationRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "medication_name", nullable = false, length = 255) var medicationName: String,
  @Column(name = "dose", length = 100) var dose: String? = null,
  @Column(name = "route", length = 30) var route: String? = null,
  @Column(name = "scheduled_at", nullable = false) var scheduledAt: Instant,
  @Column(name = "administered_at") var administeredAt: Instant? = null,
  @Column(name = "administered_by") var administeredBy: UUID? = null,
  @Column(name = "omitted_reason") var omittedReason: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "scheduled",
  // 04.02 eMAR: prescription link, barcode checks, PRN, high-alert double-check, omission codes.
  @Column(name = "prescription_id") var prescriptionId: UUID? = null,
  @Column(name = "drug_code", length = 100) var drugCode: String? = null,
  @Column(name = "is_prn", nullable = false) var isPrn: Boolean = false,
  @Column(name = "high_alert", nullable = false) var highAlert: Boolean = false,
  @Column(name = "omission_code", length = 40) var omissionCode: String? = null,
  @Column(name = "second_checker_id") var secondCheckerId: UUID? = null,
  @Column(name = "barcode_verified", nullable = false) var barcodeVerified: Boolean = false,
  @Column(name = "prn_reason", length = 400) var prnReason: String? = null,
) : BaseEntity()

@Entity
@Table(name = "risk_assessment", schema = "nursing")
class RiskAssessment(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "risk_type", nullable = false, length = 40) var riskType: String,
  @Column(name = "score") var score: Int? = null,
  @Column(name = "level", length = 20) var level: String? = null,
  @Column(name = "assessed_by") var assessedBy: UUID? = null,
  @Column(name = "assessed_at", nullable = false) var assessedAt: Instant = Instant.now(),
) : BaseEntity()
