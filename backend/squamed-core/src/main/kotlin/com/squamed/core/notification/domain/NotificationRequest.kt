package com.squamed.core.notification.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * Outbound dispatch ledger (chunk-00 item 12, `notification_request` in the chunk schema list):
 * one row per send attempt with its gate outcome (consent/DND/provider).
 */
@Entity
@Table(name = "notification_request", schema = "notifications")
class NotificationRequest(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "patient_id")
  var patientId: UUID? = null,
  @Column(name = "recipient_id")
  var recipientId: UUID? = null,
  @Column(name = "recipient_address", length = 255)
  var recipientAddress: String? = null,
  @Column(name = "channel", nullable = false, length = 20)
  var channel: String,
  @Column(name = "type", nullable = false, length = 50)
  var type: String,
  @Column(name = "title", nullable = false, length = 255)
  var title: String,
  @Column(name = "body", columnDefinition = "text")
  var body: String? = null,
  /** SENT | FAILED | BLOCKED_NO_CONSENT | DEFERRED_DND | SKIPPED_NO_PROVIDER | SKIPPED_NO_SMTP_CONFIG. */
  @Column(name = "status", nullable = false, length = 30)
  var status: String,
  @Column(name = "provider_ref", length = 255)
  var providerRef: String? = null,
  @Column(name = "error_message", columnDefinition = "text")
  var errorMessage: String? = null,
  @Column(name = "requested_by")
  var requestedBy: UUID? = null,
) : BaseEntity() {
  companion object {
    val OUTBOUND_CHANNELS: Set<String> = setOf("sms", "whatsapp", "email", "push", "in_app")
  }
}
