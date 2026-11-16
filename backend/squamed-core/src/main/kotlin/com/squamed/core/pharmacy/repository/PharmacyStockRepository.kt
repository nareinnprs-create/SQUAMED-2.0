package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.PharmacyStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PharmacyStockRepository : JpaRepository<PharmacyStock, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U9) — admin callers pass null.
  @Query("select s from PharmacyStock s where (:hid is null or s.hospitalId = :hid) and lower(s.drugName) like lower(concat('%', :drugName, '%'))")
  fun findByDrugNameContainingIgnoreCase(
    @Param("hid") hospitalId: UUID?,
    @Param("drugName") drugName: String,
  ): List<PharmacyStock>

  @Query("select s from PharmacyStock s where (:hid is null or s.hospitalId = :hid) and s.isActive = true")
  fun findByIsActiveTrue(
    @Param("hid") hospitalId: UUID?,
  ): List<PharmacyStock>

  @Query("select s from PharmacyStock s where (:hid is null or s.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<PharmacyStock>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): PharmacyStock?

  fun findByDrugId(drugId: UUID): List<PharmacyStock>
}
