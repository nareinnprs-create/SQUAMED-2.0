package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.Vendor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VendorRepository : JpaRepository<Vendor, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): Vendor?

  fun findByHospitalId(
    hospitalId: UUID,
    pageable: Pageable,
  ): Page<Vendor>

  fun findByHospitalIdAndIsActive(
    hospitalId: UUID,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Vendor>

  @Query("select v from Vendor v where v.hospitalId = :hospitalId and (lower(v.name) like lower(:name) or lower(v.code) like lower(:code))")
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<Vendor>

  @Query(
    "select v from Vendor v where v.hospitalId = :hospitalId and (lower(v.name) like lower(:name) or lower(v.code) like lower(:code)) and v.isActive = :isActive",
  )
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Vendor>
}
