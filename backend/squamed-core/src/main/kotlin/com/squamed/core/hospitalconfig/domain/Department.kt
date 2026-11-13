package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "department", schema = "hospital_config")
class Department(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "type", nullable = false, length = 50)
  var type: String,
  @Column(name = "parent_id")
  var parentId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
  @Column(name = "description")
  var description: String? = null,
) : BaseEntity()
