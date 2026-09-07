package com.squamed.core.scribe.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "scribe_session", schema = "scribe")
class ScribeSession(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "patient_id")
  var patientId: UUID? = null,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_name", length = 255)
  var patientName: String? = null,
  @Column(name = "chief_complaint", columnDefinition = "TEXT")
  var chiefComplaint: String? = null,
  @Column(name = "source", nullable = false, length = 20)
  var source: String = "MANUAL",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "DRAFT",
  @Column(name = "stage", nullable = false, length = 30)
  var stage: String = "CREATED",
  @Column(name = "transcript", columnDefinition = "TEXT")
  var transcript: String? = null,
  @Column(name = "segments", columnDefinition = "TEXT")
  var segmentsJson: String? = null,
  @Column(name = "speaker_map", columnDefinition = "TEXT")
  var speakerMapJson: String? = null,
  @Column(name = "draft", columnDefinition = "TEXT")
  var draft: String? = null,
  @Column(name = "model_provider", length = 30)
  var modelProvider: String? = null,
  @Column(name = "model_name", length = 160)
  var modelName: String? = null,
  @Column(name = "duration_seconds", nullable = false)
  var durationSeconds: Int = 0,
  @Column(name = "signed_at")
  var signedAt: Instant? = null,
) : BaseEntity()
