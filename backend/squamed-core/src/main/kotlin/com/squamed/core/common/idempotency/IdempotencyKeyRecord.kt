package com.squamed.core.common.idempotency

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "idempotency_keys", schema = "platform")
class IdempotencyKeyRecord(
  @Column(name = "idempotency_key", nullable = false, length = 255)
  var idempotencyKey: String,
  @Column(name = "method", nullable = false, length = 10)
  var method: String,
  @Column(name = "endpoint", nullable = false, length = 512)
  var endpoint: String,
  @Column(name = "request_hash", nullable = false, length = 64)
  var requestHash: String,
  @Column(name = "response_status")
  var responseStatus: Int? = null,
  @Column(name = "response_body", columnDefinition = "text")
  var responseBody: String? = null,
  @Column(name = "content_type", length = 255)
  var contentType: String? = null,
) : BaseEntity()
