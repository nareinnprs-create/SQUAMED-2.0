package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

// 03.02 payer-class rate card: one active price per (hospital, payer class, code).
@Entity
@Table(name = "rate_card", schema = "billing")
class RateCard(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "payer_class", nullable = false, length = 20)
  var payerClass: String,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "price_qar", nullable = false, precision = 12, scale = 2)
  var priceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
