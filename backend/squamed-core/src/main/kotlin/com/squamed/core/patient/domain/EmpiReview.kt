package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** EMPI manual review queue row (V47): a possible match awaiting two-person merge decision. */
@Entity
@Table(name = "empi_review", schema = "patient")
class EmpiReview(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  /** Survivor: the record kept after the merge. */
  @Column(name = "target_patient_id", nullable = false)
  var targetPatientId: UUID,
  /** Record merged into the target (archived, reversible via patient_merge_log). */
  @Column(name = "duplicate_patient_id", nullable = false)
  var duplicatePatientId: UUID,
  @Column(name = "score", nullable = false)
  var score: Int,
  @Column(name = "matched_on", nullable = false)
  var matchedOn: String = "",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = STATUS_PENDING,
  @Column(name = "submitted_by", nullable = false)
  var submittedBy: UUID,
  @Column(name = "decided_by")
  var decidedBy: UUID? = null,
  @Column(name = "decided_at")
  var decidedAt: Instant? = null,
  /** Set on approval: the patient_merge_log row executing the merge. */
  @Column(name = "merge_id")
  var mergeId: UUID? = null,
) : BaseEntity() {
  companion object {
    const val STATUS_PENDING = "PENDING"
    const val STATUS_APPROVED = "APPROVED"
    const val STATUS_REJECTED = "REJECTED"
  }
}
