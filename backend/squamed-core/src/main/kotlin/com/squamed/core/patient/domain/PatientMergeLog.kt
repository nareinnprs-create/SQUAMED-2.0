package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Versioned, reversible merge audit trail (V47). `snapshot` holds the duplicate's
 * moved child row ids (jsonb) so unmerge can re-point every row back; merge_version
 * increments across unmerge/re-merge cycles of the same pair.
 */
@Entity
@Table(name = "patient_merge_log", schema = "patient")
class PatientMergeLog(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "target_patient_id", nullable = false)
  var targetPatientId: UUID,
  @Column(name = "duplicate_patient_id", nullable = false)
  var duplicatePatientId: UUID,
  @Column(name = "merge_version", nullable = false)
  var mergeVersion: Int,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = STATUS_MERGED,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
  var snapshot: String,
  @Column(name = "merged_by", nullable = false)
  var mergedBy: UUID,
  @Column(name = "merged_at", nullable = false)
  var mergedAt: Instant? = null,
  @Column(name = "unmerged_by")
  var unmergedBy: UUID? = null,
  @Column(name = "unmerged_at")
  var unmergedAt: Instant? = null,
) : BaseEntity() {
  companion object {
    const val STATUS_MERGED = "MERGED"
    const val STATUS_UNMERGED = "UNMERGED"
  }
}
