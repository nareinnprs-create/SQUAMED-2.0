package com.squamed.core.consent.service

import com.squamed.core.consent.api.ChannelOptInDto
import com.squamed.core.consent.api.ConsentCaptureDto
import com.squamed.core.consent.api.ConsentView
import com.squamed.core.consent.domain.Consent
import com.squamed.core.consent.domain.ConsentChannelOptIn
import com.squamed.core.consent.repository.ConsentChannelOptInRepository
import com.squamed.core.consent.repository.ConsentRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ConsentService(
  private val consentRepository: ConsentRepository,
  private val optInRepository: ConsentChannelOptInRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun capture(
    dto: ConsentCaptureDto,
    actorId: UUID,
  ): ConsentView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("CONSENT_PATIENT_REQUIRED")
    val purpose = dto.purpose?.lowercase() ?: throw IllegalArgumentException("CONSENT_PURPOSE_REQUIRED")
    if (purpose !in Consent.PURPOSES) {
      throw IllegalArgumentException("CONSENT_INVALID_PURPOSE: $purpose")
    }
    val consent =
      consentRepository
        .save(
          Consent(
            hospitalId = dto.hospitalId,
            patientId = patientId,
            purpose = purpose,
            status = "granted",
            scope = dto.scope,
            signatureData = dto.signatureData,
            signedByName = dto.signedByName,
            signedAt = Instant.now(),
            expiresAt = dto.expiresAt,
          ),
        ).also { it.createdBy = actorId }
    val consentId = consent.id ?: throw IllegalStateException("CONSENT_ID_MISSING")
    (dto.channelOptIns ?: emptyList())
      .filter { it.channel != null }
      .distinctBy { it.channel }
      .forEach { optIn ->
        val channel = optIn.channel!!.lowercase()
        require(channel in ConsentChannelOptIn.CHANNELS) { "CONSENT_INVALID_CHANNEL: $channel" }
        optInRepository.save(
          ConsentChannelOptIn(
            hospitalId = dto.hospitalId,
            consentId = consentId,
            channel = channel,
            optedIn = optIn.optedIn ?: true,
          ),
        )
      }
    publish("consent.captured", consent, mapOf("purpose" to purpose, "patientId" to patientId.toString()), consent.signedByName)
    return view(consentId)
  }

  @Transactional(readOnly = true)
  fun listForPatient(
    patientId: UUID,
    hospitalId: UUID?,
  ): List<ConsentView> {
    val now = Instant.now()
    return consentRepository
      .findByPatient(patientId, hospitalId)
      .map { ConsentView.from(it, optInRepository.findByConsentId(it.id!!), now) }
  }

  @Transactional(readOnly = true)
  fun getConsent(
    id: UUID,
    hospitalId: UUID?,
  ): ConsentView {
    val consent = resolveConsent(id, hospitalId)
    return ConsentView.from(consent, optInRepository.findByConsentId(id), Instant.now())
  }

  @Transactional
  fun withdraw(
    id: UUID,
    actorId: UUID,
    reason: String?,
    hospitalId: UUID?,
  ): ConsentView {
    val consent = resolveConsent(id, hospitalId)
    if (Consent.effectiveStatus(consent.status, consent.expiresAt, consent.withdrawnAt, Instant.now()) != "granted") {
      throw IllegalArgumentException("CONSENT_NOT_ACTIVE")
    }
    consent.status = "withdrawn"
    consent.withdrawnAt = Instant.now()
    consent.withdrawnBy = actorId
    consent.updatedBy = actorId
    consentRepository.save(consent)
    publish(
      "consent.withdrawn",
      consent,
      mapOf("purpose" to consent.purpose, "patientId" to consent.patientId.toString(), "reason" to (reason ?: "WITHDRAWN")),
      consent.signedByName,
    )
    return view(id)
  }

  /**
   * Per-channel consent check (chunk item 12): the patient holds an active COMMUNICATIONS consent
   * (granted, not withdrawn, not expired) with that channel opted in.
   */
  @Transactional(readOnly = true)
  fun channelAllowed(
    patientId: UUID,
    channel: String,
    hospitalId: UUID?,
  ): Boolean {
    val now = Instant.now()
    return consentRepository
      .findByPatient(patientId, hospitalId)
      .filter { Consent.effectiveStatus(it.status, it.expiresAt, it.withdrawnAt, now) == "granted" }
      .any { consent ->
        consent.purpose == "communications" &&
          optInRepository.findByConsentId(consent.id!!).any { it.channel == channel && it.optedIn }
      }
  }

  private fun resolveConsent(
    id: UUID,
    hospitalId: UUID?,
  ): Consent =
    if (hospitalId == null) {
      consentRepository.findById(id).orElseThrow { IllegalArgumentException("CONSENT_NOT_FOUND") }
    } else {
      val consent = consentRepository.findById(id).orElse(null)
      if (consent == null || consent.hospitalId != hospitalId) throw IllegalArgumentException("CONSENT_NOT_FOUND") else consent
    }

  private fun view(id: UUID): ConsentView {
    val consent = consentRepository.findById(id).orElseThrow { IllegalStateException("CONSENT_ID_MISSING") }
    return ConsentView.from(consent, optInRepository.findByConsentId(id), Instant.now())
  }

  private fun publish(
    eventType: String,
    consent: Consent,
    payload: Map<String, Any?>,
    signedByName: String?,
  ) {
    // Fan-out to the capturing clinician so the fabric records the consent journey in-app too.
    val recipients = listOfNotNull(consent.createdBy, consent.withdrawnBy).distinct()
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "consent-service",
        entityType = "consent",
        entityId = consent.id ?: throw IllegalStateException("CONSENT_ID_MISSING"),
        hospitalId = consent.hospitalId,
        payload = payload + mapOf("signedBy" to signedByName),
        recipientIds = recipients,
      ),
    )
  }
}
