package com.squamed.core.resilience.repository

import com.squamed.core.resilience.domain.DegradedState
import com.squamed.core.resilience.domain.ErasureRequest
import com.squamed.core.resilience.domain.LegalHold
import com.squamed.core.resilience.domain.QueuedWrite
import com.squamed.core.resilience.domain.ReplayedWrite
import com.squamed.core.resilience.domain.RetentionPolicy
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DegradedStateRepository : JpaRepository<DegradedState, UUID> {
  fun findByHospitalId(hospitalId: UUID): DegradedState?
}

interface QueuedWriteRepository : JpaRepository<QueuedWrite, UUID> {
  fun findByHospitalIdAndIdempotencyKey(
    hospitalId: UUID,
    idempotencyKey: String,
  ): QueuedWrite?

  fun findByHospitalIdAndStatusOrderByOccurredAtAsc(
    hospitalId: UUID,
    status: String,
  ): List<QueuedWrite>

  fun findByHospitalIdOrderByOccurredAtAsc(hospitalId: UUID): List<QueuedWrite>
}

interface ReplayedWriteRepository : JpaRepository<ReplayedWrite, UUID> {
  fun existsByHospitalIdAndIdempotencyKey(
    hospitalId: UUID,
    idempotencyKey: String,
  ): Boolean

  fun countByHospitalId(hospitalId: UUID): Long
}

interface RetentionPolicyRepository : JpaRepository<RetentionPolicy, UUID> {
  fun findByEntityType(entityType: String): RetentionPolicy?

  fun findAllByOrderByEntityTypeAsc(): List<RetentionPolicy>
}

interface LegalHoldRepository : JpaRepository<LegalHold, UUID> {
  fun findBySubjectTypeAndSubjectIdAndActiveTrue(
    subjectType: String,
    subjectId: UUID,
  ): List<LegalHold>
}

interface ErasureRequestRepository : JpaRepository<ErasureRequest, UUID> {
  fun findAllByOrderByCreatedAtDesc(): List<ErasureRequest>
}
