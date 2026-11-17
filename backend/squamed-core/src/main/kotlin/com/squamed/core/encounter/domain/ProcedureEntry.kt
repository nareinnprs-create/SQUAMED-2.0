package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 01.04 procedure documented on an encounter (rides clinical:write:note; no new permission code). */
@Entity
@Table(name = "procedure", schema = "clinical")
class ProcedureEntry(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "encounter_id", nullable = false)
  var encounterId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "procedure_code", length = 40)
  var procedureCode: String? = null,
  @Column(name = "procedure_name", nullable = false, length = 255)
  var procedureName: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "planned",
  @Column(name = "performed_at")
  var performedAt: Instant? = null,
  @Column(name = "performed_by")
  var performedBy: UUID? = null,
  @Column(name = "outcome", columnDefinition = "text")
  var outcome: String? = null,
  @Column(name = "notes", columnDefinition = "text")
  var notes: String? = null,
) : BaseEntity()
