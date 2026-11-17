package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.DiagnosisCatalogEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DiagnosisCatalogRepository : JpaRepository<DiagnosisCatalogEntry, UUID> {
  fun findBySystemIgnoreCaseAndCodeIgnoreCaseAndHospitalIdIsNullAndIsActiveTrue(
    system: String,
    code: String,
  ): DiagnosisCatalogEntry?

  fun findByHospitalIdIsNullAndIsActiveTrueOrderBySystemAscCodeAsc(): List<DiagnosisCatalogEntry>
}
