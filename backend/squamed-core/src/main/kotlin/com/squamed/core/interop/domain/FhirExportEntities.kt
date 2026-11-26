package com.squamed.core.interop.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** One outbound FHIR push (V107 `interop.fhir_export`); EXPORTED, FAILED or WITHHELD. */
@Entity
@Table(name = "fhir_export", schema = "interop")
class FhirExport(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "target_base", nullable = false, length = 500) var targetBase: String,
  @Column(name = "resource_type", nullable = false, length = 50) var resourceType: String,
  @Column(name = "resource_id", nullable = false) var resourceId: UUID,
  @Column(name = "status", nullable = false, length = 20) var status: String,
  @Column(name = "http_status") var httpStatus: Int? = null,
  @Column(name = "detail", length = 500) var detail: String? = null,
  @Column(name = "exported_at") var exportedAt: Instant? = null,
) : BaseEntity()
