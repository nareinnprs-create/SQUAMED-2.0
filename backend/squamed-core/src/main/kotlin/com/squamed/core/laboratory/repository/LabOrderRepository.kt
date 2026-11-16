package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface LabOrderRepository : JpaRepository<LabOrder, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U8) — admin callers pass null.
  @Query("select o from LabOrder o where (:hid is null or o.hospitalId = :hid) and o.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<LabOrder>

  @Query("select o from LabOrder o where (:hid is null or o.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<LabOrder>

  fun findByStatus(status: String): List<LabOrder>

  fun findByResultStatus(resultStatus: String): List<LabOrder>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Optional<LabOrder>

  fun findByOrderNumber(orderNumber: String): Optional<LabOrder>

  fun findByPatientIdOrderByOrderedAtDesc(patientId: UUID): List<LabOrder>

  fun findByStatusIn(statuses: Collection<String>): List<LabOrder>

  fun findTopByOrderByOrderNumberDesc(): LabOrder?
}
