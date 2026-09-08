package com.squamed.core.emergency.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "emergency_registration", schema = "emergency")
class EmergencyRegistration(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "arrival_mode", nullable = false, length = 30) var arrivalMode: String = "walk_in",
  @Column(name = "arrival_at", nullable = false) var arrivalAt: Instant = Instant.now(),
  @Column(name = "presenting_complaint") var presentingComplaint: String? = null,
  @Column(name = "status", nullable = false, length = 30) var status: String = "registered",
  @Column(name = "triage_esi") var triageEsi: Int? = null,
  @Column(name = "priority", nullable = false, length = 20) var priority: String = "routine",
  @Column(name = "registered_by") var registeredBy: UUID? = null,
  @Column(name = "admitted_admission_id") var admittedAdmissionId: UUID? = null,
  @Column(name = "discharged_at") var dischargedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "triage_assessment", schema = "emergency")
class TriageAssessment(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "registration_id", nullable = false) var registrationId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "esi_level", nullable = false) var esiLevel: Int,
  @Column(name = "triage_category", length = 50) var triageCategory: String? = null,
  @Column(name = "pain_scale") var painScale: Int? = null,
  @Column(name = "vital_triggers", nullable = false) var vitalTriggers: Boolean = false,
  @Column(name = "acuity_notes") var acuityNotes: String? = null,
  @Column(name = "assessed_by") var assessedBy: UUID? = null,
  @Column(name = "assessed_at", nullable = false) var assessedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "trauma_workflow", schema = "emergency")
class TraumaWorkflow(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "registration_id", nullable = false) var registrationId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "survey_phase", nullable = false, length = 20) var surveyPhase: String = "primary",
  @Column(name = "airway") var airway: String? = null,
  @Column(name = "breathing") var breathing: String? = null,
  @Column(name = "circulation") var circulation: String? = null,
  @Column(name = "disability") var disability: String? = null,
  @Column(name = "exposure") var exposure: String? = null,
  @Column(name = "survey_notes") var surveyNotes: String? = null,
  @Column(name = "activated_by") var activatedBy: UUID? = null,
  @Column(name = "activated_at", nullable = false) var activatedAt: Instant = Instant.now(),
  @Column(name = "completed_by") var completedBy: UUID? = null,
  @Column(name = "completed_at") var completedAt: Instant? = null,
) : BaseEntity()
