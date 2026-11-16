package com.squamed.core.scheduling.repository

import com.squamed.core.scheduling.domain.ScheduleTemplate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScheduleTemplateRepository : JpaRepository<ScheduleTemplate, UUID> {
  // 00.04 tenant sweep (U4): null-safe hospital scope — admin callers pass null.
  @org.springframework.data.jpa.repository.Query(
    "select t from ScheduleTemplate t where (:hid is null or t.hospitalId = :hid) and (:doctorId is null or t.doctorId = :doctorId) order by t.weekday, t.startTime",
  )
  fun findTemplates(
    @org.springframework.data.repository.query.Param("hid") hospitalId: UUID?,
    @org.springframework.data.repository.query.Param("doctorId") doctorId: UUID?,
  ): List<ScheduleTemplate>

  fun findByHospitalIdAndId(
    hospitalId: UUID,
    id: UUID,
  ): ScheduleTemplate?
}
