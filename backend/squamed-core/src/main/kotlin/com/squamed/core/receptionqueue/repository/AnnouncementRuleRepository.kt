package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.AnnouncementRule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AnnouncementRuleRepository : JpaRepository<AnnouncementRule, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U10) — admin callers pass null.
  @Query("select r from AnnouncementRule r where (:hid is null or r.hospitalId = :hid) order by r.createdAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<AnnouncementRule>

  @Query("select r from AnnouncementRule r where r.hospitalId = :hid and r.queueType = :queueType and r.enabled = true order by r.createdAt desc")
  fun findEnabledForHospitalAndQueueType(
    @Param("hid") hospitalId: UUID,
    @Param("queueType") queueType: String,
  ): List<AnnouncementRule>
}
