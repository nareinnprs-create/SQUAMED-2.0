package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabReferenceRange
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LabReferenceRangeRepository : JpaRepository<LabReferenceRange, UUID> {
  fun findByHospitalIdAndTestCodeAndIsActiveTrue(
    hospitalId: UUID,
    testCode: String,
  ): List<LabReferenceRange>

  // 00.04 tenant sweep: null-safe hospital scope (U8) — admin callers pass null.
  @Query("select r from LabReferenceRange r where (:hid is null or r.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<LabReferenceRange>
}
