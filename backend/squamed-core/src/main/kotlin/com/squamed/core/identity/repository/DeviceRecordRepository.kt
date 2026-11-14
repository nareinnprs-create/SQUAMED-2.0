package com.squamed.core.identity.repository

import com.squamed.core.identity.domain.DeviceRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface DeviceRecordRepository : JpaRepository<DeviceRecord, UUID> {
  fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<DeviceRecord>

  fun findByUserIdAndFingerprint(
    userId: UUID,
    fingerprint: String,
  ): Optional<DeviceRecord>
}
