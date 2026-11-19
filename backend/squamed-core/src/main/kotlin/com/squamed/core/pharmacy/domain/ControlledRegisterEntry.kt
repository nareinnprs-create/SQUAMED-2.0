package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Controlled-drug dispense register: one row per controlled dispensation, second authorizer required (CS-04). */
@Entity
@Table(name = "controlled_register", schema = "pharmacy")
class ControlledRegisterEntry(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "dispensation_id")
  var dispensationId: UUID? = null,
  @Column(name = "drug_id")
  var drugId: UUID? = null,
  @Column(name = "drug_name", nullable = false, length = 255)
  var drugName: String,
  @Column(name = "batch_number", length = 100)
  var batchNumber: String? = null,
  @Column(name = "quantity", nullable = false)
  var quantity: Int,
  @Column(name = "dispensed_by")
  var dispensedBy: UUID? = null,
  @Column(name = "second_authorizer_id", nullable = false)
  var secondAuthorizerId: UUID,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant? = null,
) : BaseEntity()
