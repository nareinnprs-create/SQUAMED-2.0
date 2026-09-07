package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "patient_identifier", schema = "patient")
class PatientIdentifier(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "id_type", nullable = false, length = 30)
  var idType: String,
  @Column(name = "id_value", nullable = false, length = 100)
  var idValue: String,
  @Column(name = "issuing_authority", length = 100)
  var issuingAuthority: String? = null,
  @Column(name = "primary_flag", nullable = false)
  var primaryFlag: Boolean = false,
) : BaseEntity()
