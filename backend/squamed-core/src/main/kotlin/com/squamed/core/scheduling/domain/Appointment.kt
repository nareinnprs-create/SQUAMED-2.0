package com.squamed.core.scheduling.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "appointment", schema = "scheduling")
class Appointment(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "specialty_id")
  var specialtyId: UUID? = null,
  @Column(name = "doctor_id", nullable = false)
  var doctorId: UUID,
  @Column(name = "appointment_type", nullable = false, length = 30)
  var appointmentType: String,
  @Column(name = "scheduled_start", nullable = false)
  var scheduledStart: Instant,
  @Column(name = "scheduled_end", nullable = false)
  var scheduledEnd: Instant,
  @Column(name = "duration_minutes", nullable = false)
  var durationMinutes: Int = 15,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "scheduled",
  @Column(name = "reason")
  var reason: String? = null,
  @Column(name = "notes")
  var notes: String? = null,
  @Column(name = "visit_type", nullable = false, length = 30)
  var visitType: String = "outpatient",
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "routine",
  @Column(name = "telemedicine", nullable = false)
  var telemedicine: Boolean = false,
  @Column(name = "room_id")
  var roomId: UUID? = null,
  // 01.01: the bookable session_slot this appointment consumes (null = legacy direct-time booking)
  @Column(name = "slot_id")
  var slotId: UUID? = null,
  @Column(name = "external_ref", length = 100)
  var externalRef: String? = null,
  @Column(name = "source", nullable = false, length = 30)
  var source: String = "clinic",
  @Column(name = "checked_in_at")
  var checkedInAt: Instant? = null,
  @Column(name = "confirmed_at")
  var confirmedAt: Instant? = null,
  @Column(name = "cancelled_reason", length = 100)
  var cancelledReason: String? = null,
  @Column(name = "cancelled_at")
  var cancelledAt: Instant? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
  @Column(name = "no_show_at")
  var noShowAt: Instant? = null,
  @Column(name = "cancellation_fee", precision = 10, scale = 2)
  var cancellationFee: BigDecimal? = null,
) : BaseEntity()
