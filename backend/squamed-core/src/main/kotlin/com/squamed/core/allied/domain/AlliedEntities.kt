package com.squamed.core.allied.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "therapy_referral", schema = "allied")
class TherapyReferral(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "discipline", nullable = false, length = 20) var discipline: String,
  @Column(name = "reason", nullable = false, length = 600) var reason: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "REFERRED",
  @Column(name = "therapist_id") var therapistId: UUID? = null,
  @Column(name = "scheduled_at") var scheduledAt: Instant? = null,
  @Column(name = "appointment_id") var appointmentId: UUID? = null,
  @Column(name = "care_plan_id") var carePlanId: UUID? = null,
  @Column(name = "sessions_planned", nullable = false) var sessionsPlanned: Int = 4,
  @Column(name = "outcome", length = 600) var outcome: String? = null,
  @Column(name = "completed_at") var completedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "therapy_session", schema = "allied")
class TherapySession(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "referral_id", nullable = false) var referralId: UUID,
  @Column(name = "session_at", nullable = false) var sessionAt: Instant,
  @Column(name = "duration_minutes", nullable = false) var durationMinutes: Int,
  @Column(name = "notes", columnDefinition = "TEXT") var notes: String? = null,
  @Column(name = "outcome", length = 300) var outcome: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "DRAFT",
  @Column(name = "signed_by") var signedBy: UUID? = null,
  @Column(name = "signed_at") var signedAt: Instant? = null,
) : BaseEntity()
