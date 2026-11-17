package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.Referral
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReferralRepository : JpaRepository<Referral, UUID> {
  fun findByEncounterIdOrderByCreatedAtDesc(encounterId: UUID): List<Referral>
}
