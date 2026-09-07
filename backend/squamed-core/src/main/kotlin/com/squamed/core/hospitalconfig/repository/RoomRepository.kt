package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Room
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoomRepository : JpaRepository<Room, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Room>

  fun findByWardId(wardId: UUID): List<Room>

  fun findByBuildingId(buildingId: UUID): List<Room>
}
