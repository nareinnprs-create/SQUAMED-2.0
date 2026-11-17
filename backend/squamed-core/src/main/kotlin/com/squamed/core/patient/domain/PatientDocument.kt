package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** 01.05 Patient 360: patient document metadata (bytes live in object storage via PatientDocumentStorage). */
@Entity
@Table(name = "document", schema = "patient")
class PatientDocument(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "name", nullable = false)
  var name: String,
  @Column(name = "category", nullable = false, length = 40)
  var category: String = "other",
  @Column(name = "content_type", nullable = false, length = 120)
  var contentType: String,
  @Column(name = "size_bytes", nullable = false)
  var sizeBytes: Long,
  @Column(name = "storage_key", nullable = false, length = 500)
  var storageKey: String,
) : BaseEntity()
