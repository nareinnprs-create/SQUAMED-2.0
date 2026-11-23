package com.squamed.core.group.repository

import com.squamed.core.group.domain.CrossHospitalShare
import com.squamed.core.group.domain.GroupConfigDocument
import com.squamed.core.group.domain.GroupReferral
import com.squamed.core.group.domain.HospitalGroup
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HospitalGroupRepository : JpaRepository<HospitalGroup, UUID> {
  fun findByCodeIgnoreCase(code: String): HospitalGroup?

  fun findAllByOrderByCreatedAtAsc(): List<HospitalGroup>
}

interface GroupConfigDocumentRepository : JpaRepository<GroupConfigDocument, UUID> {
  fun findFirstByGroupIdAndTypeOrderByDocumentVersionDesc(
    groupId: UUID,
    type: String,
  ): GroupConfigDocument?

  fun findByGroupIdAndTypeAndStatus(
    groupId: UUID,
    type: String,
    status: String,
  ): List<GroupConfigDocument>
}

interface CrossHospitalShareRepository : JpaRepository<CrossHospitalShare, UUID> {
  fun findByGroupIdAndPatientIdAndToHospitalId(
    groupId: UUID,
    patientId: UUID,
    toHospitalId: UUID,
  ): CrossHospitalShare?

  fun findByGroupIdAndPatientIdAndStatus(
    groupId: UUID,
    patientId: UUID,
    status: String,
  ): List<CrossHospitalShare>
}

interface GroupReferralRepository : JpaRepository<GroupReferral, UUID> {
  fun findByToHospitalIdOrderByCreatedAtDesc(toHospitalId: UUID): List<GroupReferral>

  fun findByToHospitalIdAndStatusOrderByCreatedAtDesc(
    toHospitalId: UUID,
    status: String,
  ): List<GroupReferral>

  fun findByFromHospitalIdOrderByCreatedAtDesc(fromHospitalId: UUID): List<GroupReferral>

  fun findByIdAndGroupId(
    id: UUID,
    groupId: UUID,
  ): GroupReferral?
}
