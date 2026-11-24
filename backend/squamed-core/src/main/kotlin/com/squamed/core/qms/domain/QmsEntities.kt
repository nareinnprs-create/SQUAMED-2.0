package com.squamed.core.qms.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "indicator", schema = "qms")
class QmsIndicator(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "name", nullable = false, length = 200) var name: String,
  @Column(name = "target", nullable = false) var target: BigDecimal = BigDecimal.ZERO,
  @Column(name = "actual", nullable = false) var actual: BigDecimal = BigDecimal.ZERO,
  @Column(name = "period", nullable = false, length = 20) var period: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "ON_TRACK",
) : BaseEntity()

@Entity
@Table(name = "capa", schema = "qms")
class Capa(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "title", nullable = false, length = 200) var title: String,
  @Column(name = "root_cause", length = 600) var rootCause: String? = null,
  @Column(name = "action", nullable = false, length = 600) var action: String,
  @Column(name = "due_on") var dueOn: LocalDate? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "OPEN",
  @Column(name = "closed_at") var closedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "audit", schema = "qms")
class QmsAudit(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "title", nullable = false, length = 200) var title: String,
  @Column(name = "scheduled_on", nullable = false) var scheduledOn: LocalDate,
  @Column(name = "status", nullable = false, length = 20) var status: String = "PLANNED",
  @Column(name = "findings", length = 800) var findings: String? = null,
  @Column(name = "completed_at") var completedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "risk", schema = "qms")
class QmsRisk(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "title", nullable = false, length = 200) var title: String,
  @Column(name = "likelihood", nullable = false) var likelihood: Int,
  @Column(name = "impact", nullable = false) var impact: Int,
  @Column(name = "score", nullable = false) var score: Int,
  @Column(name = "owner", length = 120) var owner: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "OPEN",
) : BaseEntity()

@Entity
@Table(name = "infection_case", schema = "qms")
class InfectionCase(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "infection_type", nullable = false, length = 30) var infectionType: String,
  @Column(name = "organism", length = 120) var organism: String? = null,
  @Column(name = "identified_on", nullable = false) var identifiedOn: LocalDate,
  @Column(name = "status", nullable = false, length = 20) var status: String = "ACTIVE",
  @Column(name = "resolved_at") var resolvedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "outbreak", schema = "qms")
class Outbreak(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "pathogen", nullable = false, length = 120) var pathogen: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "ACTIVE",
  @Column(name = "declared_at", nullable = false) var declaredAt: Instant = Instant.now(),
  @Column(name = "closed_at") var closedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "hand_hygiene", schema = "qms")
class HandHygiene(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "ward_id") var wardId: UUID? = null,
  @Column(name = "period", nullable = false, length = 20) var period: String,
  @Column(name = "opportunities", nullable = false) var opportunities: Int,
  @Column(name = "compliant", nullable = false) var compliant: Int,
) : BaseEntity()

@Entity
@Table(name = "litigation_hold", schema = "qms")
class LitigationHold(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "case_ref", nullable = false, length = 80) var caseRef: String,
  @Column(name = "subject_type", nullable = false, length = 60) var subjectType: String,
  @Column(name = "subject_id", nullable = false) var subjectId: UUID,
  @Column(name = "reason", nullable = false, length = 400) var reason: String,
  @Column(name = "active", nullable = false) var active: Boolean = true,
  @Column(name = "released_at") var releasedAt: Instant? = null,
) : BaseEntity()
