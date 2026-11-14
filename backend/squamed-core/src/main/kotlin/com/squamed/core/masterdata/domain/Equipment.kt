package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "equipment", schema = "master_data")
class Equipment(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "vendor_id")
  var vendorId: UUID? = null,
  @Column(name = "serial_number", length = 100)
  var serialNumber: String? = null,
  @Column(name = "location", length = 255)
  var location: String? = null,
  @Column(name = "purchase_date")
  var purchaseDate: LocalDate? = null,
  @Column(name = "warranty_until")
  var warrantyUntil: LocalDate? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
