package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.ConfigDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConfigDocumentRepository : JpaRepository<ConfigDocument, UUID> {
  fun findFirstByHospitalIdAndTypeOrderByDocumentVersionDesc(
    hospitalId: UUID,
    type: String,
  ): ConfigDocument?

  fun findByHospitalIdAndTypeOrderByDocumentVersionDesc(
    hospitalId: UUID,
    type: String,
  ): List<ConfigDocument>

  fun findFirstByHospitalIdAndTypeAndStatusOrderByDocumentVersionDesc(
    hospitalId: UUID,
    type: String,
    status: String,
  ): ConfigDocument?

  fun findByHospitalIdAndTypeAndStatus(
    hospitalId: UUID,
    type: String,
    status: String,
  ): List<ConfigDocument>

  fun findFirstByHospitalIdAndTypeAndDocumentVersion(
    hospitalId: UUID,
    type: String,
    documentVersion: Int,
  ): ConfigDocument?
}
