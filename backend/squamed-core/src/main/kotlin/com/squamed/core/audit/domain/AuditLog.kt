package com.squamed.core.audit.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_log", schema = "audit")
class AuditLog(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "actor_user_id")
  var actorUserId: UUID? = null,
  @Column(name = "actor_email", length = 255)
  var actorEmail: String? = null,
  @Column(name = "action", nullable = false, length = 100)
  var action: String,
  @Column(name = "entity_type", nullable = false, length = 100)
  var entityType: String,
  @Column(name = "entity_id", length = 100)
  var entityId: String? = null,
  @Column(name = "resource", length = 255)
  var resource: String? = null,
  @Column(name = "ip_address", length = 64)
  var ipAddress: String? = null,
  @Column(name = "user_agent", length = 400)
  var userAgent: String? = null,
  @Column(name = "method", length = 10)
  var method: String? = null,
  @Column(name = "path", length = 255)
  var path: String? = null,
  @Column(name = "request_payload")
  var requestPayload: String? = null,
  @Column(name = "response_status")
  var responseStatus: Int? = null,
  @Column(name = "response_payload")
  var responsePayload: String? = null,
  // ADR-9 hash chain: per-tenant sequence + previous row hash + this row's hash.
  @Column(name = "chain_seq")
  var chainSeq: Long? = null,
  @Column(name = "previous_hash", length = 64)
  var previousHash: String? = null,
  @Column(name = "row_hash", length = 64)
  var rowHash: String? = null,
  // Actor identity incl. device (00.10): the login session and its registered device.
  @Column(name = "actor_session_id")
  var actorSessionId: UUID? = null,
  @Column(name = "device_id")
  var deviceId: UUID? = null,
  // Before/after capture (Hibernate interceptor drain, JSON).
  @Column(name = "before_state", columnDefinition = "text")
  var beforeState: String? = null,
  @Column(name = "after_state", columnDefinition = "text")
  var afterState: String? = null,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant = Instant.now(),
) : BaseEntity()
