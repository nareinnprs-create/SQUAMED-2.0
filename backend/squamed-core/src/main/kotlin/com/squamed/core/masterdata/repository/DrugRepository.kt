package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.Drug
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface DrugRepository : JpaRepository<Drug, UUID> {
  fun findByIsActiveTrue(): List<Drug>

  fun findByIsActive(
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Drug>

  fun findByCode(code: String): List<Drug>

  fun findByNameContainingIgnoreCase(name: String): List<Drug>

  @Query("select d from Drug d where lower(d.name) like lower(:name) or lower(d.code) like lower(:code)")
  fun search(
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<Drug>

  @Query("select d from Drug d where (lower(d.name) like lower(:name) or lower(d.code) like lower(:code)) and d.isActive = :isActive")
  fun search(
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<Drug>
}
