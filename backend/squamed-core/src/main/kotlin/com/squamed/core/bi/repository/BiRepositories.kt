package com.squamed.core.bi.repository

import com.squamed.core.bi.domain.MartSync
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MartSyncRepository : JpaRepository<MartSync, UUID> {
  fun findByHospitalId(hospitalId: UUID): MartSync?
}
