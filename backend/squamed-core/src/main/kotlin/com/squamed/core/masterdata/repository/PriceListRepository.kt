package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.PriceList
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface PriceListRepository : JpaRepository<PriceList, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): PriceList?

  fun findByHospitalId(
    hospitalId: UUID,
    pageable: Pageable,
  ): Page<PriceList>

  fun findByHospitalIdAndIsActive(
    hospitalId: UUID,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<PriceList>

  @Query("select p from PriceList p where p.hospitalId = :hospitalId and (lower(p.name) like lower(:name) or lower(p.code) like lower(:code))")
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<PriceList>

  @Query(
    "select p from PriceList p where p.hospitalId = :hospitalId and (lower(p.name) like lower(:name) or lower(p.code) like lower(:code)) and p.isActive = :isActive",
  )
  fun search(
    hospitalId: UUID,
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<PriceList>
}
