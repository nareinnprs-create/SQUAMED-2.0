package com.squamed.core.surgery.repository

import com.squamed.core.surgery.domain.ImplantRecord
import com.squamed.core.surgery.domain.InstrumentTracking
import com.squamed.core.surgery.domain.IntraOpRecord
import com.squamed.core.surgery.domain.OtAllocation
import com.squamed.core.surgery.domain.OtRoom
import com.squamed.core.surgery.domain.PostOpRecord
import com.squamed.core.surgery.domain.PreOpChecklist
import com.squamed.core.surgery.domain.SurgerySchedule
import com.squamed.core.surgery.domain.SurgicalTeam
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SurgeryScheduleRepository : JpaRepository<SurgerySchedule, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U12) — admin callers pass null.
  @Query("select s from SurgerySchedule s where (:hid is null or s.hospitalId = :hid) and s.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<SurgerySchedule>

  @Query("select s from SurgerySchedule s where (:hid is null or s.hospitalId = :hid) and s.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<SurgerySchedule>

  @Query("select s from SurgerySchedule s where (:hid is null or s.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<SurgerySchedule>
}

interface OtRoomRepository : JpaRepository<OtRoom, UUID> {
  fun findByHospitalId(hospitalId: UUID): List<OtRoom>
}

interface OtAllocationRepository : JpaRepository<OtAllocation, UUID> {
  fun findAllByOtRoomId(otRoomId: UUID): List<OtAllocation>

  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): OtAllocation?

  fun existsByOtRoomIdAndStartTimeLessThanAndEndTimeGreaterThan(
    otRoomId: UUID,
    endTime: Instant,
    startTime: Instant,
  ): Boolean
}

interface SurgicalTeamRepository : JpaRepository<SurgicalTeam, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<SurgicalTeam>
}

interface InstrumentTrackingRepository : JpaRepository<InstrumentTracking, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<InstrumentTracking>
}

interface ImplantRecordRepository : JpaRepository<ImplantRecord, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<ImplantRecord>
}

interface PreOpChecklistRepository : JpaRepository<PreOpChecklist, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<PreOpChecklist>

  // 04.06 WHO staged checklist ordered by creation.
  fun findBySurgeryScheduleIdOrderByCreatedAtAsc(surgeryScheduleId: UUID): List<PreOpChecklist>
}

interface IntraOpRecordRepository : JpaRepository<IntraOpRecord, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<IntraOpRecord>
}

interface PostOpRecordRepository : JpaRepository<PostOpRecord, UUID> {
  fun findFirstBySurgeryScheduleIdOrderByRecordedAtDesc(surgeryScheduleId: UUID): PostOpRecord?
}

// 04.06 consumables.
interface SurgicalConsumableRepository :
  org.springframework.data.jpa.repository.JpaRepository<com.squamed.core.surgery.domain.SurgicalConsumable, java.util.UUID> {
  fun findBySurgeryScheduleIdOrderByRecordedAtDesc(surgeryScheduleId: java.util.UUID): List<com.squamed.core.surgery.domain.SurgicalConsumable>
}
