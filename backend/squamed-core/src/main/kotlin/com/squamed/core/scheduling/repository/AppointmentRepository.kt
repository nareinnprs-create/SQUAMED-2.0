package com.squamed.core.scheduling.repository

import com.squamed.core.scheduling.domain.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AppointmentRepository : JpaRepository<Appointment, UUID> {
  // 00.04 tenant sweep: unfiltered variant kept for cross-module system callers (patient360).
  fun findByPatientId(patientId: UUID): List<Appointment>

  // 00.04 tenant sweep: null-safe hospital scope (U4) — admin callers pass null.
  @Query("select a from Appointment a where (:hid is null or a.hospitalId = :hid) and a.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Appointment>

  // 00.04 tenant sweep: null-safe hospital scope (U4).
  @Query("select a from Appointment a where (:hid is null or a.hospitalId = :hid) and a.doctorId = :doctorId")
  fun findByDoctorId(
    @Param("hid") hospitalId: UUID?,
    @Param("doctorId") doctorId: UUID,
  ): List<Appointment>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Appointment?

  // 00.04 tenant sweep: null-safe hospital scope (U4).
  @Query("select a from Appointment a where (:hid is null or a.hospitalId = :hid) and a.status = :status")
  fun findByStatus(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String,
  ): List<Appointment>

  // 00.04 tenant sweep: null-safe hospital scope (U4).
  @Query(
    "select a from Appointment a where (:hid is null or a.hospitalId = :hid) and a.scheduledStart between :start and :end",
  )
  fun findByScheduledStartBetween(
    @Param("hid") hospitalId: UUID?,
    @Param("start") start: Instant,
    @Param("end") end: Instant,
  ): List<Appointment>

  fun countByPatientIdAndStatus(
    patientId: UUID,
    status: String,
  ): Long

  // 00.04 tenant sweep: null-safe hospital scope (U4).
  @Query("select a from Appointment a where (:hid is null or a.hospitalId = :hid) order by a.scheduledStart asc")
  fun findAllByOrderByScheduledStartAsc(
    @Param("hid") hospitalId: UUID?,
  ): List<Appointment>
}
