package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "pharmacy_stock", schema = "pharmacy")
class PharmacyStock(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "drug_id")
  var drugId: UUID? = null,
  @Column(name = "drug_code", length = 50)
  var drugCode: String? = null,
  @Column(name = "drug_name", nullable = false, length = 255)
  var drugName: String,
  @Column(name = "batch_number", length = 100)
  var batchNumber: String? = null,
  @Column(name = "expiry_date")
  var expiryDate: LocalDate? = null,
  @Column(name = "quantity_on_hand", nullable = false)
  var quantityOnHand: Int = 0,
  @Column(name = "reorder_level", nullable = false)
  var reorderLevel: Int = 10,
  @Column(name = "unit_price", nullable = false)
  var unitPrice: BigDecimal = BigDecimal.ZERO,
  @Column(name = "location", length = 100)
  var location: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
