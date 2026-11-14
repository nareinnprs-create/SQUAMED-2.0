package com.squamed.core.billing.service

import com.squamed.core.billing.api.ChargeView
import com.squamed.core.billing.api.InvoiceDetailView
import com.squamed.core.billing.api.InvoiceView
import com.squamed.core.billing.api.PaymentCreateDto
import com.squamed.core.billing.api.PaymentView
import com.squamed.core.billing.api.PostChargeDto
import com.squamed.core.billing.api.TariffDto
import com.squamed.core.billing.domain.Charge
import com.squamed.core.billing.domain.Invoice
import com.squamed.core.billing.domain.InvoiceLine
import com.squamed.core.billing.domain.Payment
import com.squamed.core.billing.domain.TariffItem
import com.squamed.core.billing.repository.ChargeRepository
import com.squamed.core.billing.repository.InvoiceLineRepository
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.billing.repository.PaymentRepository
import com.squamed.core.billing.repository.TariffItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.Year
import java.util.UUID

@Service
class BillingService(
  private val tariffItemRepository: TariffItemRepository,
  private val chargeRepository: ChargeRepository,
  private val invoiceRepository: InvoiceRepository,
  private val invoiceLineRepository: InvoiceLineRepository,
  private val paymentRepository: PaymentRepository,
) {
  @Transactional
  fun createTariff(dto: TariffDto): TariffDto {
    val tariffItem =
      tariffItemRepository.save(
        TariffItem(
          hospitalId = dto.hospitalId,
          code = dto.code ?: throw IllegalArgumentException("TARIFF_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("TARIFF_NAME_REQUIRED"),
          category = dto.category,
          priceQar = dto.priceQar ?: BigDecimal.ZERO,
          insuranceApplicable = dto.insuranceApplicable ?: true,
          isActive = dto.isActive ?: true,
        ),
      )
    return TariffDto.from(tariffItem)
  }

  @Transactional(readOnly = true)
  fun listTariffs(
    search: String?,
    activeOnly: Boolean?,
  ): List<TariffDto> {
    val tariffs =
      when {
        search != null -> tariffItemRepository.findByCode(search) + tariffItemRepository.findByNameContainingIgnoreCase(search)
        activeOnly == true -> tariffItemRepository.findByIsActiveTrue()
        else -> tariffItemRepository.findAll()
      }
    return tariffs
      .filter { activeOnly != true || it.isActive }
      .distinctBy { it.id }
      .map { TariffDto.from(it) }
  }

  @Transactional
  fun updateTariff(
    id: UUID,
    dto: TariffDto,
  ): TariffDto {
    val tariffItem =
      tariffItemRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("TARIFF_NOT_FOUND") }
    tariffItem.hospitalId = dto.hospitalId ?: tariffItem.hospitalId
    tariffItem.code = dto.code ?: tariffItem.code
    tariffItem.name = dto.name ?: tariffItem.name
    tariffItem.category = dto.category ?: tariffItem.category
    tariffItem.priceQar = dto.priceQar ?: tariffItem.priceQar
    tariffItem.insuranceApplicable = dto.insuranceApplicable ?: tariffItem.insuranceApplicable
    tariffItem.isActive = dto.isActive ?: tariffItem.isActive
    tariffItemRepository.save(tariffItem)
    return TariffDto.from(tariffItem)
  }

  @Transactional
  fun postCharge(
    dto: PostChargeDto,
    postedBy: UUID? = null,
    hospitalId: UUID,
  ): ChargeView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("PATIENT_ID_REQUIRED")
    val quantity = dto.quantity ?: 1
    val unitPrice = dto.unitPriceQar ?: BigDecimal.ZERO
    val charge =
      chargeRepository.save(
        Charge(
          hospitalId = hospitalId,
          patientId = patientId,
          visitId = dto.visitId,
          encounterId = dto.encounterId,
          chargeCode = dto.chargeCode,
          chargeName = dto.chargeName ?: throw IllegalArgumentException("CHARGE_NAME_REQUIRED"),
          category = dto.category,
          quantity = quantity,
          unitPriceQar = unitPrice,
          totalQar = unitPrice.multiply(BigDecimal.valueOf(quantity.toLong())),
          status = "posted",
          insuranceClaimable = dto.insuranceClaimable ?: true,
          postedBy = postedBy,
          postedAt = Instant.now(),
        ),
      )
    return ChargeView.from(charge)
  }

  @Transactional(readOnly = true)
  fun listCharges(
    patientId: UUID?,
    visitId: UUID?,
  ): List<ChargeView> {
    val charges =
      when {
        patientId != null -> chargeRepository.findByPatientIdOrderByPostedAtDesc(patientId)
        visitId != null -> chargeRepository.findByVisitIdOrderByPostedAtDesc(visitId)
        else -> chargeRepository.findAll().sortedByDescending { it.postedAt }
      }
    return charges.map { ChargeView.from(it) }
  }

  @Transactional
  fun issueInvoice(
    patientId: UUID,
    visitId: UUID?,
    discountQar: BigDecimal?,
  ): InvoiceDetailView {
    if (visitId != null && invoiceRepository.findByVisitId(visitId).any { it.status == "unpaid" }) {
      throw IllegalArgumentException("INVOICE_ALREADY_EXISTS")
    }
    val charges =
      if (visitId != null) {
        chargeRepository.findByVisitId(visitId).filter { it.status == "posted" }
      } else {
        chargeRepository.findByPatientId(patientId).filter { it.status == "posted" }
      }
    if (charges.isEmpty()) {
      throw IllegalArgumentException("NO_CHARGES_TO_INVOICE")
    }
    val now = Instant.now()
    val subtotal = charges.fold(BigDecimal.ZERO) { acc, charge -> acc.add(charge.totalQar) }
    val discount = discountQar ?: BigDecimal.ZERO
    val tax = BigDecimal.ZERO
    val total = subtotal.subtract(discount).add(tax).coerceAtLeast(BigDecimal.ZERO)
    val invoice =
      invoiceRepository.save(
        Invoice(
          hospitalId = charges.firstNotNullOfOrNull { it.hospitalId },
          visitId = visitId,
          patientId = patientId,
          invoiceNumber = nextInvoiceNumber(),
          subtotalQar = subtotal,
          discountQar = discount,
          taxQar = tax,
          totalQar = total,
          amountPaidQar = BigDecimal.ZERO,
          amountDueQar = total,
          status = "unpaid",
          issuedAt = now,
        ),
      )
    val invoiceId = invoice.id ?: throw IllegalStateException("INVOICE_ID_MISSING")
    charges.forEach { charge ->
      invoiceLineRepository.save(
        InvoiceLine(
          invoiceId = invoiceId,
          chargeId = charge.id,
          description = charge.chargeName,
          quantity = charge.quantity,
          unitPriceQar = charge.unitPriceQar,
          totalQar = charge.totalQar,
        ),
      )
      charge.status = "invoiced"
      chargeRepository.save(charge)
    }
    return getInvoice(invoiceId)
  }

  @Transactional(readOnly = true)
  fun getInvoice(id: UUID): InvoiceDetailView {
    val invoice = findInvoice(id)
    return InvoiceDetailView.from(
      invoice = invoice,
      lines = invoiceLineRepository.findByInvoiceId(id),
      payments = paymentRepository.findByInvoiceId(id),
    )
  }

  @Transactional(readOnly = true)
  fun listInvoices(
    patientId: UUID?,
    status: String?,
  ): List<InvoiceView> {
    val invoices =
      when {
        patientId != null -> invoiceRepository.findByPatientId(patientId)
        status != null -> invoiceRepository.findByStatus(status)
        else -> invoiceRepository.findAll()
      }
    return invoices
      .sortedByDescending { it.issuedAt }
      .map { InvoiceView.from(it) }
  }

  @Transactional
  fun recordPayment(
    invoiceId: UUID,
    dto: PaymentCreateDto,
    receivedBy: UUID? = null,
  ): PaymentView {
    val invoice = findInvoice(invoiceId)
    val method = dto.method ?: throw IllegalArgumentException("PAYMENT_METHOD_REQUIRED")
    val amount = dto.amountQar ?: throw IllegalArgumentException("PAYMENT_AMOUNT_REQUIRED")
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw IllegalArgumentException("INVALID_PAYMENT_AMOUNT")
    }
    val payment =
      paymentRepository.save(
        Payment(
          invoiceId = invoiceId,
          patientId = invoice.patientId,
          amountQar = amount,
          method = method,
          reference = dto.reference,
          status = "completed",
          receivedBy = receivedBy,
          receivedAt = Instant.now(),
        ),
      )
    val amountPaid = invoice.amountPaidQar.add(amount)
    val amountDue = invoice.totalQar.subtract(amountPaid).coerceAtLeast(BigDecimal.ZERO)
    invoice.amountPaidQar = amountPaid
    invoice.amountDueQar = amountDue
    invoice.status =
      when {
        amountDue.compareTo(BigDecimal.ZERO) <= 0 -> "paid"
        amountPaid.compareTo(BigDecimal.ZERO) > 0 -> "partial"
        else -> invoice.status
      }
    invoiceRepository.save(invoice)
    return PaymentView.from(payment)
  }

  @Transactional
  fun cancelInvoice(
    invoiceId: UUID,
    reason: String?,
  ): InvoiceDetailView {
    val invoice = findInvoice(invoiceId)
    if (invoice.amountPaidQar.compareTo(BigDecimal.ZERO) != 0) {
      throw IllegalArgumentException("INVOICE_HAS_PAYMENTS")
    }
    invoice.status = "cancelled"
    invoiceRepository.save(invoice)
    invoiceLineRepository
      .findByInvoiceId(invoiceId)
      .mapNotNull { it.chargeId }
      .forEach { chargeId ->
        chargeRepository.findById(chargeId).ifPresent { charge ->
          if (charge.status == "invoiced") {
            charge.status = "posted"
            chargeRepository.save(charge)
          }
        }
      }
    return getInvoice(invoiceId)
  }

  private fun findInvoice(id: UUID): Invoice = invoiceRepository.findById(id).orElseThrow { IllegalArgumentException("INVOICE_NOT_FOUND") }

  private fun nextInvoiceNumber(): String {
    val year = Year.now().value
    val latest = invoiceRepository.findTopByOrderByInvoiceNumberDesc()
    val sequence =
      latest
        ?.invoiceNumber
        ?.split("-")
        ?.lastOrNull()
        ?.toIntOrNull() ?: 0
    return String.format("INV-%d-%04d", year, sequence + 1)
  }
}
