package com.squamed.core.resilience.api

import java.time.Instant
import java.util.UUID

data class DegradedActivateDto(
  val reason: String? = null,
)

data class DegradedCheckDto(
  val module: String,
  val operation: String,
  val appendOnly: Boolean = false,
)

data class DegradedCheckView(
  val active: Boolean,
  val allowed: Boolean,
  val mode: String,
)

data class DegradedStateView(
  val active: Boolean,
  val reason: String?,
  val activatedAt: Instant?,
  val releasedAt: Instant?,
)

data class QueueWriteDto(
  val module: String,
  val operation: String,
  val idempotencyKey: String,
  val payload: Map<String, Any?> = emptyMap(),
  val appendOnly: Boolean = false,
  val occurredAt: Instant,
)

data class QueuedWriteView(
  val id: UUID?,
  val module: String,
  val operation: String,
  val idempotencyKey: String,
  val status: String,
  val resolution: String?,
  val occurredAt: Instant,
)

data class ReplayResultView(
  val queued: Int,
  val replayed: Int,
  val duplicates: Int,
  val rejected: Int,
  val ledger: Long,
)

data class RetentionPolicyDto(
  val entityType: String,
  val retentionDays: Int,
  val legalBasis: String,
  val clinical: Boolean = false,
)

data class RetentionPolicyView(
  val id: UUID?,
  val entityType: String,
  val retentionDays: Int,
  val legalBasis: String,
  val clinical: Boolean,
)

data class LegalHoldDto(
  val subjectType: String,
  val subjectId: UUID,
  val reason: String,
)

data class LegalHoldView(
  val id: UUID?,
  val subjectType: String,
  val subjectId: UUID,
  val reason: String,
  val active: Boolean,
  val releasedAt: Instant?,
)

data class ErasureDto(
  val subjectType: String,
  val subjectId: UUID,
  val reason: String,
)

data class ErasureView(
  val id: UUID?,
  val subjectType: String,
  val subjectId: UUID,
  val status: String,
  val resolution: String?,
  val completedAt: Instant?,
)

data class SandboxSeedDto(
  val patients: Int,
)

data class SandboxSeedView(
  val hospitalId: UUID,
  val created: Int,
  val sampleMrn: String?,
)
