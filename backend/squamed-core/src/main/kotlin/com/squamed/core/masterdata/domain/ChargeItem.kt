package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "charge_item", schema = "master_data")
class ChargeItem(
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "price_qar", nullable = false, precision = 12, scale = 2)
  var priceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
  var taxRate: BigDecimal = BigDecimal.ZERO,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
