package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "service_point", schema = "queue")
class ServicePoint(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "name", nullable = false, length = 150)
  var name: String,
  @Column(name = "code", length = 50)
  var code: String? = null,
  @Column(name = "queue_type", nullable = false, length = 30)
  var queueType: String,
  @Column(name = "counter_number", length = 20)
  var counterNumber: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "open",
) : BaseEntity()
