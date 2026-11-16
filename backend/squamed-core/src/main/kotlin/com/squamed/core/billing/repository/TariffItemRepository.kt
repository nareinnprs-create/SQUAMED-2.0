package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.TariffItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface TariffItemRepository : JpaRepository<TariffItem, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U6) — admin callers pass null.
  @Query("select t from TariffItem t where (:hid is null or t.hospitalId = :hid) and t.code = :code")
  fun findByCode(
    @Param("hid") hospitalId: UUID?,
    @Param("code") code: String,
  ): List<TariffItem>

  @Query("select t from TariffItem t where (:hid is null or t.hospitalId = :hid) and t.isActive = true")
  fun findByIsActiveTrue(
    @Param("hid") hospitalId: UUID?,
  ): List<TariffItem>

  @Query("select t from TariffItem t where (:hid is null or t.hospitalId = :hid) and lower(t.name) like lower(concat('%', :name, '%'))")
  fun findByNameContainingIgnoreCase(
    @Param("hid") hospitalId: UUID?,
    @Param("name") name: String,
  ): List<TariffItem>

  @Query("select t from TariffItem t where (:hid is null or t.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<TariffItem>
}
