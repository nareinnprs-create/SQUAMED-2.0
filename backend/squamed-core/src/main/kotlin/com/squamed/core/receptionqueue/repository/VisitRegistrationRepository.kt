package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.VisitRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface VisitRegistrationRepository : JpaRepository<VisitRegistration, UUID> {
  // 00.04 tenant sweep: unfiltered variant kept for cross-module system callers (patient360).
  fun findByPatientId(patientId: UUID): List<VisitRegistration>

  // 00.04 tenant sweep: null-safe hospital scope (U11) — admin callers pass null.
  @Query("select v from VisitRegistration v where (:hid is null or v.hospitalId = :hid) and v.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<VisitRegistration>

  fun findByVisitNumber(visitNumber: String): VisitRegistration?

  // 00.04 tenant sweep: null-safe hospital scope (U11).
  @Query("select v from VisitRegistration v where (:hid is null or v.hospitalId = :hid) and v.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<VisitRegistration>

  fun findByHospitalId(hospitalId: UUID): List<VisitRegistration>

  // 00.04 tenant sweep: null-safe hospital scope for the default /reception/visits branch (findAll has no filter hook).
  @Query("select v from VisitRegistration v where (:hid is null or v.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<VisitRegistration>

  fun findTopByOrderByVisitNumberDesc(): VisitRegistration?
}
