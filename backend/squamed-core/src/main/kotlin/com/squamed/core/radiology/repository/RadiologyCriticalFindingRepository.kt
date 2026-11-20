package com.squamed.core.radiology.repository

import com.squamed.core.radiology.domain.RadiologyCriticalFinding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RadiologyCriticalFindingRepository : JpaRepository<RadiologyCriticalFinding, UUID> {
  @Query("select f from RadiologyCriticalFinding f where (:hid is null or f.hospitalId = :hid) and (:status is null or f.status = :status)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String?,
  ): List<RadiologyCriticalFinding>
}
