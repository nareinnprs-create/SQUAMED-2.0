package com.squamed.core.scheduling.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Materialized bookable slot (01.01): generated lazily from [ScheduleTemplate]s for a requested
 * date window; unique per (hospital, doctor, date, start). bookedCount counts active bookings;
 * effective capacity ceiling = capacity * overbookFactor from the hospital `scheduling` config doc.
 */
@Entity
@Table(name = "session_slot", schema = "scheduling")
class SessionSlot(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID? = null,
  @Column(name = "template_id", nullable = false)
  var templateId: UUID,
  @Column(name = "doctor_id", nullable = false)
  var doctorId: UUID,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "session_type", nullable = false, length = 20)
  var sessionType: String = "in_person",
  @Column(name = "room_id")
  var roomId: UUID? = null,
  @Column(name = "slot_date", nullable = false)
  var slotDate: LocalDate,
  @Column(name = "start_time", nullable = false)
  var startTime: LocalTime,
  @Column(name = "end_time", nullable = false)
  var endTime: LocalTime,
  @Column(name = "capacity", nullable = false)
  var capacity: Int,
  @Column(name = "booked_count", nullable = false)
  var bookedCount: Int = 0,
  @Column(name = "no_show_count", nullable = false)
  var noShowCount: Int = 0,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "open",
) : BaseEntity()
