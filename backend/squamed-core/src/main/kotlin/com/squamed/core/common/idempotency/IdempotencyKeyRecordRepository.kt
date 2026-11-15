package com.squamed.core.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface IdempotencyKeyRecordRepository : JpaRepository<IdempotencyKeyRecord, UUID> {
  fun findByIdempotencyKeyAndMethodAndEndpoint(
    idempotencyKey: String,
    method: String,
    endpoint: String,
  ): IdempotencyKeyRecord?

  // 01-ENGINEERING-STANDARDS section 3: keys are stored with their response for 24 h.
  fun deleteByCreatedAtBefore(cutoff: Instant): Long
}
