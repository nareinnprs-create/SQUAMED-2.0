package com.squamed.core.downtime.repository

import com.squamed.core.downtime.domain.DowntimeLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DowntimeLogRepository : JpaRepository<DowntimeLog, UUID> {
  fun findByHospitalIdOrderByStartedAtDesc(hospitalId: UUID?): List<DowntimeLog>

  fun findFirstByHospitalIdAndEndedAtIsNullOrderByStartedAtDesc(hospitalId: UUID?): DowntimeLog?
}
