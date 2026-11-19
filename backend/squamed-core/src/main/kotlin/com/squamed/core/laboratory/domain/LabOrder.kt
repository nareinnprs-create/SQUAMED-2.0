package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "lab_order", schema = "laboratory")
class LabOrder(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "order_number", nullable = false, length = 50)
  var orderNumber: String,
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "routine",
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "ordered",
  @Column(name = "clinical_note")
  var clinicalNote: String? = null,
  @Column(name = "ordered_by")
  var orderedBy: UUID? = null,
  @Column(name = "ordered_at", nullable = false)
  var orderedAt: Instant? = null,
  @Column(name = "specimen_taken_at")
  var specimenTakenAt: Instant? = null,
  @Column(name = "collected_by")
  var collectedBy: UUID? = null,
  @Column(name = "received_at")
  var receivedAt: Instant? = null,
  @Column(name = "started_at")
  var startedAt: Instant? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
  @Column(name = "rejected_reason")
  var rejectedReason: String? = null,
  @Column(name = "result_status", nullable = false, length = 30)
  var resultStatus: String = "pending",
  @Column(name = "verified_at")
  var verifiedAt: Instant? = null,
  @Column(name = "verified_by")
  var verifiedBy: UUID? = null,
  @Column(name = "released_at")
  var releasedAt: Instant? = null,
  @Column(name = "released_by")
  var releasedBy: UUID? = null,
  @Column(name = "barcode_id", length = 50)
  var barcodeId: String? = null,
  @Column(name = "accession_id", length = 50)
  var accessionId: String? = null,
  @Column(name = "rejected_by")
  var rejectedBy: UUID? = null,
  @Column(name = "rejected_at")
  var rejectedAt: Instant? = null,
  @Column(name = "recollect_of")
  var recollectOf: UUID? = null,
) : BaseEntity()
