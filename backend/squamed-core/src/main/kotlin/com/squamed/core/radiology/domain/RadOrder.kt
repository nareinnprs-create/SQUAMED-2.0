package com.squamed.core.radiology.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 02.05 RIS-lite: an imaging order with its modality-worklist accession.
@Entity
@Table(name = "rad_order", schema = "radiology")
class RadOrder(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "accession", nullable = false, length = 40)
  var accession: String,
  @Column(name = "modality", nullable = false, length = 16)
  var modality: String,
  @Column(name = "body_part", nullable = false, length = 80)
  var bodyPart: String,
  @Column(name = "priority", nullable = false, length = 10)
  var priority: String = "routine",
  @Column(name = "status", nullable = false, length = 12)
  var status: String = "ordered",
  @Column(name = "clinical_note", length = 400)
  var clinicalNote: String? = null,
  @Column(name = "ordered_by")
  var orderedBy: UUID? = null,
  @Column(name = "ordered_at", nullable = false)
  var orderedAt: Instant = Instant.now(),
) : BaseEntity()
