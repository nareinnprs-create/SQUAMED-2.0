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
        createdAt = entity.createdAt,
      )
  }
}

data class IssueInvoiceDto(
  val patientId: UUID? = null,
  val visitId: UUID? = null,
  val discountQar: BigDecimal? = null,
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
) {
  companion object {
    fun from(entity: InvoiceLine) =
      InvoiceLineView(
        description = entity.description,
        quantity = entity.quantity,
        unitPriceQar = entity.unitPriceQar,
        totalQar = entity.totalQar,
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
