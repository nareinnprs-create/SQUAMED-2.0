package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.FormularyEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FormularyEntryRepository : JpaRepository<FormularyEntry, UUID> {
  fun findByHospitalIdAndDrugId(
    hospitalId: UUID,
    drugId: UUID,
  ): FormularyEntry?

  @Query("select f from FormularyEntry f where (:hid is null or f.hospitalId = :hid) and f.isActive = true")
  fun findAllActiveForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<FormularyEntry>
}
