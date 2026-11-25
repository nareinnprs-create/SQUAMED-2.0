package com.squamed.core.allied.repository

import com.squamed.core.allied.domain.TherapyReferral
import com.squamed.core.allied.domain.TherapySession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TherapyReferralRepository : JpaRepository<TherapyReferral, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): TherapyReferral?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<TherapyReferral>
}

interface TherapySessionRepository : JpaRepository<TherapySession, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): TherapySession?

  fun findByReferralIdOrderBySessionAtAsc(referralId: UUID): List<TherapySession>

  fun countByReferralIdAndStatus(
    referralId: UUID,
    status: String,
  ): Long
}
