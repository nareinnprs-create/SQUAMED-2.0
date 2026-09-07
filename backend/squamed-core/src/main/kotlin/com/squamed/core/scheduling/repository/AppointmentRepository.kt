package com.squamed.core.scheduling.repository

import com.squamed.core.scheduling.domain.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface AppointmentRepository : JpaRepository<Appointment, UUID> {
  fun findByPatientId(patientId: UUID): List<Appointment>

  fun findByDoctorId(doctorId: UUID): List<Appointment>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Appointment?

  fun findByStatus(status: String): List<Appointment>

  fun findByScheduledStartBetween(
    start: Instant,
    end: Instant,
  ): List<Appointment>

  fun countByPatientIdAndStatus(
    patientId: UUID,
    status: String,
  ): Long

  fun findAllByOrderByScheduledStartAsc(): List<Appointment>
}
