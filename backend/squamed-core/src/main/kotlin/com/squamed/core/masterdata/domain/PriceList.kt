package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "price_list", schema = "master_data")
class PriceList(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "currency", nullable = false, length = 3)
  var currency: String = "QAR",
  @Column(name = "valid_from")
  var validFrom: LocalDate? = null,
  @Column(name = "valid_to")
  var validTo: LocalDate? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
