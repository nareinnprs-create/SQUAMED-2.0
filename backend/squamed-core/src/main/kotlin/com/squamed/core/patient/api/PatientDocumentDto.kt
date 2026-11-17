package com.squamed.core.patient.api

import com.squamed.core.patient.domain.PatientDocument
import java.time.Instant
import java.util.UUID

data class PatientDocumentDto(
  val id: UUID?,
  val hospitalId: UUID,
  val patientId: UUID,
  val name: String,
  val category: String,
  val contentType: String,
  val sizeBytes: Long,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: PatientDocument): PatientDocumentDto =
      PatientDocumentDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        name = entity.name,
        category = entity.category,
        contentType = entity.contentType,
        sizeBytes = entity.sizeBytes,
        createdAt = entity.createdAt,
      )
  }
}
