package com.squamed.core.consent.api

import com.squamed.core.consent.domain.Consent
import com.squamed.core.consent.domain.ConsentChannelOptIn
import java.time.Instant
import java.util.UUID

data class ConsentCaptureDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val purpose: String? = null,
  val scope: String? = null,
  val signatureData: String? = null,
  val signedByName: String? = null,
  val expiresAt: Instant? = null,
  val channelOptIns: List<ChannelOptInDto>? = null,
)

data class ChannelOptInDto(
  val channel: String? = null,
  val optedIn: Boolean? = null,
)

data class ConsentWithdrawDto(
  val reason: String? = null,
)

data class ConsentView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID,
  val purpose: String,
  val status: String,
  val scope: String?,
  val signaturePresent: Boolean,
  val signedByName: String?,
  val signedAt: Instant?,
  val expiresAt: Instant?,
  val withdrawnAt: Instant?,
  val channelOptIns: List<ChannelOptInDto>,
) {
  companion object {
    fun from(
      consent: Consent,
      optIns: List<ConsentChannelOptIn>,
      now: Instant,
    ): ConsentView =
      ConsentView(
        id = consent.id,
        hospitalId = consent.hospitalId,
        patientId = consent.patientId,
        purpose = consent.purpose,
        status = Consent.effectiveStatus(consent.status, consent.expiresAt, consent.withdrawnAt, now),
        scope = consent.scope,
        signaturePresent = !consent.signatureData.isNullOrBlank(),
        signedByName = consent.signedByName,
        signedAt = consent.signedAt,
        expiresAt = consent.expiresAt,
        withdrawnAt = consent.withdrawnAt,
        channelOptIns = optIns.map { ChannelOptInDto(channel = it.channel, optedIn = it.optedIn) },
      )
  }
}
