package com.squamed.core.pharmacy.api

import com.squamed.core.masterdata.domain.Drug
import com.squamed.core.pharmacy.domain.ControlledRegisterEntry
import com.squamed.core.pharmacy.domain.Dispensation
import com.squamed.core.pharmacy.domain.FormularyEntry
import com.squamed.core.pharmacy.domain.Indent
import com.squamed.core.pharmacy.domain.PharmacyStock
import com.squamed.core.pharmacy.domain.StockLedger
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
  // 02.06: audited substitution — reason required whenever a substitution is recorded.
  val substitutedDrugId: UUID? = null,
  val substitutedDrugName: String? = null,
  val substitutionReason: String? = null,
  // 02.06 / CS-04 pharmacist leg: required for controlled drugs.
  val secondAuthorizerId: UUID? = null,
  // 02.07 safety gate: mandatory when dispensing past a BLOCK finding; audited override.
  val safetyOverrideReason: String? = null,
)

data class FormularyRequestDto(
  val drugId: UUID,
  val notes: String? = null,
)

data class IndentRequestDto(
  val drugId: UUID? = null,
  val drugName: String? = null,
  val quantityRequested: Int,
  val note: String? = null,
)

data class IndentReceiveRequestDto(
  val quantityReceived: Int,
)

data class WastageRequestDto(
  val quantity: Int,
  val reason: String? = null,
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
  val stockId: UUID?,
  val batchNumber: String?,
  // 02.08 eMAR groundwork: batch expiry snapshot at dispense time.
  val expiryDate: java.time.LocalDate?,
  val substitutedDrugId: UUID?,
  val substitutedDrugName: String?,
  val substitutionReason: String?,
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
        stockId = entity.stockId,
        batchNumber = entity.batchNumber,
        expiryDate = entity.expiryDate,
        substitutedDrugId = entity.substitutedDrugId,
        substitutedDrugName = entity.substitutedDrugName,
        substitutionReason = entity.substitutionReason,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class FormularyEntryView(
  val id: UUID?,
  val hospitalId: UUID,
  val drugId: UUID,
  val drugName: String?,
  val drugCode: String?,
  val isActive: Boolean,
  val notes: String?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(
      entity: FormularyEntry,
      drug: Drug?,
    ) = FormularyEntryView(
      id = entity.id,
      hospitalId = entity.hospitalId,
      drugId = entity.drugId,
      drugName = drug?.name,
      drugCode = drug?.code,
      isActive = entity.isActive,
      notes = entity.notes,
      createdAt = entity.createdAt,
    )
  }
}

data class IndentView(
  val id: UUID?,
  val hospitalId: UUID,
  val drugId: UUID?,
  val drugName: String,
  val quantityRequested: Int,
  val quantityReceived: Int,
  val status: String,
  val note: String?,
  val requestedBy: UUID?,
  val receivedAt: Instant?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Indent) =
      IndentView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        drugId = entity.drugId,
        drugName = entity.drugName,
        quantityRequested = entity.quantityRequested,
        quantityReceived = entity.quantityReceived,
        status = entity.status,
        note = entity.note,
        requestedBy = entity.requestedBy,
        receivedAt = entity.receivedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class StockLedgerView(
  val id: UUID?,
  val hospitalId: UUID,
  val stockId: UUID,
  val movement: String,
  val quantityDelta: Int,
  val balanceAfter: Int,
  val referenceType: String?,
  val referenceId: UUID?,
  val actorId: UUID?,
  val note: String?,
  val occurredAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: StockLedger) =
      StockLedgerView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        stockId = entity.stockId,
        movement = entity.movement,
        quantityDelta = entity.quantityDelta,
        balanceAfter = entity.balanceAfter,
        referenceType = entity.referenceType,
        referenceId = entity.referenceId,
        actorId = entity.actorId,
        note = entity.note,
        occurredAt = entity.occurredAt,
        createdAt = entity.createdAt,
      )
  }
}

data class ControlledRegisterView(
  val id: UUID?,
  val hospitalId: UUID,
  val dispensationId: UUID?,
  val drugId: UUID?,
  val drugName: String,
  val batchNumber: String?,
  val quantity: Int,
  val dispensedBy: UUID?,
  val secondAuthorizerId: UUID,
  val occurredAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: ControlledRegisterEntry) =
      ControlledRegisterView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        dispensationId = entity.dispensationId,
        drugId = entity.drugId,
        drugName = entity.drugName,
        batchNumber = entity.batchNumber,
        quantity = entity.quantity,
        dispensedBy = entity.dispensedBy,
        secondAuthorizerId = entity.secondAuthorizerId,
        occurredAt = entity.occurredAt,
        createdAt = entity.createdAt,
      )
  }
}

// 02.08 reorder proposals: human-approved PO stubs generated from min-level stock alerts.
data class ReorderProposalView(
  val id: UUID?,
  val hospitalId: UUID,
  val drugId: UUID?,
  val drugName: String,
  val stockId: UUID?,
  val requestedQuantity: Int,
  val status: String,
  val note: String?,
  val proposedBy: UUID?,
  val decidedBy: UUID?,
  val decidedAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.pharmacy.domain.ReorderProposal) =
      ReorderProposalView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        drugId = entity.drugId,
        drugName = entity.drugName,
        stockId = entity.stockId,
        requestedQuantity = entity.requestedQuantity,
        status = entity.status,
        note = entity.note,
        proposedBy = entity.proposedBy,
        decidedBy = entity.decidedBy,
        decidedAt = entity.decidedAt,
        createdAt = entity.createdAt,
      )
  }
}

data class ReorderApproveDto(
  val quantity: Int? = null,
  val note: String? = null,
)

data class ReorderRejectDto(
  val note: String,
)
