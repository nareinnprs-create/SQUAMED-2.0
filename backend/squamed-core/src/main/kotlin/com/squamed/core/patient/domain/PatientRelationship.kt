package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "patient_relationship", schema = "patient")
class PatientRelationship(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "related_patient_id", nullable = false)
  var relatedPatientId: UUID,
  @Column(name = "relationship_type", nullable = false, length = 50)
  var relationshipType: String,
  @Column(name = "is_primary_guardian", nullable = false)
  var isPrimaryGuardian: Boolean = false,
  @Column(name = "notes", length = 500)
  var notes: String? = null,
) : BaseEntity()
