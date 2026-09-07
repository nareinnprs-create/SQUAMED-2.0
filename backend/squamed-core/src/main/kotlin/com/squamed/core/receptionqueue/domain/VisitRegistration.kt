package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "visit_registration", schema = "reception")
class VisitRegistration(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "appointment_id")
  var appointmentId: UUID? = null,
  @Column(name = "visit_number", nullable = false, length = 50)
  var visitNumber: String,
  @Column(name = "visit_type", nullable = false, length = 30)
  var visitType: String = "outpatient",
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "doctor_id")
  var doctorId: UUID? = null,
  @Column(name = "specialty_id")
  var specialtyId: UUID? = null,
  @Column(name = "reason")
  var reason: String? = null,
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "routine",
  @Column(name = "source", nullable = false, length = 30)
  var source: String = "walk-in",
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "registered",
  @Column(name = "insurance_verification", length = 30)
  var insuranceVerification: String? = null,
  @Column(name = "insurance_verified_at")
  var insuranceVerifiedAt: Instant? = null,
  @Column(name = "registration_started_at", nullable = false)
  var registrationStartedAt: Instant,
  @Column(name = "registered_at")
  var registeredAt: Instant? = null,
) : BaseEntity()
