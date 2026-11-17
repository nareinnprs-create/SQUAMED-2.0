package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.DisplayZone
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface DisplayZoneRepository : JpaRepository<DisplayZone, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U10) — admin callers pass null.
  @Query("select z from DisplayZone z where (:hid is null or z.hospitalId = :hid) order by z.createdAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<DisplayZone>

  @Query("select z from DisplayZone z where z.hospitalId = :hid and z.servicePointId = :servicePointId and z.enabled = true")
  fun findEnabledForHospitalAndServicePoint(
    @Param("hid") hospitalId: UUID,
    @Param("servicePointId") servicePointId: String,
  ): List<DisplayZone>

  fun findByIdAndHospitalId(
    id: UUID,
    hid: UUID,
  ): Optional<DisplayZone>
}
