package com.squamed.core.billing.api

import com.squamed.core.billing.domain.Charge
import com.squamed.core.billing.domain.Invoice
import com.squamed.core.billing.domain.InvoiceLine
import com.squamed.core.billing.domain.Payment
import com.squamed.core.billing.domain.TariffItem
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TariffDto(
  val id: UUID? = null,
  val hospitalId: UUID? = null,
  val code: String? = null,
  val name: String? = null,
  val category: String? = null,
  val priceQar: BigDecimal? = null,
  val insuranceApplicable: Boolean? = null,
  val isActive: Boolean? = null,
) {
  companion object {
    fun from(entity: TariffItem) =
      TariffDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        code = entity.code,
        name = entity.name,
        category = entity.category,
        priceQar = entity.priceQar,
        insuranceApplicable = entity.insuranceApplicable,
        isActive = entity.isActive,
      )
  }
}

data class PostChargeDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val visitId: UUID? = null,
  val encounterId: UUID? = null,
  val chargeCode: String? = null,
  val chargeName: String? = null,
  val category: String? = null,
  val quantity: Int? = null,
  val unitPriceQar: BigDecimal? = null,
  val insuranceClaimable: Boolean? = null,
  // 03.01: manual ledger entries are audited with a mandatory reason.
  val reason: String? = null,
  // 03.02: package code pins the active package version at capture time.
  val packageCode: String? = null,
)

data class ChargeView(
  val id: UUID?,
  val hospitalId: UUID?,
  val visitId: UUID?,
  val encounterId: UUID?,
  val patientId: UUID,
  val chargeCode: String?,
  val chargeName: String,
  val category: String?,
  val quantity: Int,
  val unitPriceQar: BigDecimal,
  val totalQar: BigDecimal,
  val status: String,
  val insuranceClaimable: Boolean,
  val postedBy: UUID?,
  val postedAt: Instant,
  val referenceType: String?,
  val referenceId: UUID?,
  // 03.01 ledger provenance.
  val sourceEventId: UUID?,
  val entryMode: String,
  val entryReason: String?,
  val packageVersionId: UUID?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Charge) =
      ChargeView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        visitId = entity.visitId,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        chargeCode = entity.chargeCode,
        chargeName = entity.chargeName,
        category = entity.category,
        quantity = entity.quantity,
        unitPriceQar = entity.unitPriceQar,
        totalQar = entity.totalQar,
        status = entity.status,
        insuranceClaimable = entity.insuranceClaimable,
        postedBy = entity.postedBy,
        postedAt = entity.postedAt,
        referenceType = entity.referenceType,
        referenceId = entity.referenceId,
        sourceEventId = entity.sourceEventId,
        entryMode = entity.entryMode,
        entryReason = entity.entryReason,
        packageVersionId = entity.packageVersionId,
        createdAt = entity.createdAt,
      )
  }
}

data class IssueInvoiceDto(
  val patientId: UUID? = null,
  val visitId: UUID? = null,
  val discountQar: BigDecimal? = null,
  // 03.02: payer class drives rate-card repricing; discounts need a reason and >10% needs approval.
  val payerClass: String? = null,
  val discountReason: String? = null,
)

data class InvoiceView(
  val id: UUID?,
  val hospitalId: UUID?,
  val visitId: UUID?,
  val patientId: UUID,
  val invoiceNumber: String,
  val subtotalQar: BigDecimal,
  val discountQar: BigDecimal,
  val taxQar: BigDecimal,
  val totalQar: BigDecimal,
  val amountPaidQar: BigDecimal,
  val amountDueQar: BigDecimal,
  val status: String,
  val insuranceClaimStatus: String?,
  val payerClass: String,
  val discountReason: String?,
  val issuedAt: Instant,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Invoice) =
      InvoiceView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        visitId = entity.visitId,
        patientId = entity.patientId,
        invoiceNumber = entity.invoiceNumber,
        subtotalQar = entity.subtotalQar,
        discountQar = entity.discountQar,
        taxQar = entity.taxQar,
        totalQar = entity.totalQar,
        amountPaidQar = entity.amountPaidQar,
        amountDueQar = entity.amountDueQar,
        status = entity.status,
        insuranceClaimStatus = entity.insuranceClaimStatus,
        payerClass = entity.payerClass,
        discountReason = entity.discountReason,
        issuedAt = entity.issuedAt,
        createdAt = entity.createdAt,
      )
  }
}

data class InvoiceLineView(
  val description: String,
  val quantity: Int,
  val unitPriceQar: BigDecimal,
  val totalQar: BigDecimal,
  val basePriceQar: BigDecimal?,
  val rateCardId: UUID?,
) {
  companion object {
    fun from(entity: InvoiceLine) =
      InvoiceLineView(
        description = entity.description,
        quantity = entity.quantity,
        unitPriceQar = entity.unitPriceQar,
        totalQar = entity.totalQar,
        basePriceQar = entity.basePriceQar,
        rateCardId = entity.rateCardId,
      )
  }
}

data class PaymentCreateDto(
  val method: String? = null,
  val amountQar: BigDecimal? = null,
  val reference: String? = null,
)

data class PaymentView(
  val id: UUID?,
  val invoiceId: UUID,
  val patientId: UUID,
  val amountQar: BigDecimal,
  val method: String,
  val reference: String?,
  val status: String,
  val receivedBy: UUID?,
  val receivedAt: Instant,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: Payment) =
      PaymentView(
        id = entity.id,
        invoiceId = entity.invoiceId,
        patientId = entity.patientId,
        amountQar = entity.amountQar,
        method = entity.method,
        reference = entity.reference,
        status = entity.status,
        receivedBy = entity.receivedBy,
        receivedAt = entity.receivedAt,
        createdAt = entity.createdAt,
      )
  }
}

data class InvoiceDetailView(
  val invoice: InvoiceView,
  val lines: List<InvoiceLineView>,
  val payments: List<PaymentView>,
) {
  companion object {
    fun from(
      invoice: Invoice,
      lines: List<InvoiceLine>,
      payments: List<Payment>,
    ) = InvoiceDetailView(
      invoice = InvoiceView.from(invoice),
      lines = lines.map { InvoiceLineView.from(it) },
      payments = payments.map { PaymentView.from(it) },
    )
  }
}

data class CancelInvoiceDto(
  val reason: String? = null,
)

// 03.02 billing engine surfaces.
data class RateCardDto(
  val id: UUID? = null,
  val hospitalId: UUID? = null,
  val payerClass: String,
  val code: String,
  val priceQar: java.math.BigDecimal,
  val isActive: Boolean = true,
) {
  companion object {
    fun from(entity: com.squamed.core.billing.domain.RateCard) =
      RateCardDto(entity.id, entity.hospitalId, entity.payerClass, entity.code, entity.priceQar, entity.isActive)
  }
}

data class PackageDto(
  val id: UUID? = null,
  val hospitalId: UUID? = null,
  val code: String,
  val name: String,
  val isActive: Boolean = true,
  val versions: List<PackageVersionDto> = emptyList(),
) {
  companion object {
    fun from(
      entity: com.squamed.core.billing.domain.BillingPackage,
      versions: List<com.squamed.core.billing.domain.PackageVersion>,
    ) = PackageDto(
      entity.id,
      entity.hospitalId,
      entity.code,
      entity.name,
      entity.isActive,
      versions.map { PackageVersionDto.from(it) },
    )
  }
}

data class PackageVersionDto(
  val id: UUID? = null,
  val packageId: UUID,
  val packageVersion: Int,
  val priceQar: java.math.BigDecimal,
  val effectiveFrom: java.time.LocalDate? = null,
  val isActive: Boolean = true,
) {
  companion object {
    fun from(entity: com.squamed.core.billing.domain.PackageVersion) =
      PackageVersionDto(entity.id, entity.packageId, entity.packageVersion, entity.priceQar, entity.effectiveFrom, entity.isActive)
  }
}

data class PackageVersionRequestDto(
  val packageVersion: Int? = null,
  val priceQar: java.math.BigDecimal? = null,
  val effectiveFrom: java.time.LocalDate? = null,
)

data class EstimateItemDto(
  val code: String,
  val name: String,
  val quantity: Int = 1,
  val unitPriceQar: java.math.BigDecimal,
)

data class EstimateRequestDto(
  val patientId: UUID,
  val encounterId: UUID? = null,
  val hospitalId: UUID? = null,
  val items: List<EstimateItemDto> = emptyList(),
  val expiresInDays: Int? = null,
)

data class EstimateView(
  val id: UUID?,
  val hospitalId: UUID,
  val patientId: UUID,
  val encounterId: UUID?,
  val estimateNumber: String,
  val totalQar: java.math.BigDecimal,
  val status: String,
  val expiresAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.billing.domain.Estimate) =
      EstimateView(
        entity.id,
        entity.hospitalId,
        entity.patientId,
        entity.encounterId,
        entity.estimateNumber,
        entity.totalQar,
        entity.status,
        entity.expiresAt,
        entity.createdAt,
      )
  }
}

data class DepositRequestDto(
  val patientId: UUID,
  val hospitalId: UUID? = null,
  val amountQar: java.math.BigDecimal,
  val method: String,
  val reference: String? = null,
)

data class DepositApplyDto(
  val invoiceId: UUID,
)

data class DepositView(
  val id: UUID?,
  val hospitalId: UUID,
  val patientId: UUID,
  val amountQar: java.math.BigDecimal,
  val method: String,
  val reference: String?,
  val status: String,
  val receivedBy: UUID?,
  val receivedAt: Instant,
  val appliedInvoiceId: UUID?,
  val appliedAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.billing.domain.Deposit) =
      DepositView(
        entity.id,
        entity.hospitalId,
        entity.patientId,
        entity.amountQar,
        entity.method,
        entity.reference,
        entity.status,
        entity.receivedBy,
        entity.receivedAt,
        entity.appliedInvoiceId,
        entity.appliedAt,
      )
  }
}
