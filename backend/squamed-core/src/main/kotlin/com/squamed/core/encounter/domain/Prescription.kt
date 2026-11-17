package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "prescription", schema = "clinical")
class Prescription(
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "drug_id")
  var drugId: UUID? = null,
  @Column(name = "drug_name", nullable = false, length = 255)
  var drugName: String,
  @Column(name = "strength", length = 100)
  var strength: String? = null,
  @Column(name = "form", length = 50)
  var form: String? = null,
  @Column(name = "dose", length = 100)
  var dose: String? = null,
  @Column(name = "frequency", length = 100)
  var frequency: String? = null,
  @Column(name = "route", length = 50)
  var route: String? = null,
  @Column(name = "duration", length = 50)
  var duration: String? = null,
  @Column(name = "instructions")
  var instructions: String? = null,
  @Column(name = "quantity")
  var quantity: Int? = null,
  @Column(name = "is_prn", nullable = false)
  var isPrn: Boolean = false,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "prescribed",
  @Column(name = "prescribed_by")
  var prescribedBy: UUID? = null,
  @Column(name = "prescribed_at")
  var prescribedAt: Instant? = null,
  // 01.04 allergy gate (CS-02/CS-03): how the allergy screen went for this Rx.
  @Column(name = "override_reason", columnDefinition = "text")
  var overrideReason: String? = null,
  @Column(name = "allergy_status", length = 20)
  var allergyStatus: String? = null,
) : BaseEntity()
