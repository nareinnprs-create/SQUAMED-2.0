package com.squamed.core.ai.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

// 07.02 voice operating interface: a parsed voice command and its governed decision.
@Entity
@Table(name = "voice_command", schema = "ai")
class VoiceCommand(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "user_id") var userId: UUID? = null,
  @Column(name = "transcript", nullable = false, length = 600) var transcript: String,
  @Column(name = "locale", nullable = false, length = 10) var locale: String = "en",
  @Column(name = "screen", length = 80) var screen: String? = null,
  @Column(name = "patient_id") var patientId: UUID? = null,
  @Column(name = "intent", nullable = false, length = 40) var intent: String,
  @Column(name = "confidence", nullable = false) var confidence: BigDecimal = BigDecimal.ZERO,
  @Column(name = "risk_class", nullable = false, length = 20) var riskClass: String = "UNKNOWN",
  @Column(name = "status", nullable = false, length = 30) var status: String = "ESCALATED",
  @Column(name = "voice_approval_allowed", nullable = false) var voiceApprovalAllowed: Boolean = false,
  @Column(name = "response", length = 600) var response: String? = null,
  @Column(name = "target", length = 120) var target: String? = null,
  @Column(name = "search_term", length = 200) var searchTerm: String? = null,
  @Column(name = "escalated_reason", length = 300) var escalatedReason: String? = null,
  @Column(name = "action_draft_id") var actionDraftId: UUID? = null,
) : BaseEntity()
