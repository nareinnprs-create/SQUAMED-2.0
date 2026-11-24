package com.squamed.core.resilience.domain

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "degraded_state", schema = "resilience")
class DegradedState(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "active", nullable = false)
  var active: Boolean = false,
  @Column(name = "reason", length = 300)
  var reason: String? = null,
  @Column(name = "activated_by")
  var activatedBy: UUID? = null,
  @Column(name = "activated_at")
  var activatedAt: Instant? = null,
  @Column(name = "released_at")
  var releasedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "queued_write", schema = "resilience")
class QueuedWrite(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "module", nullable = false, length = 40)
  var module: String,
  @Column(name = "operation", nullable = false, length = 40)
  var operation: String,
  @Column(name = "idempotency_key", nullable = false, length = 160)
  var idempotencyKey: String,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  var payload: JsonNode,
  @Column(name = "append_only", nullable = false)
  var appendOnly: Boolean = false,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PENDING",
  @Column(name = "resolution", length = 40)
  var resolution: String? = null,
) : BaseEntity()

@Entity
@Table(name = "replayed_write", schema = "resilience")
class ReplayedWrite(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "idempotency_key", nullable = false, length = 160)
  var idempotencyKey: String,
  @Column(name = "module", nullable = false, length = 40)
  var module: String,
  @Column(name = "operation", nullable = false, length = 40)
  var operation: String,
) : BaseEntity()

@Entity
@Table(name = "retention_policy", schema = "governance")
class RetentionPolicy(
  @Column(name = "entity_type", nullable = false, length = 60)
  var entityType: String,
  @Column(name = "retention_days", nullable = false)
  var retentionDays: Int,
  @Column(name = "legal_basis", nullable = false, length = 200)
  var legalBasis: String,
  @Column(name = "clinical", nullable = false)
  var clinical: Boolean = false,
) : BaseEntity()

@Entity
@Table(name = "legal_hold", schema = "governance")
class LegalHold(
  @Column(name = "subject_type", nullable = false, length = 60)
  var subjectType: String,
  @Column(name = "subject_id", nullable = false)
  var subjectId: UUID,
  @Column(name = "reason", nullable = false, length = 400)
  var reason: String,
  @Column(name = "active", nullable = false)
  var active: Boolean = true,
  @Column(name = "released_at")
  var releasedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "erasure_request", schema = "governance")
class ErasureRequest(
  @Column(name = "subject_type", nullable = false, length = 60)
  var subjectType: String,
  @Column(name = "subject_id", nullable = false)
  var subjectId: UUID,
  @Column(name = "reason", nullable = false, length = 400)
  var reason: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PENDING",
  @Column(name = "resolution", length = 400)
  var resolution: String? = null,
  @Column(name = "requested_by")
  var requestedBy: UUID? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
) : BaseEntity()
