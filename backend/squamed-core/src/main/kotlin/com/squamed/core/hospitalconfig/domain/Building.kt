package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "building", schema = "hospital_config")
class Building(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "campus_id")
  var campusId: UUID? = null,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", length = 50)
  var code: String? = null,
  @Column(name = "floors", nullable = false)
  var floors: Int = 1,
) : BaseEntity()
