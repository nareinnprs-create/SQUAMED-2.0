package com.squamed.core.group.domain

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "hospital_group", schema = "grp")
class HospitalGroup(
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
) : BaseEntity()

// 06.01: group-level defaults; a hospital's own active document overrides them.
@Entity
@Table(name = "group_config_document", schema = "grp")
class GroupConfigDocument(
  @Column(name = "group_id", nullable = false)
  var groupId: UUID,
  @Column(name = "type", nullable = false, length = 50)
  var type: String,
  @Column(name = "doc_version", nullable = false)
  var documentVersion: Int,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content", columnDefinition = "jsonb", nullable = false)
  var content: JsonNode,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
) : BaseEntity()

// 06.01: a consent-gated share of one patient record into another group hospital.
@Entity
@Table(name = "cross_hospital_share", schema = "grp")
class CrossHospitalShare(
  @Column(name = "group_id", nullable = false)
  var groupId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "from_hospital_id", nullable = false)
  var fromHospitalId: UUID,
  @Column(name = "to_hospital_id", nullable = false)
  var toHospitalId: UUID,
  @Column(name = "consent_id", nullable = false)
  var consentId: UUID,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "ACTIVE",
  @Column(name = "revoked_at")
  var revokedAt: Instant? = null,
  @Column(name = "revoked_by")
  var revokedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "referral", schema = "grp")
class GroupReferral(
  @Column(name = "group_id", nullable = false)
  var groupId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "from_hospital_id", nullable = false)
  var fromHospitalId: UUID,
  @Column(name = "to_hospital_id", nullable = false)
  var toHospitalId: UUID,
  @Column(name = "reason", nullable = false, length = 400)
  var reason: String,
  @Column(name = "notes", length = 600)
  var notes: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PENDING",
  @Column(name = "decided_by")
  var decidedBy: UUID? = null,
  @Column(name = "decided_at")
  var decidedAt: Instant? = null,
) : BaseEntity()
