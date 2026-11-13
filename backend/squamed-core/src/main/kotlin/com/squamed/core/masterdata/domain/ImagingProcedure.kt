package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "imaging_procedure", schema = "master_data")
class ImagingProcedure(
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "name", nullable = false, length = 255)
  var name: String,
  @Column(name = "modality", length = 50)
  var modality: String? = null,
  @Column(name = "body_part", length = 100)
  var bodyPart: String? = null,
  @Column(name = "preparation")
  var preparation: String? = null,
  @Column(name = "price_qar", precision = 12, scale = 2)
  var priceQar: BigDecimal? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
