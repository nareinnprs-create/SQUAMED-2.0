package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.TariffItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TariffItemRepository : JpaRepository<TariffItem, UUID> {
  fun findByCode(code: String): List<TariffItem>

  fun findByIsActiveTrue(): List<TariffItem>

  fun findByNameContainingIgnoreCase(name: String): List<TariffItem>
}
