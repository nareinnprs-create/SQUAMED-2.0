package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "invoice_line", schema = "billing")
class InvoiceLine(
  @Column(name = "invoice_id", nullable = false)
  var invoiceId: UUID,
  @Column(name = "charge_id")
  var chargeId: UUID? = null,
  @Column(name = "description", nullable = false, length = 255)
  var description: String,
  @Column(name = "quantity", nullable = false)
  var quantity: Int = 1,
  @Column(name = "unit_price_qar", nullable = false, precision = 12, scale = 2)
  var unitPriceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "total_qar", nullable = false, precision = 12, scale = 2)
  var totalQar: BigDecimal = BigDecimal.ZERO,
  // 03.02: captured base price + the rate card that repriced the line (null = charge price).
  @Column(name = "base_price_qar", precision = 12, scale = 2)
  var basePriceQar: BigDecimal? = null,
  @Column(name = "rate_card_id")
  var rateCardId: UUID? = null,
) : BaseEntity()
