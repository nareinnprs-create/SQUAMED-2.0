package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "hospital_service", schema = "hospital_config")
class HospitalService(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", length = 50)
  var code: String? = null,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
  @Column(name = "description")
  var description: String? = null,
) : BaseEntity()
