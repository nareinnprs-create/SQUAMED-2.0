package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// 03.02 package header; pricing lives in immutable versions.
@Entity
@Table(name = "billing_package", schema = "billing")
class BillingPackage(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "package_version", schema = "billing")
class PackageVersion(
  @Column(name = "package_id", nullable = false)
  var packageId: UUID,
  @Column(name = "package_version", nullable = false)
  var packageVersion: Int,
  @Column(name = "price_qar", nullable = false, precision = 12, scale = 2)
  var priceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "effective_from")
  var effectiveFrom: LocalDate? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
