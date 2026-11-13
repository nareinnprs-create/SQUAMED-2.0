package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "ward", schema = "hospital_config")
class Ward(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", length = 50)
  var code: String? = null,
  @Column(name = "type", nullable = false, length = 50)
  var type: String = "general",
  @Column(name = "room_count", nullable = false)
  var roomCount: Int = 0,
  @Column(name = "bed_count", nullable = false)
  var bedCount: Int = 0,
) : BaseEntity()
