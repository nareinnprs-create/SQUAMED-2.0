package com.squamed.core.him.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 03.05 HIM coding: one active assignment per completed encounter.
@Entity
@Table(name = "coding_assignment", schema = "him")
class CodingAssignment(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "encounter_id", nullable = false)
  var encounterId: UUID,
  @Column(name = "icd_codes", nullable = false, length = 300)
  var icdCodes: String,
  @Column(name = "snomed_codes", length = 300)
  var snomedCodes: String? = null,
  @Column(name = "coder_id")
  var coderId: UUID? = null,
  @Column(name = "coded_at", nullable = false)
  var codedAt: Instant = Instant.now(),
) : BaseEntity()

// 03.05 coder query: opens a task for the clinician; answer closes the loop.
@Entity
@Table(name = "coder_query", schema = "him")
class CoderQuery(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "encounter_id", nullable = false)
  var encounterId: UUID,
  @Column(name = "question", nullable = false, columnDefinition = "TEXT")
  var question: String,
  @Column(name = "status", nullable = false, length = 12)
  var status: String = "OPEN",
  @Column(name = "task_id")
  var taskId: UUID? = null,
  @Column(name = "raised_by")
  var raisedBy: UUID? = null,
  @Column(name = "raised_at", nullable = false)
  var raisedAt: Instant = Instant.now(),
  @Column(name = "answer", columnDefinition = "TEXT")
  var answer: String? = null,
  @Column(name = "answered_by")
  var answeredBy: UUID? = null,
  @Column(name = "answered_at")
  var answeredAt: Instant? = null,
) : BaseEntity()

// 03.05 revenue integrity finding: dedupe_key makes daily scans idempotent.
@Entity
@Table(name = "finding", schema = "integrity")
class IntegrityFinding(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "rule_code", nullable = false, length = 40)
  var ruleCode: String,
  @Column(name = "severity", nullable = false, length = 10)
  var severity: String = "warning",
  @Column(name = "subject_type", nullable = false, length = 30)
  var subjectType: String,
  @Column(name = "subject_id")
  var subjectId: UUID? = null,
  @Column(name = "detail", length = 400)
  var detail: String? = null,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "OPEN",
  @Column(name = "dedupe_key", nullable = false, length = 160)
  var dedupeKey: String,
  @Column(name = "detected_at", nullable = false)
  var detectedAt: Instant = Instant.now(),
  @Column(name = "resolved_by")
  var resolvedBy: UUID? = null,
  @Column(name = "resolved_at")
  var resolvedAt: Instant? = null,
  @Column(name = "resolution", length = 400)
  var resolution: String? = null,
) : BaseEntity()
