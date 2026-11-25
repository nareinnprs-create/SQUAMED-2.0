package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 07.09 histopathology/cytology: accession -> gross -> slides ready -> pathologist sign-out.
@Entity
@Table(name = "path_specimen", schema = "laboratory")
class PathSpecimen(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "accession_no", nullable = false, length = 40) var accessionNo: String,
  @Column(name = "specimen_type", nullable = false, length = 20) var specimenType: String,
  @Column(name = "site", length = 120) var site: String? = null,
  @Column(name = "gross_description", length = 2000) var grossDescription: String? = null,
  @Column(name = "grossed_by") var grossedBy: UUID? = null,
  @Column(name = "grossed_at") var grossedAt: Instant? = null,
  @Column(name = "status", nullable = false, length = 30) var status: String = "ACCESSIONED",
  @Column(name = "diagnosis_code", length = 40) var diagnosisCode: String? = null,
  @Column(name = "diagnosis_text", length = 2000) var diagnosisText: String? = null,
  @Column(name = "signed_by") var signedBy: UUID? = null,
  @Column(name = "signed_at") var signedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "path_slide", schema = "laboratory")
class PathSlide(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "specimen_id", nullable = false) var specimenId: UUID,
  @Column(name = "slide_no", nullable = false) var slideNo: Int,
  @Column(name = "stain", nullable = false, length = 60) var stain: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "PREPARED",
  @Column(name = "ready_at") var readyAt: Instant? = null,
) : BaseEntity()
