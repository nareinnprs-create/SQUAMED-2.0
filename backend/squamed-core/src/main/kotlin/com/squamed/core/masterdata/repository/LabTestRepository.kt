package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.LabTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface LabTestRepository : JpaRepository<LabTest, UUID> {
  fun findByIsActiveTrue(): List<LabTest>

  fun findByIsActive(
    isActive: Boolean,
    pageable: Pageable,
  ): Page<LabTest>

  fun findByCode(code: String): List<LabTest>

  fun findByNameContainingIgnoreCase(name: String): List<LabTest>

  @Query("select l from LabTest l where lower(l.name) like lower(:name) or lower(l.code) like lower(:code)")
  fun search(
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<LabTest>

  @Query("select l from LabTest l where (lower(l.name) like lower(:name) or lower(l.code) like lower(:code)) and l.isActive = :isActive")
  fun search(
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<LabTest>
}
