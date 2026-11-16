package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.Dispensation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DispensationRepository : JpaRepository<Dispensation, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U9) — admin callers pass null.
  @Query("select d from Dispensation d where (:hid is null or d.hospitalId = :hid) and d.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Dispensation>

  fun findByPrescriptionId(prescriptionId: UUID): List<Dispensation>

  @Query("select d from Dispensation d where (:hid is null or d.hospitalId = :hid) and d.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<Dispensation>

  fun findByStatusIn(statuses: Collection<String>): List<Dispensation>

  @Query("select d from Dispensation d where (:hid is null or d.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Dispensation>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Dispensation?
}
