package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "queue_entry", schema = "queue")
class QueueEntry(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "queue_type", nullable = false, length = 30)
  var queueType: String,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "service_point_id", length = 50)
  var servicePointId: String? = null,
  @Column(name = "service_point_name", length = 150)
  var servicePointName: String? = null,
  @Column(name = "visit_id")
  var visitId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "appointment_id")
  var appointmentId: UUID? = null,
  @Column(name = "token_number", nullable = false, length = 20)
  var tokenNumber: String,
  @Column(name = "patient_name", length = 200)
  var patientName: String? = null,
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "routine",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "waiting",
  @Column(name = "served_by")
  var servedBy: UUID? = null,
  @Column(name = "called_at")
  var calledAt: Instant? = null,
  @Column(name = "started_at")
  var startedAt: Instant? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
  @Column(name = "skipped_at")
  var skippedAt: Instant? = null,
  @Column(name = "no_show_at")
  var noShowAt: Instant? = null,
  @Column(name = "estimated_wait_minutes")
  var estimatedWaitMinutes: Int? = null,
  @Column(name = "notes")
  var notes: String? = null,
  @Column(name = "queue_date", nullable = false)
  var queueDate: LocalDate,
) : BaseEntity()
