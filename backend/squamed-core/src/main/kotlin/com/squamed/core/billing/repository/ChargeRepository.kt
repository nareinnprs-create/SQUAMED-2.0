package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Charge
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ChargeRepository : JpaRepository<Charge, UUID> {
  // 03.01 ledger idempotency: one row per source event.
  fun findBySourceEventId(sourceEventId: UUID): Charge?

  // 00.04 tenant sweep: null-safe hospital scope (U7/D9) — admin callers pass null.
  @Query("select c from Charge c where (:hid is null or c.hospitalId = :hid) and c.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Charge>

  @Query("select c from Charge c where (:hid is null or c.hospitalId = :hid) and c.visitId = :visitId")
  fun findByVisitId(
    @Param("hid") hospitalId: UUID?,
    @Param("visitId") visitId: UUID,
  ): List<Charge>

  @Query("select c from Charge c where (:hid is null or c.hospitalId = :hid) and c.patientId = :patientId order by c.postedAt desc")
  fun findByPatientIdOrderByPostedAtDesc(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Charge>

  @Query("select c from Charge c where (:hid is null or c.hospitalId = :hid) and c.visitId = :visitId order by c.postedAt desc")
  fun findByVisitIdOrderByPostedAtDesc(
    @Param("hid") hospitalId: UUID?,
    @Param("visitId") visitId: UUID,
  ): List<Charge>

  @Query("select c from Charge c where (:hid is null or c.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Charge>
}
