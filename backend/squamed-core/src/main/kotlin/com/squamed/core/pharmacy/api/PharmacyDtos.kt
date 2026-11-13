package com.squamed.core.pharmacy.api

import com.squamed.core.pharmacy.domain.Dispensation
import com.squamed.core.pharmacy.domain.PharmacyStock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StockRequestDto(
  val hospitalId: UUID? = null,
  val drugId: UUID? = null,
  val drugCode: String? = null,
  val drugName: String,
  val batchNumber: String? = null,
  val expiryDate: LocalDate? = null,
  val quantityOnHand: Int? = null,
  val reorderLevel: Int? = null,
  val unitPrice: BigDecimal? = null,
  val location: String? = null,
  val isActive: Boolean? = null,
)

data class StockUpdateDto(
  val drugId: UUID? = null,
  val drugCode: String? = null,
  val drugName: String? = null,
  val batchNumber: String? = null,
  val expiryDate: LocalDate? = null,
  val quantityOnHand: Int? = null,
  val reorderLevel: Int? = null,
  val unitPrice: BigDecimal? = null,
  val location: String? = null,
  val isActive: Boolean? = null,
)

data class DispenseRequestDto(
  val prescriptionId: UUID? = null,
  val patientId: UUID,
  val encounterId: UUID? = null,
  val drugId: UUID? = null,
  val stockId: UUID? = null,
  val drugName: String,
  val quantity: Int,
  val unitPrice: BigDecimal? = null,
  val notes: String? = null,
)

data class ReturnDispensationRequest(
  val reason: String? = null,
)

data class PharmacyStockView(
  val id: UUID?,
  val hospitalId: UUID,
  val drugId: UUID?,
  val drugCode: String?,
  val drugName: String,
  val batchNumber: String?,
  val expiryDate: LocalDate?,
  val quantityOnHand: Int,
  val reorderLevel: Int,
  val unitPrice: BigDecimal,
  val location: String?,
  val isActive: Boolean,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: PharmacyStock) =
      PharmacyStockView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        drugId = entity.drugId,
        drugCode = entity.drugCode,
        drugName = entity.drugName,
        batchNumber = entity.batchNumber,
        expiryDate = entity.expiryDate,
        quantityOnHand = entity.quantityOnHand,
        reorderLevel = entity.reorderLevel,
        unitPrice = entity.unitPrice,
        location = entity.location,
        isActive = entity.isActive,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class DispensationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val prescriptionId: UUID?,
  val patientId: UUID,
  val encounterId: UUID?,
  val drugId: UUID?,
  val drugName: String,
  val quantity: Int,
  val status: String,
  val dispensedBy: UUID?,
  val dispensedAt: Instant?,
  val verifiedBy: UUID?,
  val verifiedAt: Instant?,
  val unitPrice: BigDecimal,
  val totalPrice: BigDecimal,
  val notes: String?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Dispensation) =
      DispensationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        prescriptionId = entity.prescriptionId,
        patientId = entity.patientId,
        encounterId = entity.encounterId,
        drugId = entity.drugId,
        drugName = entity.drugName,
        quantity = entity.quantity,
        status = entity.status,
        dispensedBy = entity.dispensedBy,
        dispensedAt = entity.dispensedAt,
        verifiedBy = entity.verifiedBy,
        verifiedAt = entity.verifiedAt,
        unitPrice = entity.unitPrice,
        totalPrice = entity.totalPrice,
        notes = entity.notes,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}
