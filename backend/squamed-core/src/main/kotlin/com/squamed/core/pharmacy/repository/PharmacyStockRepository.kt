package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.PharmacyStock
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PharmacyStockRepository : JpaRepository<PharmacyStock, UUID> {
  fun findByDrugNameContainingIgnoreCase(drugName: String): List<PharmacyStock>

  fun findByIsActiveTrue(): List<PharmacyStock>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): PharmacyStock?

  fun findByDrugId(drugId: UUID): List<PharmacyStock>
}
