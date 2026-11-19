package com.squamed.core.medsafety.repository

import com.squamed.core.medsafety.domain.DoseRule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DoseRuleRepository : JpaRepository<DoseRule, UUID> {
  fun findByIsActiveTrue(): List<DoseRule>
}
