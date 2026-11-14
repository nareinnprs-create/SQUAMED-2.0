package com.squamed.core.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IdempotencyKeyRecordRepository : JpaRepository<IdempotencyKeyRecord, UUID> {
  fun findByIdempotencyKeyAndMethodAndEndpoint(
    idempotencyKey: String,
    method: String,
    endpoint: String,
  ): IdempotencyKeyRecord?
}
