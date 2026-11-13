package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.ImagingProcedure
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ImagingProcedureRepository : JpaRepository<ImagingProcedure, UUID> {
  fun findByIsActiveTrue(): List<ImagingProcedure>

  fun findByIsActive(
    isActive: Boolean,
    pageable: Pageable,
  ): Page<ImagingProcedure>

  fun findByCode(code: String): List<ImagingProcedure>

  fun findByNameContainingIgnoreCase(name: String): List<ImagingProcedure>

  @Query("select i from ImagingProcedure i where lower(i.name) like lower(:name) or lower(i.code) like lower(:code)")
  fun search(
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<ImagingProcedure>

  @Query(
    "select i from ImagingProcedure i where (lower(i.name) like lower(:name) or lower(i.code) like lower(:code)) and i.isActive = :isActive",
  )
  fun search(
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<ImagingProcedure>
}
