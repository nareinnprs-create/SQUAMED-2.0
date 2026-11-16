package com.squamed.core.scheduling.repository

import com.squamed.core.scheduling.domain.WaitlistEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WaitlistRepository : JpaRepository<WaitlistEntry, UUID> {
  // Ordered fair: priority class first (urgent ahead), then FIFO within the class.
  @Query(
    "select w from WaitlistEntry w where (:hid is null or w.hospitalId = :hid) and (:doctorId is null or w.doctorId = :doctorId) and w.status = 'waiting' order by case w.priority when 'urgent' then 0 when 'vip' then 1 else 2 end, w.createdAt asc",
  )
  fun findWaiting(
    @Param("hid") hospitalId: UUID?,
    @Param("doctorId") doctorId: UUID?,
  ): List<WaitlistEntry>

  fun findByHospitalIdAndId(
    hospitalId: UUID,
    id: UUID,
  ): WaitlistEntry?

  fun countByPatientIdAndStatus(
    patientId: UUID,
    status: String,
  ): Long
}
