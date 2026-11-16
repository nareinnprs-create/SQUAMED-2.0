package com.squamed.core.scheduling.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

/**
 * Appointment waitlist (01.01): patients waiting for a free slot with a given doctor/department.
 * status waiting|booked|cancelled; promote books the entry into a concrete slot.
 */
@Entity
@Table(name = "waitlist_entry", schema = "scheduling")
class WaitlistEntry(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "doctor_id")
  var doctorId: UUID? = null,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "routine",
  @Column(name = "desired_date")
  var desiredDate: LocalDate? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "waiting",
  @Column(name = "booked_appointment_id")
  var bookedAppointmentId: UUID? = null,
  @Column(name = "notes", length = 500)
  var notes: String? = null,
) : BaseEntity()
