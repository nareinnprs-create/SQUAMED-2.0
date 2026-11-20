package com.squamed.core.medsafety.repository

import com.squamed.core.medsafety.domain.InteractionRule
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InteractionRuleRepository : JpaRepository<InteractionRule, UUID> {
  fun findByIsActiveTrue(): List<InteractionRule>
}
