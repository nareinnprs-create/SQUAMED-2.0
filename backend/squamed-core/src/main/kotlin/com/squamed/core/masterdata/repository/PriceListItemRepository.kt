package com.squamed.core.masterdata.repository

import com.squamed.core.masterdata.domain.PriceListItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PriceListItemRepository : JpaRepository<PriceListItem, UUID> {
  fun findByPriceListId(priceListId: UUID): List<PriceListItem>

  fun findByPriceListIdAndChargeItemId(
    priceListId: UUID,
    chargeItemId: UUID,
  ): PriceListItem?
}
