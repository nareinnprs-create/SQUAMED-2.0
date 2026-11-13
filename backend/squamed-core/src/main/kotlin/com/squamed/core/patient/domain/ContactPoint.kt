package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "contact_point", schema = "patient")
class ContactPoint(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "type", nullable = false, length = 30)
  var type: String,
  @Column(name = "value", nullable = false, length = 150)
  var value: String,
  @Column(name = "purpose", length = 50)
  var purpose: String? = null,
  @Column(name = "is_primary", nullable = false)
  var isPrimary: Boolean = false,
) : BaseEntity()
