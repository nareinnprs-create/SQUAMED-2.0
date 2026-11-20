package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dispensation", schema = "pharmacy")
class Dispensation(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "prescription_id")
  var prescriptionId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "drug_id")
  var drugId: UUID? = null,
  @Column(name = "drug_name", nullable = false, length = 255)
  var drugName: String,
  @Column(name = "quantity", nullable = false)
  var quantity: Int,
  @Column(name = "status", nullable = false, length = 30)
  var status: String = "pending",
  @Column(name = "dispensed_by")
  var dispensedBy: UUID? = null,
  @Column(name = "dispensed_at")
  var dispensedAt: Instant? = null,
  @Column(name = "verified_by")
  var verifiedBy: UUID? = null,
  @Column(name = "verified_at")
  var verifiedAt: Instant? = null,
  @Column(name = "unit_price", nullable = false)
  var unitPrice: BigDecimal = BigDecimal.ZERO,
  @Column(name = "total_price", nullable = false)
  var totalPrice: BigDecimal = BigDecimal.ZERO,
  @Column(name = "notes")
  var notes: String? = null,
  @Column(name = "stock_id")
  var stockId: UUID? = null,
  @Column(name = "batch_number", length = 100)
  var batchNumber: String? = null,
  // 02.08 eMAR groundwork: expiry snapshot of the dispensed batch.
  @Column(name = "expiry_date")
  var expiryDate: java.time.LocalDate? = null,
  @Column(name = "substituted_drug_id")
  var substitutedDrugId: UUID? = null,
  @Column(name = "substituted_drug_name", length = 255)
  var substitutedDrugName: String? = null,
  @Column(name = "substitution_reason", length = 400)
  var substitutionReason: String? = null,
) : BaseEntity()
