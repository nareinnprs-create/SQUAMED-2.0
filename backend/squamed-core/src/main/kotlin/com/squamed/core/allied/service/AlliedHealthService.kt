package com.squamed.core.allied.service

import com.squamed.core.allied.domain.TherapyReferral
import com.squamed.core.allied.domain.TherapySession
import com.squamed.core.allied.repository.TherapyReferralRepository
import com.squamed.core.allied.repository.TherapySessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ReferralDetailView(
  val referral: TherapyReferral,
  val sessions: List<TherapySession>,
)

// 07.07 allied health: referral -> schedule -> in progress -> signed sessions -> completion.
@Service
class AlliedHealthService(
  private val referralRepository: TherapyReferralRepository,
  private val sessionRepository: TherapySessionRepository,
) {
  companion object {
    val DISCIPLINES = setOf("PHYSIO", "DIET", "OT", "SPEECH", "REHAB", "SOCIAL", "PSYCH")
  }

  @Transactional
  fun refer(
    hospitalId: UUID,
    patientId: UUID,
    discipline: String,
    reason: String,
    therapistId: UUID?,
    carePlanId: UUID?,
    sessionsPlanned: Int,
    actorId: UUID?,
  ): TherapyReferral {
    val type = discipline.trim().uppercase()
    if (type !in DISCIPLINES) throw IllegalArgumentException("ALLIED_DISCIPLINE_INVALID")
    if (reason.isBlank()) throw IllegalArgumentException("ALLIED_REASON_REQUIRED")
    if (sessionsPlanned <= 0) throw IllegalArgumentException("ALLIED_SESSIONS_INVALID")
    return referralRepository.save(
      TherapyReferral(
        hospitalId = hospitalId,
        patientId = patientId,
        discipline = type,
        reason = reason.trim(),
        therapistId = therapistId,
        carePlanId = carePlanId,
        sessionsPlanned = sessionsPlanned,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun schedule(
    id: UUID,
    hospitalId: UUID,
    scheduledAt: Instant,
    therapistId: UUID,
    appointmentId: UUID?,
    actorId: UUID?,
  ): TherapyReferral {
    val referral = find(id, hospitalId)
    if (referral.status != "REFERRED") throw IllegalStateException("ALLIED_ALREADY_SCHEDULED")
    referral.scheduledAt = scheduledAt
    referral.therapistId = therapistId
    referral.appointmentId = appointmentId
    referral.status = "SCHEDULED"
    referral.updatedBy = actorId
    return referralRepository.save(referral)
  }

  @Transactional
  fun start(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): TherapyReferral {
    val referral = find(id, hospitalId)
    if (referral.status != "SCHEDULED") throw IllegalStateException("ALLIED_NOT_SCHEDULED")
    referral.status = "IN_PROGRESS"
    referral.updatedBy = actorId
    return referralRepository.save(referral)
  }

  @Transactional
  fun addSession(
    id: UUID,
    hospitalId: UUID,
    sessionAt: Instant,
    durationMinutes: Int,
    notes: String?,
    outcome: String?,
    actorId: UUID?,
  ): TherapySession {
    val referral = find(id, hospitalId)
    if (referral.status != "IN_PROGRESS") throw IllegalStateException("ALLIED_NOT_IN_PROGRESS")
    if (durationMinutes <= 0) throw IllegalArgumentException("ALLIED_DURATION_INVALID")
    return sessionRepository.save(
      TherapySession(
        hospitalId = hospitalId,
        referralId = checkNotNull(referral.id),
        sessionAt = sessionAt,
        durationMinutes = durationMinutes,
        notes = notes?.trim(),
        outcome = outcome?.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun signSession(
    referralId: UUID,
    sessionId: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): TherapySession {
    find(referralId, hospitalId)
    val session = sessionRepository.findByIdAndHospitalId(sessionId, hospitalId) ?: throw IllegalArgumentException("ALLIED_SESSION_NOT_FOUND")
    if (session.referralId != referralId) throw IllegalArgumentException("ALLIED_SESSION_NOT_FOUND")
    if (session.status == "SIGNED") throw IllegalStateException("ALLIED_SESSION_ALREADY_SIGNED")
    session.status = "SIGNED"
    session.signedBy = actorId
    session.signedAt = Instant.now()
    session.updatedBy = actorId
    return sessionRepository.save(session)
  }

  @Transactional
  fun complete(
    id: UUID,
    hospitalId: UUID,
    outcome: String,
    actorId: UUID?,
  ): TherapyReferral {
    if (outcome.isBlank()) throw IllegalArgumentException("ALLIED_OUTCOME_REQUIRED")
    val referral = find(id, hospitalId)
    if (referral.status == "COMPLETED") throw IllegalStateException("ALLIED_ALREADY_COMPLETED")
    if (referral.status != "IN_PROGRESS") throw IllegalStateException("ALLIED_NOT_IN_PROGRESS")
    if (sessionRepository.countByReferralIdAndStatus(checkNotNull(referral.id), "SIGNED") == 0L) {
      throw IllegalArgumentException("ALLIED_NO_SIGNED_SESSION")
    }
    referral.outcome = outcome.trim()
    referral.status = "COMPLETED"
    referral.completedAt = Instant.now()
    referral.updatedBy = actorId
    return referralRepository.save(referral)
  }

  @Transactional(readOnly = true)
  fun referrals(hospitalId: UUID): List<TherapyReferral> = referralRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional(readOnly = true)
  fun detail(
    id: UUID,
    hospitalId: UUID,
  ): ReferralDetailView {
    val referral = find(id, hospitalId)
    return ReferralDetailView(referral, sessionRepository.findByReferralIdOrderBySessionAtAsc(checkNotNull(referral.id)))
  }

  private fun find(
    id: UUID,
    hospitalId: UUID,
  ): TherapyReferral = referralRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("ALLIED_REFERRAL_NOT_FOUND")
}
