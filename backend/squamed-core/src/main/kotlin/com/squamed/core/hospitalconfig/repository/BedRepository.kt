package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Bed
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BedRepository : JpaRepository<Bed, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<Bed>

  fun findByWardId(wardId: UUID): List<Bed>

  fun findByRoomId(roomId: UUID): List<Bed>

  fun findByStatus(status: String): List<Bed>
}
