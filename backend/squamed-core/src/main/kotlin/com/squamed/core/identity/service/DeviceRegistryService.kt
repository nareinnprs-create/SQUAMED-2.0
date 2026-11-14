package com.squamed.core.identity.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.identity.domain.DeviceRecord
import com.squamed.core.identity.repository.DeviceRecordRepository
import com.squamed.core.identity.repository.SessionRepository
import com.squamed.core.identity.security.TokenBlacklistService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * 00.03 hardening (chunk-00 item 4): device sessions. Every login registers or touches a
 * device derived from the User-Agent fingerprint; users list and revoke their devices,
 * revocation kills the device's live sessions (row + Redis blacklist).
 */
@Service
class DeviceRegistryService(
  private val deviceRepository: DeviceRecordRepository,
  private val sessionRepository: SessionRepository,
  private val tokenBlacklistService: TokenBlacklistService,
  private val eventPublisher: DomainEventPublisher,
  @Value("\${squamed.security.jwt.refresh-token-ttl-days}") private val refreshTtlDays: Long,
) {
  data class DeviceView(
    val id: UUID,
    val label: String?,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val revoked: Boolean,
  )

  fun fingerprint(userAgent: String?): String =
    if (userAgent.isNullOrBlank()) {
      UNKNOWN_FINGERPRINT
    } else {
      MessageDigest
        .getInstance("SHA-256")
        .digest(userAgent.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    }

  @Transactional
  fun registerOrTouch(
    userId: UUID,
    fingerprint: String,
    label: String?,
    hospitalId: UUID?,
  ): DeviceRecord {
    val existing = deviceRepository.findByUserIdAndFingerprint(userId, fingerprint).orElse(null)
    if (existing != null) {
      existing.lastSeenAt = Instant.now()
      if (existing.revokedAt != null) {
        // A previously revoked device logging in again is a fresh, user-visible session grant.
        existing.revokedAt = null
      }
      existing.hospitalId = hospitalId ?: existing.hospitalId
      return deviceRepository.save(existing)
    }
    val device =
      DeviceRecord(
        userId = userId,
        fingerprint = fingerprint,
        label = label,
        hospitalId = hospitalId,
      )
    val saved = deviceRepository.save(device)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "device.registered",
        occurredAt = Instant.now(),
        source = "identity-device-registry",
        entityType = "device",
        entityId = saved.id,
        hospitalId = hospitalId,
        payload = mapOf("userId" to userId.toString()),
      ),
    )
    return saved
  }

  @Transactional(readOnly = true)
  fun listDevices(userId: UUID): List<DeviceView> =
    deviceRepository.findByUserIdAndRevokedAtIsNull(userId).map {
      DeviceView(
        id = it.id ?: error("DEVICE_ID_MISSING"),
        label = it.label,
        firstSeenAt = it.firstSeenAt,
        lastSeenAt = it.lastSeenAt,
        revoked = it.isRevoked(),
      )
    }

  @Transactional
  fun revokeDevice(
    userId: UUID,
    deviceId: UUID,
  ) {
    val device = deviceRepository.findById(deviceId).orElse(null)
    if (device == null || device.userId != userId) {
      throw NoSuchElementException("DEVICE_NOT_FOUND")
    }
    if (device.revokedAt == null) {
      device.revokedAt = Instant.now()
      deviceRepository.save(device)
      sessionRepository.findByDeviceIdAndRevokedAtIsNull(deviceId).forEach { session ->
        session.revokedAt = Instant.now()
        sessionRepository.save(session)
        tokenBlacklistService.revokeSession(
          session.id ?: error("SESSION_ID_MISSING"),
          session.refreshTokenHash,
          Duration.ofDays(refreshTtlDays),
        )
      }
      eventPublisher.publish(
        DomainEvent(
          eventId = UUID.randomUUID(),
          eventType = "device.revoked",
          occurredAt = Instant.now(),
          source = "identity-device-registry",
          entityType = "device",
          entityId = deviceId,
          hospitalId = device.hospitalId,
          payload = mapOf("userId" to userId.toString()),
        ),
      )
      logger.info { "Revoked device $deviceId for user $userId (sessions killed)" }
    }
  }

  private companion object {
    val UNKNOWN_FINGERPRINT: String = "unknown"
  }
}
