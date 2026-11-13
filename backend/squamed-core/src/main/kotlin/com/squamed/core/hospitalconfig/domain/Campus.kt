package com.squamed.core.hospitalconfig.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "campus", schema = "hospital_config")
class Campus(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "name", nullable = false, length = 200)
  var name: String,
  @Column(name = "code", length = 50)
  var code: String? = null,
  @Column(name = "address_line1", length = 300)
  var addressLine1: String? = null,
  @Column(name = "city", length = 100)
  var city: String? = null,
  @Column(name = "timezone", length = 50)
  var timezone: String? = null,
) : BaseEntity()
