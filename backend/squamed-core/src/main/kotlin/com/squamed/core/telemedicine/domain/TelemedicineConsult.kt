package com.squamed.core.telemedicine.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 06.02: a tele-consult. The RMP is an employee record, not a free-text name, so the guideline
// check can actually verify a registration instead of trusting the caller.
@Entity
@Table(name = "consult", schema = "telemedicine")
class TelemedicineConsult(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "doctor_user_id", nullable = false)
  var doctorUserId: UUID,
  @Column(name = "rmp_employee_id", nullable = false)
  var rmpEmployeeId: UUID,
  @Column(name = "consult_type", nullable = false, length = 20)
  var consultType: String,
  @Column(name = "mode", nullable = false, length = 20)
  var mode: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "SCHEDULED",
  @Column(name = "scheduled_at", nullable = false)
  var scheduledAt: Instant,
  @Column(name = "started_at")
  var startedAt: Instant? = null,
  @Column(name = "ended_at")
  var endedAt: Instant? = null,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "meeting_ref", length = 200)
  var meetingRef: String? = null,
  @Column(name = "escape_reason", length = 300)
  var escapeReason: String? = null,
  @Column(name = "notes", length = 1000)
  var notes: String? = null,
) : BaseEntity()
