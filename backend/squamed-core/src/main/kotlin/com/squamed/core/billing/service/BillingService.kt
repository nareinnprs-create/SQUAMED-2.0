package com.squamed.core.billing.service

import com.squamed.core.billing.api.CashupCloseDto
import com.squamed.core.billing.api.CashupOpenDto
import com.squamed.core.billing.api.CashupView
import com.squamed.core.billing.api.ChargeView
import com.squamed.core.billing.api.CreditNoteRequestDto
import com.squamed.core.billing.api.CreditNoteView
import com.squamed.core.billing.api.DepositRequestDto
import com.squamed.core.billing.api.DepositView
import com.squamed.core.billing.api.EstimateRequestDto
import com.squamed.core.billing.api.EstimateView
import com.squamed.core.billing.api.InvoiceDetailView
import com.squamed.core.billing.api.InvoiceView
import com.squamed.core.billing.api.JournalExportView
import com.squamed.core.billing.api.JournalLineView
import com.squamed.core.billing.api.PackageDto
import com.squamed.core.billing.api.PackageVersionDto
import com.squamed.core.billing.api.PackageVersionRequestDto
import com.squamed.core.billing.api.PaymentCreateDto
import com.squamed.core.billing.api.PaymentView
import com.squamed.core.billing.api.PostChargeDto
import com.squamed.core.billing.api.RateCardDto
import com.squamed.core.billing.api.ReconRequestDto
import com.squamed.core.billing.api.ReconView
import com.squamed.core.billing.api.RefundRequestDto
import com.squamed.core.billing.api.RefundView
import com.squamed.core.billing.api.TariffDto
import com.squamed.core.billing.api.TaxProfileDto
import com.squamed.core.billing.domain.BillingPackage
import com.squamed.core.billing.domain.Charge
import com.squamed.core.billing.domain.CreditNote
import com.squamed.core.billing.domain.Deposit
import com.squamed.core.billing.domain.Estimate
import com.squamed.core.billing.domain.HospitalTaxProfile
import com.squamed.core.billing.domain.Invoice
import com.squamed.core.billing.domain.InvoiceLine
import com.squamed.core.billing.domain.PackageVersion
import com.squamed.core.billing.domain.Payment
import com.squamed.core.billing.domain.RateCard
import com.squamed.core.billing.domain.ReconStatement
import com.squamed.core.billing.domain.Refund
import com.squamed.core.billing.domain.ShiftCashup
import com.squamed.core.billing.domain.TariffItem
import com.squamed.core.billing.domain.TaxLine
import com.squamed.core.billing.repository.BillingPackageRepository
import com.squamed.core.billing.repository.ChargeRepository
import com.squamed.core.billing.repository.CreditNoteRepository
import com.squamed.core.billing.repository.DepositRepository
import com.squamed.core.billing.repository.EstimateRepository
import com.squamed.core.billing.repository.HospitalTaxProfileRepository
import com.squamed.core.billing.repository.InvoiceLineRepository
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.billing.repository.PackageVersionRepository
import com.squamed.core.billing.repository.PaymentRepository
import com.squamed.core.billing.repository.RateCardRepository
import com.squamed.core.billing.repository.ReconStatementRepository
import com.squamed.core.billing.repository.RefundRepository
import com.squamed.core.billing.repository.ShiftCashupRepository
import com.squamed.core.billing.repository.TariffItemRepository
import com.squamed.core.billing.repository.TaxLineRepository
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.identity.security.SquamedPermissionEvaluator
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
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
  private val rateCardRepository: RateCardRepository,
  private val billingPackageRepository: BillingPackageRepository,
  private val packageVersionRepository: PackageVersionRepository,
  private val estimateRepository: EstimateRepository,
  private val depositRepository: DepositRepository,
  private val hospitalTaxProfileRepository: HospitalTaxProfileRepository,
  private val taxLineRepository: TaxLineRepository,
  private val creditNoteRepository: CreditNoteRepository,
  private val shiftCashupRepository: ShiftCashupRepository,
  private val reconStatementRepository: ReconStatementRepository,
  private val refundRepository: RefundRepository,
  private val taskService: TaskService,
  private val permissionEvaluator: SquamedPermissionEvaluator,
) {
  private val payerClasses = setOf("general", "cghs", "esic", "tpa", "corporate", "pmjay")
  private val discountApprovalThresholdPct = BigDecimal("10")

  // 03.03: healthcare services are exempt; pharmacy goods 5%, devices 12%.
  private fun defaultTaxCategory(category: String?): String =
    when (category?.trim()?.uppercase()) {
      "PHARMACY", "GOODS" -> "GOODS_GST_5"
      "DEVICE", "DEVICES" -> "GOODS_GST_12"
      else -> "EXEMPT_SERVICE"
    }

  private fun defaultHsn(taxCategory: String): String =
    when (taxCategory) {
      "GOODS_GST_5" -> "3004"
      "GOODS_GST_12" -> "9018"
      else -> "9993"
    }

  private fun taxRatePct(taxCategory: String): BigDecimal =
    when (taxCategory) {
      "GOODS_GST_5" -> BigDecimal("5")
      "GOODS_GST_12" -> BigDecimal("12")
      else -> BigDecimal.ZERO
    }

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
    hospitalId: UUID?,
  ): List<TariffDto> {
    val tariffs =
      when {
        search != null -> tariffItemRepository.findByCode(hospitalId, search) + tariffItemRepository.findByNameContainingIgnoreCase(hospitalId, search)
        activeOnly == true -> tariffItemRepository.findByIsActiveTrue(hospitalId)
        else -> tariffItemRepository.findAllForHospital(hospitalId)
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
    // 03.01: manual ledger entries are audited and must carry a reason.
    val reason = dto.reason?.trim()
    if (reason.isNullOrEmpty()) throw IllegalArgumentException("CHARGE_REASON_REQUIRED")
    // 03.02: a package code pins the active version at capture time.
    var unitPrice = dto.unitPriceQar ?: BigDecimal.ZERO
    var packageVersionId: UUID? = null
    if (dto.packageCode != null) {
      val pkg = billingPackageRepository.findByCode(dto.packageCode) ?: throw IllegalArgumentException("PACKAGE_NOT_FOUND")
      val version =
        packageVersionRepository
          .findByPackageIdOrderByPackageVersionDesc(pkg.id!!)
          .firstOrNull { it.isActive }
          ?: throw IllegalArgumentException("PACKAGE_VERSION_NOT_FOUND")
      unitPrice = version.priceQar
      packageVersionId = version.id
    }
    val quantity = dto.quantity ?: 1
    val taxCategory = dto.taxCategory?.trim()?.uppercase() ?: defaultTaxCategory(dto.category)
    if (taxCategory !in setOf("EXEMPT_SERVICE", "GOODS_GST_5", "GOODS_GST_12")) {
      throw IllegalArgumentException("TAX_CATEGORY_INVALID")
    }
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
          entryMode = "MANUAL",
          entryReason = reason,
          packageVersionId = packageVersionId,
          taxCategory = taxCategory,
          hsnCode = dto.hsnCode?.trim() ?: defaultHsn(taxCategory),
        ),
      )
    return ChargeView.from(charge)
  }

  @Transactional(readOnly = true)
  fun listCharges(
    patientId: UUID?,
    visitId: UUID?,
    hospitalId: UUID?,
  ): List<ChargeView> {
    val charges =
      when {
        patientId != null -> chargeRepository.findByPatientIdOrderByPostedAtDesc(hospitalId, patientId)
        visitId != null -> chargeRepository.findByVisitIdOrderByPostedAtDesc(hospitalId, visitId)
        else -> chargeRepository.findAllForHospital(hospitalId).sortedByDescending { it.postedAt }
      }
    return charges.map { ChargeView.from(it) }
  }

  @Transactional
  fun issueInvoice(
    patientId: UUID,
    visitId: UUID?,
    discountQar: BigDecimal?,
    payerClass: String?,
    discountReason: String?,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): InvoiceDetailView {
    if (visitId != null && invoiceRepository.findByVisitId(hospitalId, visitId).any { it.status == "unpaid" }) {
      throw IllegalArgumentException("INVOICE_ALREADY_EXISTS")
    }
    val charges =
      if (visitId != null) {
        chargeRepository.findByVisitId(hospitalId, visitId).filter { it.status == "posted" }
      } else {
        chargeRepository.findByPatientId(hospitalId, patientId).filter { it.status == "posted" }
      }
    if (charges.isEmpty()) {
      throw IllegalArgumentException("NO_CHARGES_TO_INVOICE")
    }
    val payer = (payerClass ?: "general").lowercase()
    if (payer !in payerClasses) throw IllegalArgumentException("PAYER_CLASS_INVALID")
    val hid = charges.firstNotNullOfOrNull { it.hospitalId } ?: hospitalId
    // 03.02: rate cards reprice each line for the payer class; the captured base price is kept.
    val priced =
      charges.map { charge ->
        val card =
          charge.chargeCode
            ?.let { code -> hid?.let { h -> rateCardRepository.findActive(h, payer, code).firstOrNull() } }
        Triple(charge, card?.priceQar ?: charge.unitPriceQar, card)
      }
    val now = Instant.now()
    val subtotal = priced.fold(BigDecimal.ZERO) { acc, (charge, unit, _) -> acc.add(unit.multiply(BigDecimal.valueOf(charge.quantity.toLong()))) }
    val discount = discountQar ?: BigDecimal.ZERO
    val reason = discountReason?.trim()
    if (discount.compareTo(BigDecimal.ZERO) > 0) {
      if (reason.isNullOrEmpty()) throw IllegalArgumentException("DISCOUNT_REASON_REQUIRED")
      val pct =
        if (subtotal.compareTo(BigDecimal.ZERO) == 0) {
          BigDecimal.ZERO
        } else {
          discount.multiply(BigDecimal("100")).divide(subtotal, 2, RoundingMode.HALF_UP)
        }
      if (pct > discountApprovalThresholdPct && !permissionEvaluator.check(principal, "billing:approve:discount")) {
        throw IllegalArgumentException("DISCOUNT_APPROVAL_REQUIRED")
      }
    }
    // 03.03: tax per HSN/category group; exempt services contribute a 0% line.
    val taxGroups =
      priced.groupBy { (charge, _, _) -> (charge.hsnCode ?: defaultHsn(charge.taxCategory)) to charge.taxCategory }
    val taxLines =
      taxGroups.map { (key, rows) ->
        val taxable =
          rows.fold(BigDecimal.ZERO) { acc, (charge, unit, _) ->
            acc.add(unit.multiply(BigDecimal.valueOf(charge.quantity.toLong())))
          }
        val rate = taxRatePct(key.second)
        TaxLine(
          invoiceId = UUID.randomUUID(),
          hsnCode = key.first,
          taxCategory = key.second,
          taxableQar = taxable,
          ratePct = rate,
          taxQar = taxable.multiply(rate).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP),
          isExempt = rate.compareTo(BigDecimal.ZERO) == 0,
        )
      }
    val tax = taxLines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.taxQar) }
    val profile = hid?.let { hospitalTaxProfileRepository.findByHospitalId(it) }
    val eInvoice =
      profile?.eInvoiceThresholdQar?.let { subtotal.subtract(discount).add(tax).compareTo(it) >= 0 } ?: false
    val total = subtotal.subtract(discount).add(tax).coerceAtLeast(BigDecimal.ZERO)
    val invoice =
      invoiceRepository.save(
        Invoice(
          hospitalId = hid,
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
          payerClass = payer,
          discountReason = reason,
          eInvoiceRequired = eInvoice,
          issuedAt = now,
        ),
      )
    val invoiceId = invoice.id ?: throw IllegalStateException("INVOICE_ID_MISSING")
    taxLines.forEach {
      it.invoiceId = invoiceId
      taxLineRepository.save(it)
    }
    priced.forEach { (charge, unit, card) ->
      invoiceLineRepository.save(
        InvoiceLine(
          invoiceId = invoiceId,
          chargeId = charge.id,
          description = charge.chargeName,
          quantity = charge.quantity,
          unitPriceQar = unit,
          totalQar = unit.multiply(BigDecimal.valueOf(charge.quantity.toLong())),
          basePriceQar = charge.unitPriceQar,
          rateCardId = card?.id,
        ),
      )
      charge.status = "invoiced"
      chargeRepository.save(charge)
    }
    return getInvoice(invoiceId, hospitalId)
  }

  @Transactional(readOnly = true)
  fun getInvoice(
    id: UUID,
    hospitalId: UUID?,
  ): InvoiceDetailView {
    val invoice = findInvoice(id, hospitalId)
    return InvoiceDetailView.from(
      invoice = invoice,
      lines = invoiceLineRepository.findByInvoiceId(id),
      payments = paymentRepository.findByInvoiceId(id),
      taxLines = taxLineRepository.findByInvoiceId(id),
      creditNotes = creditNoteRepository.findByInvoiceId(id),
    )
  }

  @Transactional(readOnly = true)
  fun listInvoices(
    patientId: UUID?,
    status: String?,
    hospitalId: UUID?,
  ): List<InvoiceView> {
    val invoices =
      when {
        patientId != null -> invoiceRepository.findByPatientId(hospitalId, patientId)
        status != null -> invoiceRepository.findByStatus(hospitalId, status)
        else -> invoiceRepository.findAllForHospital(hospitalId)
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
    hospitalId: UUID?,
  ): PaymentView {
    val invoice = findInvoice(invoiceId, hospitalId)
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
    hospitalId: UUID?,
  ): InvoiceDetailView {
    val invoice = findInvoice(invoiceId, hospitalId)
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
    return getInvoice(invoiceId, hospitalId)
  }

  private fun findInvoice(
    id: UUID,
    hospitalId: UUID?,
  ): Invoice {
    val invoice = invoiceRepository.findById(id).orElseThrow { IllegalArgumentException("INVOICE_NOT_FOUND") }
    requireSameHospital(hospitalId, invoice.hospitalId) // 00.04 tenant sweep
    return invoice
  }

  // -- 03.02 rate cards: one active price per (hospital, payer class, code); re-posting updates it. --

  @Transactional
  fun upsertRateCard(
    dto: RateCardDto,
    hospitalId: UUID,
  ): RateCardDto {
    val payer = dto.payerClass.lowercase()
    if (payer !in payerClasses) throw IllegalArgumentException("PAYER_CLASS_INVALID")
    if (dto.code.isBlank()) throw IllegalArgumentException("RATE_CARD_CODE_REQUIRED")
    if (dto.priceQar.compareTo(BigDecimal.ZERO) < 0) throw IllegalArgumentException("RATE_CARD_PRICE_INVALID")
    val existing = rateCardRepository.findActive(hospitalId, payer, dto.code.trim()).firstOrNull()
    val card =
      existing ?: RateCard(hospitalId = hospitalId, payerClass = payer, code = dto.code.trim())
    card.priceQar = dto.priceQar
    card.isActive = dto.isActive
    return RateCardDto.from(rateCardRepository.save(card))
  }

  @Transactional(readOnly = true)
  fun listRateCards(hospitalId: UUID?): List<RateCardDto> = rateCardRepository.findAllForHospital(hospitalId).map { RateCardDto.from(it) }

  // -- 03.02 packages: header + immutable versions; capture pins the version. --

  @Transactional
  fun createPackage(
    dto: PackageDto,
    hospitalId: UUID?,
  ): PackageDto {
    if (dto.code.isBlank()) throw IllegalArgumentException("PACKAGE_CODE_REQUIRED")
    if (billingPackageRepository.findByCode(dto.code.trim()) != null) throw IllegalArgumentException("PACKAGE_CODE_EXISTS")
    val saved = billingPackageRepository.save(BillingPackage(hospitalId = hospitalId, code = dto.code.trim(), name = dto.name))
    return PackageDto.from(saved, emptyList())
  }

  @Transactional
  fun addPackageVersion(
    packageId: UUID,
    dto: PackageVersionRequestDto,
  ): PackageVersionDto {
    val pkg = billingPackageRepository.findById(packageId).orElseThrow { IllegalArgumentException("PACKAGE_NOT_FOUND") }
    val price = dto.priceQar ?: throw IllegalArgumentException("PACKAGE_PRICE_REQUIRED")
    if (price.compareTo(BigDecimal.ZERO) < 0) throw IllegalArgumentException("PACKAGE_PRICE_INVALID")
    val versions = packageVersionRepository.findByPackageIdOrderByPackageVersionDesc(pkg.id!!)
    val nextVersion = dto.packageVersion ?: ((versions.firstOrNull()?.packageVersion ?: 0) + 1)
    if (versions.any { it.packageVersion == nextVersion }) throw IllegalArgumentException("PACKAGE_VERSION_EXISTS")
    val saved =
      packageVersionRepository.save(
        PackageVersion(
          packageId = pkg.id!!,
          packageVersion = nextVersion,
          priceQar = price,
          effectiveFrom = dto.effectiveFrom,
        ),
      )
    return PackageVersionDto.from(saved)
  }

  @Transactional(readOnly = true)
  fun listPackages(): List<PackageDto> =
    billingPackageRepository.findAllByIsActiveTrue().map { pkg ->
      PackageDto.from(pkg, packageVersionRepository.findByPackageIdOrderByPackageVersionDesc(pkg.id!!))
    }

  // -- 03.02 estimates: quoted before service. --

  @Transactional
  fun createEstimate(
    dto: EstimateRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID,
  ): EstimateView {
    if (dto.items.isEmpty()) throw IllegalArgumentException("ESTIMATE_ITEMS_REQUIRED")
    val total =
      dto.items.fold(BigDecimal.ZERO) { acc, item ->
        if (item.quantity <= 0) throw IllegalArgumentException("ESTIMATE_QUANTITY_INVALID")
        acc.add(item.unitPriceQar.multiply(BigDecimal.valueOf(item.quantity.toLong())))
      }
    val saved =
      estimateRepository.save(
        Estimate(
          hospitalId = hospitalId,
          patientId = dto.patientId,
          encounterId = dto.encounterId,
          estimateNumber = nextEstimateNumber(),
          itemsJson =
            com.fasterxml.jackson.databind
              .ObjectMapper()
              .writeValueAsString(dto.items),
          totalQar = total,
          expiresAt = dto.expiresInDays?.let { Instant.now().plusSeconds(it.toLong() * 86_400) },
        ),
      )
    return EstimateView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listEstimates(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<EstimateView> {
    val rows =
      if (patientId != null) estimateRepository.findByPatient(hospitalId, patientId) else estimateRepository.findAllForHospital(hospitalId)
    return rows.map { EstimateView.from(it) }
  }

  // -- 03.02 deposits: held money applied to one invoice. --

  @Transactional
  fun takeDeposit(
    dto: DepositRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID,
  ): DepositView {
    if (dto.amountQar.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("DEPOSIT_AMOUNT_INVALID")
    if (dto.method.isBlank()) throw IllegalArgumentException("DEPOSIT_METHOD_REQUIRED")
    val saved =
      depositRepository.save(
        Deposit(
          hospitalId = hospitalId,
          patientId = dto.patientId,
          amountQar = dto.amountQar,
          method = dto.method,
          reference = dto.reference,
          receivedBy = principal.userId,
          receivedAt = Instant.now(),
        ),
      )
    return DepositView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listDeposits(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<DepositView> {
    val rows =
      if (patientId != null) depositRepository.findByPatient(hospitalId, patientId) else depositRepository.findAllForHospital(hospitalId)
    return rows.map { DepositView.from(it) }
  }

  @Transactional
  fun applyDeposit(
    depositId: UUID,
    invoiceId: UUID,
    hospitalId: UUID?,
  ): DepositView {
    val deposit = depositRepository.findById(depositId).orElseThrow { IllegalArgumentException("DEPOSIT_NOT_FOUND") }
    requireSameHospital(hospitalId, deposit.hospitalId)
    if (deposit.status != "held") throw IllegalArgumentException("DEPOSIT_NOT_HELD")
    val invoice = findInvoice(invoiceId, hospitalId)
    if (invoice.patientId != deposit.patientId) throw IllegalArgumentException("DEPOSIT_PATIENT_MISMATCH")
    if (invoice.status == "cancelled") throw IllegalArgumentException("INVOICE_CANCELLED")
    val applied = deposit.amountQar.min(invoice.amountDueQar)
    val amountPaid = invoice.amountPaidQar.add(applied)
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
    deposit.status = "applied"
    deposit.appliedInvoiceId = invoiceId
    deposit.appliedAt = Instant.now()
    return DepositView.from(depositRepository.save(deposit))
  }

  private fun nextEstimateNumber(): String {
    val year = Year.now().value
    val count = estimateRepository.count()
    return String.format("EST-%d-%05d", year, count + 1)
  }

  // -- 03.03 tax profile + credit notes. --

  @Transactional
  fun upsertTaxProfile(
    dto: TaxProfileDto,
    hospitalId: UUID,
  ): TaxProfileDto {
    val profile = hospitalTaxProfileRepository.findByHospitalId(hospitalId) ?: HospitalTaxProfile(hospitalId = hospitalId)
    profile.gstin = dto.gstin
    profile.stateCode = dto.stateCode
    profile.isGstRegistered = dto.isGstRegistered
    profile.eInvoiceThresholdQar = dto.eInvoiceThresholdQar
    return TaxProfileDto.from(hospitalTaxProfileRepository.save(profile))
  }

  @Transactional(readOnly = true)
  fun getTaxProfile(hospitalId: UUID): TaxProfileDto? = hospitalTaxProfileRepository.findByHospitalId(hospitalId)?.let { TaxProfileDto.from(it) }

  @Transactional
  fun issueCreditNote(
    invoiceId: UUID,
    dto: CreditNoteRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): CreditNoteView {
    val invoice = findInvoice(invoiceId, hospitalId)
    if (invoice.status == "cancelled") throw IllegalArgumentException("INVOICE_CANCELLED")
    if (dto.amountQar.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("CREDIT_NOTE_AMOUNT_INVALID")
    if (dto.amountQar.compareTo(invoice.totalQar) > 0) throw IllegalArgumentException("CREDIT_NOTE_EXCEEDS_INVOICE")
    val reason = dto.reason.trim()
    if (reason.isEmpty()) throw IllegalArgumentException("CREDIT_NOTE_REASON_REQUIRED")
    val note =
      creditNoteRepository.save(
        CreditNote(
          hospitalId = invoice.hospitalId ?: hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED"),
          invoiceId = invoiceId,
          noteNumber = nextCreditNoteNumber(),
          amountQar = dto.amountQar,
          reason = reason,
          issuedBy = principal.userId,
          issuedAt = Instant.now(),
        ),
      )
    // A credit note reduces the billable total; corrections never edit finalized lines.
    invoice.totalQar = invoice.totalQar.subtract(dto.amountQar).coerceAtLeast(BigDecimal.ZERO)
    invoice.amountDueQar = invoice.totalQar.subtract(invoice.amountPaidQar).coerceAtLeast(BigDecimal.ZERO)
    invoice.status =
      when {
        invoice.amountDueQar.compareTo(BigDecimal.ZERO) <= 0 && invoice.amountPaidQar.compareTo(BigDecimal.ZERO) > 0 -> "paid"
        invoice.amountPaidQar.compareTo(BigDecimal.ZERO) > 0 -> "partial"
        else -> invoice.status
      }
    invoiceRepository.save(invoice)
    return CreditNoteView.from(note)
  }

  private fun nextCreditNoteNumber(): String {
    val year = Year.now().value
    val count = creditNoteRepository.count()
    return String.format("CN-%d-%05d", year, count + 1)
  }

  // -- 03.06 collections: shift cash-up, reconciliation, refunds, GL export. --

  @Transactional
  fun openCashup(
    dto: CashupOpenDto,
    principal: SquamedPrincipal,
    hospitalId: UUID,
  ): CashupView {
    if (dto.openingFloatQar.compareTo(BigDecimal.ZERO) < 0) throw IllegalArgumentException("CASHUP_FLOAT_INVALID")
    if (shiftCashupRepository.findOpenForCashier(hospitalId, principal.userId).isNotEmpty()) {
      throw IllegalArgumentException("CASHUP_ALREADY_OPEN")
    }
    val saved =
      shiftCashupRepository.save(
        ShiftCashup(
          hospitalId = hospitalId,
          cashierId = principal.userId,
          openedAt = Instant.now(),
          openingFloatQar = dto.openingFloatQar,
        ),
      )
    return CashupView.from(saved)
  }

  @Transactional
  fun closeCashup(
    id: UUID,
    dto: CashupCloseDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): CashupView {
    val cashup = shiftCashupRepository.findById(id).orElseThrow { IllegalArgumentException("CASHUP_NOT_FOUND") }
    requireSameHospital(hospitalId, cashup.hospitalId)
    if (cashup.status != "OPEN") throw IllegalArgumentException("CASHUP_NOT_OPEN")
    if (dto.countedCashQar.compareTo(BigDecimal.ZERO) < 0) throw IllegalArgumentException("CASHUP_COUNT_INVALID")
    val now = Instant.now()
    val collected =
      paymentRepository
        .cashForCashier(cashup.cashierId, cashup.openedAt, now)
        .fold(BigDecimal.ZERO) { acc, payment -> acc.add(payment.amountQar) }
    val expected = cashup.openingFloatQar.add(collected)
    val variance = dto.countedCashQar.subtract(expected)
    cashup.cashCollectedQar = collected
    cashup.countedCashQar = dto.countedCashQar
    cashup.varianceQar = variance
    cashup.status = "CLOSED"
    cashup.closedAt = now
    cashup.note = dto.note
    val saved = shiftCashupRepository.save(cashup)
    // Drawer variance beyond the tolerance raises a task for the supervisor.
    if (variance.abs() > BigDecimal("50")) {
      taskService.createTask(
        TaskCreateDto(
          hospitalId = cashup.hospitalId,
          title = "Cash drawer variance $variance",
          description = "Cash-up ${cashup.id} closed with variance $variance (expected $expected, counted ${dto.countedCashQar})",
          priority = "high",
          relatedType = "CASHUP",
          relatedId = cashup.id,
        ),
        principal.userId,
      )
    }
    return CashupView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listCashups(hospitalId: UUID?): List<CashupView> = shiftCashupRepository.findAllForHospital(hospitalId).map { CashupView.from(it) }

  @Transactional
  fun reconcile(
    dto: ReconRequestDto,
    hospitalId: UUID,
  ): ReconView {
    if (dto.method.isBlank()) throw IllegalArgumentException("RECON_METHOD_REQUIRED")
    if (!dto.periodEnd.isAfter(dto.periodStart)) throw IllegalArgumentException("RECON_PERIOD_INVALID")
    val recorded =
      paymentRepository
        .findForPeriod(hospitalId, dto.method, dto.periodStart, dto.periodEnd)
        .fold(BigDecimal.ZERO) { acc, payment -> acc.add(payment.amountQar) }
    val variance = dto.statementTotalQar.subtract(recorded)
    val saved =
      reconStatementRepository.save(
        ReconStatement(
          hospitalId = hospitalId,
          method = dto.method,
          periodStart = dto.periodStart,
          periodEnd = dto.periodEnd,
          statementTotalQar = dto.statementTotalQar,
          recordedTotalQar = recorded,
          varianceQar = variance,
          status = if (variance.compareTo(BigDecimal.ZERO) == 0) "MATCHED" else "VARIANCE",
        ),
      )
    return ReconView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listRecon(hospitalId: UUID?): List<ReconView> = reconStatementRepository.findAllForHospital(hospitalId).map { ReconView.from(it) }

  @Transactional
  fun refundPayment(
    paymentId: UUID,
    dto: RefundRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): RefundView {
    val payment = paymentRepository.findById(paymentId).orElseThrow { IllegalArgumentException("PAYMENT_NOT_FOUND") }
    if (payment.status != "completed") throw IllegalArgumentException("PAYMENT_NOT_REFUNDABLE")
    if (dto.amountQar.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("REFUND_AMOUNT_INVALID")
    if (dto.amountQar.compareTo(payment.amountQar) > 0) throw IllegalArgumentException("REFUND_EXCEEDS_PAYMENT")
    val reason = dto.reason.trim()
    if (reason.isEmpty()) throw IllegalArgumentException("REFUND_REASON_REQUIRED")
    val invoice = findInvoice(payment.invoiceId, hospitalId)
    val refund =
      refundRepository.save(
        Refund(
          hospitalId = invoice.hospitalId ?: hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED"),
          paymentId = payment.id!!,
          invoiceId = payment.invoiceId,
          amountQar = dto.amountQar,
          reason = reason,
          issuedBy = principal.userId,
          issuedAt = Instant.now(),
        ),
      )
    payment.status = "refunded"
    paymentRepository.save(payment)
    invoice.amountPaidQar = invoice.amountPaidQar.subtract(dto.amountQar).coerceAtLeast(BigDecimal.ZERO)
    invoice.amountDueQar = invoice.totalQar.subtract(invoice.amountPaidQar).coerceAtLeast(BigDecimal.ZERO)
    invoice.status =
      when {
        invoice.amountDueQar.compareTo(BigDecimal.ZERO) <= 0 && invoice.amountPaidQar.compareTo(BigDecimal.ZERO) > 0 -> "paid"
        invoice.amountPaidQar.compareTo(BigDecimal.ZERO) > 0 -> "partial"
        invoice.status == "paid" -> "unpaid"
        else -> invoice.status
      }
    invoiceRepository.save(invoice)
    return RefundView.from(refund)
  }

  @Transactional(readOnly = true)
  fun listRefunds(hospitalId: UUID?): List<RefundView> = refundRepository.findAllForHospital(hospitalId).map { RefundView.from(it) }

  // Summarized double-entry export per day (payments in, invoices earned, refunds out).
  @Transactional(readOnly = true)
  fun journalExport(
    from: Instant,
    to: Instant,
    hospitalId: UUID?,
  ): JournalExportView {
    if (!to.isAfter(from)) throw IllegalArgumentException("JOURNAL_PERIOD_INVALID")
    val lines = mutableListOf<JournalLineView>()

    fun add(
      at: Instant,
      account: String,
      debit: BigDecimal,
      credit: BigDecimal,
    ) {
      if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) return
      lines += JournalLineView(at.toString().take(10), account, debit, credit)
    }

    val methods = listOf("cash", "upi", "card")
    methods.forEach { method ->
      val account = "CASH_" + method.uppercase() + "_IN"
      paymentRepository.findForPeriod(hospitalId, method, from, to).forEach { payment ->
        add(payment.receivedAt, account, payment.amountQar, BigDecimal.ZERO)
        add(payment.receivedAt, "RECEIVABLE", BigDecimal.ZERO, payment.amountQar)
      }
    }

    invoiceRepository
      .findAllForHospital(hospitalId)
      .filter { !it.issuedAt.isBefore(from) && it.issuedAt.isBefore(to) && it.status != "cancelled" }
      .forEach { invoice ->
        add(invoice.issuedAt, "RECEIVABLE", invoice.totalQar, BigDecimal.ZERO)
        add(invoice.issuedAt, "REVENUE", BigDecimal.ZERO, invoice.totalQar.subtract(invoice.taxQar))
        add(invoice.issuedAt, "TAX_PAYABLE", BigDecimal.ZERO, invoice.taxQar)
      }

    refundRepository.findAllForHospital(hospitalId).filter { !it.issuedAt.isBefore(from) && it.issuedAt.isBefore(to) }.forEach { refund ->
      val method =
        paymentRepository
          .findById(refund.paymentId)
          .orElse(null)
          ?.method
          ?.uppercase() ?: "CASH"
      add(refund.issuedAt, "REVENUE_REFUND", refund.amountQar, BigDecimal.ZERO)
      add(refund.issuedAt, "CASH_${method}_IN", BigDecimal.ZERO, refund.amountQar)
    }

    return JournalExportView(
      from = from,
      to = to,
      lines = lines,
      totalDebitQar = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.debitQar) },
      totalCreditQar = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.creditQar) },
    )
  }

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
