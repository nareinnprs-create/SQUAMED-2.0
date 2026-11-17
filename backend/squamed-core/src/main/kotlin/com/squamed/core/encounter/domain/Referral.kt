package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** 01.04 referral raised from an encounter (activates the existing clinical:manage:referral). */
@Entity
@Table(name = "referral", schema = "clinical")
class Referral(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "encounter_id", nullable = false)
  var encounterId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "from_doctor_id")
  var fromDoctorId: UUID? = null,
  @Column(name = "to_department_id")
  var toDepartmentId: UUID? = null,
  @Column(name = "to_specialty", length = 80)
  var toSpecialty: String? = null,
  @Column(name = "target_facility", length = 200)
  var targetFacility: String? = null,
  @Column(name = "reason", nullable = false, columnDefinition = "text")
  var reason: String,
  @Column(name = "urgency", nullable = false, length = 20)
  var urgency: String = "routine",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "pending",
  @Column(name = "notes", columnDefinition = "text")
  var notes: String? = null,
) : BaseEntity()
