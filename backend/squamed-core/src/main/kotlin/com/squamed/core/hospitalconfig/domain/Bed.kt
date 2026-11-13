package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "bed", schema = "hospital_config")
class Bed(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "ward_id")
  var wardId: UUID? = null,
  @Column(name = "room_id")
  var roomId: UUID? = null,
  @Column(name = "label", nullable = false, length = 50)
  var label: String,
  @Column(name = "type", nullable = false, length = 50)
  var type: String = "standard",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "available",
) : BaseEntity()
