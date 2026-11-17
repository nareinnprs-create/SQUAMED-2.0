package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "clinical_note", schema = "clinical")
class ClinicalNote(
  @Column(name = "encounter_id", nullable = false)
  var encounterId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "note_type", nullable = false, length = 30)
  var noteType: String = "progress",
  @Column(name = "title", length = 200)
  var title: String? = null,
  @Column(name = "subjective")
  var subjective: String? = null,
  @Column(name = "objective")
  var objective: String? = null,
  @Column(name = "assessment")
  var assessment: String? = null,
  @Column(name = "plan")
  var plan: String? = null,
  @Column(name = "is_signed", nullable = false)
  var isSigned: Boolean = false,
  @Column(name = "signed_at")
  var signedAt: Instant? = null,
  @Column(name = "signed_by")
  var signedBy: UUID? = null,
  // 01.04 note signature (E5 aging fallback): PIN hash now, external eSign tx id later.
  @Column(name = "signature_method", length = 20)
  var signatureMethod: String? = null,
  @Column(name = "signature_data", columnDefinition = "text")
  var signatureData: String? = null,
  @Column(name = "esign_transaction_id", length = 100)
  var esignTransactionId: String? = null,
) : BaseEntity()
