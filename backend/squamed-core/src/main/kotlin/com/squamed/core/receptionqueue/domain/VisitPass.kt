package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "visit_pass", schema = "reception")
class VisitPass(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "visit_id", nullable = false)
  var visitId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "pass_number", nullable = false, length = 20)
  var passNumber: String,
  @Column(name = "visitor_name", nullable = false, length = 120)
  var visitorName: String,
  @Column(name = "relation", nullable = false, length = 30)
  var relation: String = "attendant",
  @Column(name = "phone", length = 30)
  var phone: String? = null,
  @Column(name = "id_reference", length = 60)
  var idReference: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
  @Column(name = "issued_at", nullable = false)
  var issuedAt: Instant,
  @Column(name = "returned_at")
  var returnedAt: Instant? = null,
) : BaseEntity()
