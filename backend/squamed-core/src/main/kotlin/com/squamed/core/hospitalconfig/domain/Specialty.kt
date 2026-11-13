package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "specialty", schema = "hospital_config")
class Specialty(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @Column(name = "description")
  var description: String? = null,
) : BaseEntity()
