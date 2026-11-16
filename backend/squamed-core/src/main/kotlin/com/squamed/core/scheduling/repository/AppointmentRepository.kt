package com.squamed.core.scheduling.repository

import com.squamed.core.scheduling.domain.Appointment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AppointmentRepository : JpaRepository<Appointment, UUID> {
  // 00.04 tenant sweep: unfiltered variant kept for cross-module system callers (patient360).
  fun findByPatientId(patientId: UUID): List<Appointment>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Appointment?

  fun countByPatientIdAndStatus(
    patientId: UUID,
    status: String,
  ): Long

  // 01.01 no-show policy: patient no-shows inside the rolling policy window.
  fun countByPatientIdAndStatusAndNoShowAtAfter(
    patientId: UUID,
    status: String,
    noShowAt: Instant,
  ): Long

  /** 01.01 cursor page (00.02 style guide): keyset over (scheduledStart, id), hid-scoped, optional filters. */
  @Query(
    """
    select a from Appointment a
    where (:hid is null or a.hospitalId = :hid)
      and (:patientId is null or a.patientId = :patientId)
      and (:doctorId is null or a.doctorId = :doctorId)
      and (:status is null or a.status = :status)
      and (a.scheduledStart > :cursorAt or (a.scheduledStart = :cursorAt and a.id > :cursorId))
    order by a.scheduledStart asc, a.id asc
    """,
  )
  fun pageAfter(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID?,
    @Param("doctorId") doctorId: UUID?,
    @Param("status") status: String?,
    @Param("cursorAt") cursorAt: Instant,
    @Param("cursorId") cursorId: UUID,
    pageable: Pageable,
  ): List<Appointment>

  // 01.01: first keyset page (cursor page start).
  @Query(
    """
    select a from Appointment a
    where (:hid is null or a.hospitalId = :hid)
      and (:patientId is null or a.patientId = :patientId)
      and (:doctorId is null or a.doctorId = :doctorId)
      and (:status is null or a.status = :status)
    order by a.scheduledStart asc, a.id asc
    """,
  )
  fun pageFirst(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID?,
    @Param("doctorId") doctorId: UUID?,
    @Param("status") status: String?,
    pageable: Pageable,
  ): List<Appointment>
}
