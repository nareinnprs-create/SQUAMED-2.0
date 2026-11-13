package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "room", schema = "hospital_config")
class Room(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "ward_id")
  var wardId: UUID? = null,
  @Column(name = "building_id")
  var buildingId: UUID? = null,
  @Column(name = "floor")
  var floor: Int? = null,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", length = 50)
  var code: String? = null,
  @Column(name = "type", nullable = false, length = 50)
  var type: String = "general",
  @Column(name = "max_beds", nullable = false)
  var maxBeds: Int = 1,
) : BaseEntity()
