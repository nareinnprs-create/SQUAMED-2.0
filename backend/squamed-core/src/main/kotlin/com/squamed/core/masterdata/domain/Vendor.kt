package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "vendor", schema = "master_data")
class Vendor(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "contact_person", length = 255)
  var contactPerson: String? = null,
  @Column(name = "phone", length = 50)
  var phone: String? = null,
  @Column(name = "email", length = 255)
  var email: String? = null,
  @Column(name = "address", columnDefinition = "text")
  var address: String? = null,
  @Column(name = "tax_id", length = 100)
  var taxId: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
