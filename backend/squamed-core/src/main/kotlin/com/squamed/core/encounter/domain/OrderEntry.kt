package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_entry", schema = "clinical")
class OrderEntry(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "order_type", nullable = false, length = 30)
  var orderType: String,
  @Column(name = "order_code", length = 50)
  var orderCode: String? = null,
  @Column(name = "order_name", nullable = false, length = 255)
  var orderName: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "pending",
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "routine",
  @Column(name = "instructions")
  var instructions: String? = null,
  @Column(name = "ordered_by")
  var orderedBy: UUID? = null,
  @Column(name = "ordered_at")
  var orderedAt: Instant? = null,
) : BaseEntity()
