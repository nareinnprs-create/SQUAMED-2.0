package com.squamed.core.consent.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** Per-channel opt-in bound to a consent record (chunk-00 item 12 per-channel consent check). */
@Entity
@Table(name = "consent_channel_optin", schema = "consents")
class ConsentChannelOptIn(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "consent_id", nullable = false)
  var consentId: UUID,
  @Column(name = "channel", nullable = false, length = 20)
  var channel: String,
  @Column(name = "opted_in", nullable = false)
  var optedIn: Boolean = true,
) : BaseEntity() {
  companion object {
    /** Outbound channels the consent gate covers (in_app is internal and never consent-gated). */
    val CHANNELS: Set<String> = setOf("sms", "whatsapp", "email", "push")
  }
}
