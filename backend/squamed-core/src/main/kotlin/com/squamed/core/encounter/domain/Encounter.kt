package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "encounter", schema = "clinical")
class Encounter(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "visit_id")
  var visitId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "doctor_id")
  var doctorId: UUID? = null,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "encounter_type", nullable = false, length = 30)
  var encounterType: String = "outpatient",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "in-progress",
  @Column(name = "reason")
  var reason: String? = null,
  @Column(name = "chief_complaint")
  var chiefComplaint: String? = null,
  @Column(name = "started_at")
  var startedAt: Instant? = null,
  @Column(name = "ended_at")
  var endedAt: Instant? = null,
) : BaseEntity()
