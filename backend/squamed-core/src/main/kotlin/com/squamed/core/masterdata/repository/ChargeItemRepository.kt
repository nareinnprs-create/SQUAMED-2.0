package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.ChargeItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ChargeItemRepository : JpaRepository<ChargeItem, UUID> {
  fun findByIsActiveTrue(): List<ChargeItem>

  fun findByIsActive(
    isActive: Boolean,
    pageable: Pageable,
  ): Page<ChargeItem>

  fun findByCode(code: String): List<ChargeItem>

  fun findByNameContainingIgnoreCase(name: String): List<ChargeItem>

  @Query("select c from ChargeItem c where lower(c.name) like lower(:name) or lower(c.code) like lower(:code)")
  fun search(
    name: String,
    code: String,
    pageable: Pageable,
  ): Page<ChargeItem>

  @Query(
    "select c from ChargeItem c where (lower(c.name) like lower(:name) or lower(c.code) like lower(:code)) and c.isActive = :isActive",
  )
  fun search(
    name: String,
    code: String,
    isActive: Boolean,
    pageable: Pageable,
  ): Page<ChargeItem>
}
