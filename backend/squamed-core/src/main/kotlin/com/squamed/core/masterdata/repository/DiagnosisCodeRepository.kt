package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.DiagnosisCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface DiagnosisCodeRepository : JpaRepository<DiagnosisCode, UUID> {
  fun findByIsActiveTrue(): List<DiagnosisCode>

  fun findByIsActive(
    isActive: Boolean,
    pageable: Pageable,
  ): Page<DiagnosisCode>

  fun findByCode(code: String): List<DiagnosisCode>

  fun findByNameContainingIgnoreCase(name: String): List<DiagnosisCode>

  @Query("select d from DiagnosisCode d where lower(d.name) like lower(:name) or lower(d.code) like lower(:code)")
  fun search(
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<DiagnosisCode>

  @Query(
    "select d from DiagnosisCode d where (lower(d.name) like lower(:name) or lower(d.code) like lower(:code)) and d.isActive = :isActive",
  )
  fun search(
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<DiagnosisCode>
}
