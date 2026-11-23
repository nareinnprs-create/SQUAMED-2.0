package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "hospital", schema = "hospital_config")
class Hospital(
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "legal_name", length = 300)
  var legalName: String? = null,
  @Column(name = "country", nullable = false, length = 100)
  var country: String,
  @Column(name = "region", length = 100)
  var region: String? = null,
  @Column(name = "city", length = 100)
  var city: String? = null,
  @Column(name = "address_line1", length = 300)
  var addressLine1: String? = null,
  @Column(name = "address_line2", length = 300)
  var addressLine2: String? = null,
  @Column(name = "postal_code", length = 20)
  var postalCode: String? = null,
  @Column(name = "phone", length = 30)
  var phone: String? = null,
  @Column(name = "email", length = 150)
  var email: String? = null,
  @Column(name = "license_number", length = 150)
  var licenseNumber: String? = null,
  @Column(name = "timezone", nullable = false, length = 50)
  var timezone: String = "UTC",
  @Column(name = "currency", nullable = false, length = 10)
  var currency: String = "USD",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
  // 06.01: the hospital group this hospital belongs to (null = standalone).
  @Column(name = "group_id")
  var groupId: UUID? = null,
) : BaseEntity()
