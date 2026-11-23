package com.squamed.core.bloodbank.repository

import com.squamed.core.bloodbank.domain.BloodDonor
import com.squamed.core.bloodbank.domain.BloodGrouping
import com.squamed.core.bloodbank.domain.BloodIssue
import com.squamed.core.bloodbank.domain.BloodReservation
import com.squamed.core.bloodbank.domain.BloodUnit
import com.squamed.core.bloodbank.domain.Crossmatch
import com.squamed.core.bloodbank.domain.TransfusionReaction
import com.squamed.core.bloodbank.domain.TransfusionRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BloodDonorRepository : JpaRepository<BloodDonor, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope — admin callers pass null.
  @Query("select d from BloodDonor d where (:hid is null or d.hospitalId = :hid) and d.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<BloodDonor>

  @Query("select d from BloodDonor d where (:hid is null or d.hospitalId = :hid) and d.bloodGroup = :bloodGroup")
  fun findByBloodGroup(
    @Param("hid") hospitalId: UUID?,
    @Param("bloodGroup") bloodGroup: String,
  ): List<BloodDonor>

  @Query("select d from BloodDonor d where (:hid is null or d.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<BloodDonor>
}

interface BloodUnitRepository : JpaRepository<BloodUnit, UUID> {
  @Query("select u from BloodUnit u where (:hid is null or u.hospitalId = :hid) and u.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<BloodUnit>

  @Query("select u from BloodUnit u where (:hid is null or u.hospitalId = :hid) and u.bloodGroup = :bloodGroup")
  fun findByBloodGroup(
    @Param("hid") hospitalId: UUID?,
    @Param("bloodGroup") bloodGroup: String,
  ): List<BloodUnit>

  @Query("select u from BloodUnit u where (:hid is null or u.hospitalId = :hid) and u.component = :component")
  fun findByComponent(
    @Param("hid") hospitalId: UUID?,
    @Param("component") component: String,
  ): List<BloodUnit>

  @Query("select u from BloodUnit u where (:hid is null or u.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<BloodUnit>
}

interface BloodGroupingRepository : JpaRepository<BloodGrouping, UUID> {
  fun findByUnitId(unitId: UUID): BloodGrouping?
}

interface CrossmatchRepository : JpaRepository<Crossmatch, UUID> {
  @Query("select c from Crossmatch c where (:hid is null or c.hospitalId = :hid) and c.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Crossmatch>

  @Query("select c from Crossmatch c where (:hid is null or c.hospitalId = :hid) and c.unitId = :unitId")
  fun findByUnitId(
    @Param("hid") hospitalId: UUID?,
    @Param("unitId") unitId: UUID,
  ): List<Crossmatch>

  @Query("select c from Crossmatch c where (:hid is null or c.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Crossmatch>
}

interface BloodReservationRepository : JpaRepository<BloodReservation, UUID> {
  fun findByPatientId(patientId: UUID): List<BloodReservation>

  fun findByUnitId(unitId: UUID): List<BloodReservation>
}

interface BloodIssueRepository : JpaRepository<BloodIssue, UUID> {
  @Query("select i from BloodIssue i where (:hid is null or i.hospitalId = :hid) and i.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<BloodIssue>

  fun findByUnitId(unitId: UUID): List<BloodIssue>

  @Query("select i from BloodIssue i where (:hid is null or i.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<BloodIssue>
}

interface TransfusionRecordRepository : JpaRepository<TransfusionRecord, UUID> {
  @Query("select t from TransfusionRecord t where (:hid is null or t.hospitalId = :hid) and t.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<TransfusionRecord>

  @Query("select t from TransfusionRecord t where (:hid is null or t.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<TransfusionRecord>

  // 04.07 traceability.
  fun findByUnitId(unitId: UUID): List<TransfusionRecord>
}

interface TransfusionReactionRepository : JpaRepository<TransfusionReaction, UUID> {
  fun findByTransfusionRecordId(transfusionRecordId: UUID): List<TransfusionReaction>
}
