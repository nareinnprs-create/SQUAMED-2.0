package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.ProcedureCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProcedureCodeRepository : JpaRepository<ProcedureCode, UUID> {
  fun findByIsActiveTrue(): List<ProcedureCode>

  fun findByIsActive(
    isActive: Boolean,
    pageable: Pageable,
  ): Page<ProcedureCode>

  fun findByCode(code: String): List<ProcedureCode>

  fun findByNameContainingIgnoreCase(name: String): List<ProcedureCode>

  @Query("select p from ProcedureCode p where lower(p.name) like lower(:name) or lower(p.code) like lower(:code)")
  fun search(
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<ProcedureCode>

  @Query(
    "select p from ProcedureCode p where (lower(p.name) like lower(:name) or lower(p.code) like lower(:code)) and p.isActive = :isActive",
  )
  fun search(
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<ProcedureCode>
}
