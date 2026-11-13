package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "tariff_item", schema = "billing")
class TariffItem(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "price_qar", nullable = false, precision = 12, scale = 2)
  var priceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "insurance_applicable", nullable = false)
  var insuranceApplicable: Boolean = true,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
