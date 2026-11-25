package com.squamed.core.laboratory.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// 07.09 microbiology: culture -> organism identification -> AST panel -> final sign-out.
@Entity
@Table(name = "micro_culture", schema = "laboratory")
class MicroCulture(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "lab_order_id", nullable = false) var labOrderId: UUID,
  @Column(name = "specimen_type", nullable = false, length = 60) var specimenType: String,
  @Column(name = "status", nullable = false, length = 30) var status: String = "PENDING",
  @Column(name = "organism_code", length = 40) var organismCode: String? = null,
  @Column(name = "organism_name", length = 200) var organismName: String? = null,
  @Column(name = "identified_at") var identifiedAt: Instant? = null,
  @Column(name = "identified_by") var identifiedBy: UUID? = null,
  @Column(name = "finalized_at") var finalizedAt: Instant? = null,
  @Column(name = "finalized_by") var finalizedBy: UUID? = null,
  @Column(name = "notes", length = 600) var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "micro_ast", schema = "laboratory")
class MicroAst(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "culture_id", nullable = false) var cultureId: UUID,
  @Column(name = "antibiotic_code", nullable = false, length = 40) var antibioticCode: String,
  @Column(name = "antibiotic_name", nullable = false, length = 120) var antibioticName: String,
  @Column(name = "susceptibility", nullable = false, length = 1) var susceptibility: String,
  @Column(name = "mic") var mic: BigDecimal? = null,
  @Column(name = "panel", nullable = false, length = 60) var panel: String,
) : BaseEntity()
