package com.squamed.core.group.api

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class GroupCreateDto(
  val code: String,
  val name: String,
)

data class GroupHospitalView(
  val id: UUID?,
  val name: String,
  val code: String,
)

data class GroupView(
  val id: UUID?,
  val code: String,
  val name: String,
  val status: String,
  val hospitals: List<GroupHospitalView>,
)

data class ShareCandidateView(
  val patientId: UUID?,
  val mrn: String,
  val name: String,
  val hospitalId: UUID?,
  val hospitalName: String?,
  val matchReason: String,
  val score: Int,
)

data class ShareCreateDto(
  val toHospitalId: UUID,
  val consentId: UUID,
)

data class ShareView(
  val id: UUID?,
  val patientId: UUID,
  val fromHospitalId: UUID,
  val toHospitalId: UUID,
  val consentId: UUID,
  val status: String,
  val createdAt: Instant?,
  val revokedAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.group.domain.CrossHospitalShare) =
      ShareView(
        entity.id,
        entity.patientId,
        entity.fromHospitalId,
        entity.toHospitalId,
        entity.consentId,
        entity.status,
        entity.createdAt,
        entity.revokedAt,
      )
  }
}

data class AccessView(
  val patientId: UUID,
  val hospitalId: UUID,
  val shared: Boolean,
  val shareId: UUID?,
  val mrn: String?,
  val name: String?,
)

data class ReferralCreateDto(
  val patientId: UUID,
  val toHospitalId: UUID,
  val reason: String,
  val notes: String? = null,
)

data class ReferralStatusDto(
  val status: String,
)

data class ReferralView(
  val id: UUID?,
  val patientId: UUID,
  val fromHospitalId: UUID,
  val toHospitalId: UUID,
  val reason: String,
  val notes: String?,
  val status: String,
  val createdAt: Instant?,
  val decidedAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.group.domain.GroupReferral) =
      ReferralView(
        entity.id,
        entity.patientId,
        entity.fromHospitalId,
        entity.toHospitalId,
        entity.reason,
        entity.notes,
        entity.status,
        entity.createdAt,
        entity.decidedAt,
      )
  }
}

data class GroupConfigPublishDto(
  val type: String,
  val content: JsonNode,
)

data class ConfigResolveView(
  val type: String,
  val source: String,
  val documentVersion: Int,
  val content: JsonNode,
)

data class DirectoryEntryView(
  val userId: UUID,
  val email: String,
  val hospitalId: UUID?,
  val roles: List<String>,
)
