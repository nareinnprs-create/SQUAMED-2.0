package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "price_list_item", schema = "master_data")
class PriceListItem(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "price_list_id", nullable = false)
  var priceListId: UUID,
  @Column(name = "charge_item_id", nullable = false)
  var chargeItemId: UUID,
  @Column(name = "price_qar", nullable = false, precision = 12, scale = 2)
  var priceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
