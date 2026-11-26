package com.squamed.core.ops.repository

import com.squamed.core.ops.domain.EdDrill
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EdDrillRepository : JpaRepository<EdDrill, UUID> {
  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<EdDrill>
}
