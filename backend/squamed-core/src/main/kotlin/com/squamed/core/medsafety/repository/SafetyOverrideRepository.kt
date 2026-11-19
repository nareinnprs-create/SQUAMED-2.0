package com.squamed.core.medsafety.repository

import com.squamed.core.medsafety.domain.SafetyOverride
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface SafetyOverrideRepository : JpaRepository<SafetyOverride, UUID> {
  fun findByHospitalIdAndOccurredAtAfterOrderByOccurredAtDesc(
    hospitalId: UUID,
    since: Instant,
  ): List<SafetyOverride>

  fun findByOccurredAtAfterOrderByOccurredAtDesc(since: Instant): List<SafetyOverride>
}
