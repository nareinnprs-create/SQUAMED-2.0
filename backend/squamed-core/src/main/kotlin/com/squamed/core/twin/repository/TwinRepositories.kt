package com.squamed.core.twin.repository

import com.squamed.core.twin.domain.CapacitySnapshot
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CapacitySnapshotRepository : JpaRepository<CapacitySnapshot, UUID> {
  fun findTopByHospitalIdOrderByGeneratedAtDesc(hospitalId: UUID): CapacitySnapshot?

  fun findByHospitalIdOrderByGeneratedAtDesc(hospitalId: UUID): List<CapacitySnapshot>
}
