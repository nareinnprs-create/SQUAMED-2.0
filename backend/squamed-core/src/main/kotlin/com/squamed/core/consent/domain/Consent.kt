package com.squamed.core.consent.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Chunk-00 item 11 consent record: purpose-scoped, eSign-ready signature, withdrawal, expiry. */
@Entity
@Table(name = "consent", schema = "consents")
class Consent(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "purpose", nullable = false, length = 30)
  var purpose: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "granted",
  @Column(name = "scope")
  var scope: String? = null,
  /** eSign-ready capture: base64 signature payload (handwriting/image/hash of signed doc). */
  @Column(name = "signature_data", columnDefinition = "text")
  var signatureData: String? = null,
  @Column(name = "signed_by_name", length = 255)
  var signedByName: String? = null,
  @Column(name = "signed_at")
  var signedAt: Instant? = null,
  @Column(name = "expires_at")
  var expiresAt: Instant? = null,
  @Column(name = "withdrawn_at")
  var withdrawnAt: Instant? = null,
  @Column(name = "withdrawn_by")
  var withdrawnBy: UUID? = null,
) : BaseEntity() {
  companion object {
    val PURPOSES: Set<String> = setOf("treatment", "data_sharing", "communications")

    /** Effective status: withdrawal wins, then expiry (derived at read time). */
    fun effectiveStatus(
      status: String,
      expiresAt: Instant?,
      withdrawnAt: Instant?,
      now: Instant,
    ): String =
      when {
        status == "withdrawn" || withdrawnAt != null -> "withdrawn"
        expiresAt != null && !expiresAt.isAfter(now) -> "expired"
        else -> status
      }
  }
}
