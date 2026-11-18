package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "allergy", schema = "patient")
class Allergy(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "allergen", nullable = false, length = 255)
  var allergen: String,
  @Column(name = "reaction", length = 255)
  var reaction: String? = null,
  @Column(name = "severity", nullable = false, length = 20)
  var severity: String = "mild",
  @Column(name = "recorded_date")
  var recordedDate: LocalDate? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
  @Column(name = "notes")
  var notes: String? = null,
) : BaseEntity() {
  companion object {
    /** NKA sentinel allergen (SAFETY L49): an explicit, audited "no known allergies" row. */
    const val NKA_ALLERGEN = "NO KNOWN ALLERGIES"

    const val SEVERITY_NONE = "none"

    /** SAFETY L50 severity codes: block | warn | mild | none. Unknown input fails safe to warn. */
    fun normalizeSeverity(raw: String?): String =
      when (raw?.trim()?.lowercase()) {
        "block", "anaphylaxis", "critical", "high", "life-threatening", "life_threatening" -> "block"
        "mild", "low", "minor" -> "mild"
        SEVERITY_NONE, "nka" -> SEVERITY_NONE
        else -> "warn" // unknown severity forces the typed-reason path, never a silent pass
      }
  }
}
