package com.squamed.core.cx.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "contact_interaction", schema = "cx")
class ContactInteraction(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id") var patientId: UUID? = null,
  @Column(name = "channel", nullable = false, length = 20) var channel: String,
  @Column(name = "direction", nullable = false, length = 20) var direction: String,
  @Column(name = "subject", nullable = false, length = 300) var subject: String,
  @Column(name = "agent_id") var agentId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "OPEN",
  @Column(name = "resolution", length = 600) var resolution: String? = null,
  @Column(name = "ai_suggested_reply", length = 600) var aiSuggestedReply: String? = null,
  @Column(name = "closed_at") var closedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "intl_case", schema = "cx")
class IntlCase(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "country", nullable = false, length = 80) var country: String,
  @Column(name = "language", nullable = false, length = 40) var language: String,
  @Column(name = "interpreter_needed", nullable = false) var interpreterNeeded: Boolean = false,
  @Column(name = "coordinator_id") var coordinatorId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "OPEN",
  @Column(name = "notes", length = 600) var notes: String? = null,
  @Column(name = "closed_at") var closedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "feedback", schema = "cx")
class PatientFeedback(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "score", nullable = false) var score: Int,
  @Column(name = "category", nullable = false, length = 60) var category: String,
  @Column(name = "comment", length = 1000) var comment: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "NEW",
  @Column(name = "followup_task_id") var followupTaskId: UUID? = null,
  @Column(name = "resolution", length = 600) var resolution: String? = null,
  @Column(name = "resolved_at") var resolvedAt: Instant? = null,
) : BaseEntity()
