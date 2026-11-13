package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "address", schema = "patient")
class Address(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "type", nullable = false, length = 30)
  var type: String = "home",
  @Column(name = "line1", length = 300)
  var line1: String? = null,
  @Column(name = "line2", length = 300)
  var line2: String? = null,
  @Column(name = "city", length = 100)
  var city: String? = null,
  @Column(name = "state_province", length = 100)
  var stateProvince: String? = null,
  @Column(name = "country", length = 100)
  var country: String? = null,
  @Column(name = "postal_code", length = 20)
  var postalCode: String? = null,
  @Column(name = "is_primary", nullable = false)
  var isPrimary: Boolean = true,
) : BaseEntity()
