package com.squamed.core.finance.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.billing.repository.HospitalTaxProfileRepository
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.billing.repository.PaymentRepository
import com.squamed.core.billing.repository.RefundRepository
import com.squamed.core.billing.repository.TaxLineRepository
import com.squamed.core.finance.domain.ApInvoice
import com.squamed.core.finance.domain.ApPayment
import com.squamed.core.finance.domain.BankAccount
import com.squamed.core.finance.domain.BankStatementLine
import com.squamed.core.finance.domain.Budget
import com.squamed.core.finance.domain.CostCentre
import com.squamed.core.finance.domain.DepreciationRun
import com.squamed.core.finance.domain.FixedAsset
import com.squamed.core.finance.domain.GlAccount
import com.squamed.core.finance.domain.GstReturn
import com.squamed.core.finance.domain.JournalEntry
import com.squamed.core.finance.domain.JournalLine
import com.squamed.core.finance.domain.PeriodClose
import com.squamed.core.finance.repository.ApInvoiceRepository
import com.squamed.core.finance.repository.ApPaymentRepository
import com.squamed.core.finance.repository.BankAccountRepository
import com.squamed.core.finance.repository.BankStatementLineRepository
import com.squamed.core.finance.repository.BudgetRepository
import com.squamed.core.finance.repository.CostCentreRepository
import com.squamed.core.finance.repository.DepreciationRunRepository
import com.squamed.core.finance.repository.FixedAssetRepository
import com.squamed.core.finance.repository.GlAccountRepository
import com.squamed.core.finance.repository.GstReturnRepository
import com.squamed.core.finance.repository.JournalEntryRepository
import com.squamed.core.finance.repository.JournalLineRepository
import com.squamed.core.finance.repository.PeriodCloseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class JournalLineInput(
  val accountCode: String,
  val debit: BigDecimal,
  val credit: BigDecimal,
  val costCentre: String? = null,
  val description: String? = null,
)

@Service
class FinanceService(
  private val accountRepository: GlAccountRepository,
  private val costCentreRepository: CostCentreRepository,
  private val entryRepository: JournalEntryRepository,
  private val lineRepository: JournalLineRepository,
  private val apInvoiceRepository: ApInvoiceRepository,
  private val apPaymentRepository: ApPaymentRepository,
  private val bankAccountRepository: BankAccountRepository,
  private val statementLineRepository: BankStatementLineRepository,
  private val assetRepository: FixedAssetRepository,
  private val depreciationRepository: DepreciationRunRepository,
  private val budgetRepository: BudgetRepository,
  private val closeRepository: PeriodCloseRepository,
  private val gstRepository: GstReturnRepository,
  private val invoiceRepository: InvoiceRepository,
  private val paymentRepository: PaymentRepository,
  private val refundRepository: RefundRepository,
  private val taxLineRepository: TaxLineRepository,
  private val taxProfileRepository: HospitalTaxProfileRepository,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    const val AR = "1100"
    const val CASH = "1000"
    const val AP = "2000"
    const val GST = "2100"
    const val REVENUE = "4000"
    const val SUPPLIES = "5100"
    const val DEPRECIATION = "5200"
    const val ACCUM_DEPRECIATION = "1590"

    private val CHART =
      listOf(
        Triple(CASH, "Cash & bank", "ASSET"),
        Triple(AR, "Accounts receivable", "ASSET"),
        Triple("1200", "Inventory", "ASSET"),
        Triple("1500", "Fixed assets", "ASSET"),
        Triple(ACCUM_DEPRECIATION, "Accumulated depreciation", "ASSET"),
        Triple(AP, "Accounts payable", "LIABILITY"),
        Triple(GST, "GST payable", "LIABILITY"),
        Triple("3000", "Equity", "EQUITY"),
        Triple(REVENUE, "Patient revenue", "REVENUE"),
        Triple("5000", "Salaries", "EXPENSE"),
        Triple(SUPPLIES, "Supplies", "EXPENSE"),
        Triple(DEPRECIATION, "Depreciation", "EXPENSE"),
        Triple("5300", "Other operating expense", "EXPENSE"),
      )

    private val PERIOD = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    private val MONEY = BigDecimal("0.01")
  }

  // -- chart / masters ------------------------------------------------------

  @Transactional
  fun ensureChart(hospitalId: UUID): List<GlAccount> {
    val existing = accountRepository.findByHospitalIdOrderByCodeAsc(hospitalId)
    if (existing.isNotEmpty()) return existing
    return CHART
      .map { (code, name, type) ->
        accountRepository.save(GlAccount(hospitalId = hospitalId, code = code, name = name, type = type))
      }.sortedBy { it.code }
  }

  @Transactional(readOnly = true)
  fun accounts(hospitalId: UUID): List<GlAccount> = accountRepository.findByHospitalIdOrderByCodeAsc(hospitalId)

  @Transactional
  fun createCostCentre(
    hospitalId: UUID,
    code: String,
    name: String,
    actorId: UUID?,
  ): CostCentre {
    if (code.isBlank() || name.isBlank()) throw IllegalArgumentException("COST_CENTRE_FIELDS_REQUIRED")
    if (costCentreRepository.findByHospitalIdAndCode(hospitalId, code.trim().uppercase()) != null) {
      throw IllegalArgumentException("COST_CENTRE_EXISTS")
    }
    return costCentreRepository.save(
      CostCentre(hospitalId = hospitalId, code = code.trim().uppercase(), name = name.trim()).also { it.createdBy = actorId },
    )
  }

  @Transactional(readOnly = true)
  fun costCentres(hospitalId: UUID): List<CostCentre> = costCentreRepository.findByHospitalIdOrderByCodeAsc(hospitalId)

  // -- journal posting ------------------------------------------------------

  @Transactional
  fun postJournal(
    hospitalId: UUID,
    entryDate: LocalDate,
    sourceType: String,
    sourceId: UUID,
    description: String,
    lines: List<JournalLineInput>,
    actorId: UUID?,
  ): JournalEntry? {
    if (entryRepository.findByHospitalIdAndSourceTypeAndSourceId(hospitalId, sourceType, sourceId) != null) return null
    if (lines.isEmpty()) throw IllegalArgumentException("JOURNAL_LINES_REQUIRED")
    val debit = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.debit }
    val credit = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.credit }
    if (debit.setScale(2, RoundingMode.HALF_UP) != credit.setScale(2, RoundingMode.HALF_UP)) {
      throw IllegalArgumentException("JOURNAL_UNBALANCED")
    }
    val latest = entryRepository.findTopByOrderByEntryNoDesc()
    val next =
      latest
        ?.entryNo
        ?.removePrefix("JE-")
        ?.toIntOrNull()
        ?.plus(1) ?: 1
    val entry =
      entryRepository.save(
        JournalEntry(
          hospitalId = hospitalId,
          entryNo = String.format("JE-%06d", next),
          entryDate = entryDate,
          sourceType = sourceType,
          sourceId = sourceId,
          description = description,
          postedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    val entryId = entry.id ?: throw IllegalStateException("JOURNAL_ENTRY_ID_MISSING")
    lines.forEach { line ->
      lineRepository.save(
        JournalLine(
          hospitalId = hospitalId,
          entryId = entryId,
          accountCode = line.accountCode,
          debitQar = line.debit.setScale(2, RoundingMode.HALF_UP),
          creditQar = line.credit.setScale(2, RoundingMode.HALF_UP),
          costCentre = line.costCentre,
          description = line.description,
        ).also { it.createdBy = actorId },
      )
    }
    return entry
  }

  // Pulls the operational billing ledger into double-entry postings, idempotently per source row.
  @Transactional
  fun postOperations(
    hospitalId: UUID,
    period: String,
    actorId: UUID?,
  ): Map<String, Any?> {
    val (start, end) = periodRange(period)
    ensureChart(hospitalId)
    val fromInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC)
    val toInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    var posted = 0

    invoiceRepository
      .findAllForHospital(hospitalId)
      .filter { it.status != "cancelled" && !it.issuedAt.isBefore(fromInstant) && it.issuedAt.isBefore(toInstant) }
      .forEach { invoice ->
        val revenue = invoice.totalQar - invoice.taxQar
        val lines =
          buildList {
            add(JournalLineInput(AR, invoice.totalQar, BigDecimal.ZERO, description = "Invoice ${invoice.invoiceNumber}"))
            add(JournalLineInput(REVENUE, BigDecimal.ZERO, revenue, description = "Invoice ${invoice.invoiceNumber}"))
            if (invoice.taxQar.signum() > 0) {
              add(JournalLineInput(GST, BigDecimal.ZERO, invoice.taxQar, description = "GST ${invoice.invoiceNumber}"))
            }
          }
        if (postJournal(
            hospitalId,
            invoice.issuedAt.atZone(ZoneOffset.UTC).toLocalDate(),
            "INVOICE",
            checkNotNull(invoice.id),
            "Invoice ${invoice.invoiceNumber}",
            lines,
            actorId,
          ) !=
          null
        ) {
          posted++
        }
      }

    paymentRepository.findAllForPeriod(hospitalId, fromInstant, toInstant).forEach { payment ->
      val lines =
        listOf(
          JournalLineInput(CASH, payment.amountQar, BigDecimal.ZERO, description = "Payment ${payment.reference ?: ""}".trim()),
          JournalLineInput(AR, BigDecimal.ZERO, payment.amountQar, description = "Payment ${payment.reference ?: ""}".trim()),
        )
      if (postJournal(
          hospitalId,
          payment.receivedAt.atZone(ZoneOffset.UTC).toLocalDate(),
          "PAYMENT",
          checkNotNull(payment.id),
          "Payment ${payment.method}",
          lines,
          actorId,
        ) !=
        null
      ) {
        posted++
      }
    }

    refundRepository
      .findAllForHospital(hospitalId)
      .filter { it.status == "issued" && !it.issuedAt.isBefore(fromInstant) && it.issuedAt.isBefore(toInstant) }
      .forEach { refund ->
        val lines =
          listOf(
            JournalLineInput(AR, refund.amountQar, BigDecimal.ZERO, description = "Refund ${refund.id}"),
            JournalLineInput(CASH, BigDecimal.ZERO, refund.amountQar, description = "Refund ${refund.id}"),
          )
        if (postJournal(hospitalId, refund.issuedAt.atZone(ZoneOffset.UTC).toLocalDate(), "REFUND", checkNotNull(refund.id), "Refund", lines, actorId) !=
          null
        ) {
          posted++
        }
      }

    apInvoiceRepository
      .findByHospitalIdOrderByInvoiceDateDesc(hospitalId)
      .filter { !it.invoiceDate.isBefore(start) && !it.invoiceDate.isAfter(end) }
      .forEach { invoice ->
        val lines =
          listOf(
            JournalLineInput(SUPPLIES, invoice.amountQar, BigDecimal.ZERO, invoice.costCentre, "AP ${invoice.number}"),
            JournalLineInput(AP, BigDecimal.ZERO, invoice.amountQar, invoice.costCentre, "AP ${invoice.number}"),
          )
        if (postJournal(hospitalId, invoice.invoiceDate, "AP_INVOICE", checkNotNull(invoice.id), "AP invoice ${invoice.number}", lines, actorId) !=
          null
        ) {
          posted++
        }
      }

    apPaymentRepository.findByHospitalIdAndPaidOnBetween(hospitalId, start, end).forEach { payment ->
      val lines =
        listOf(
          JournalLineInput(AP, payment.amountQar, BigDecimal.ZERO, description = "AP payment ${payment.apInvoiceId}"),
          JournalLineInput(CASH, BigDecimal.ZERO, payment.amountQar, description = "AP payment ${payment.apInvoiceId}"),
        )
      if (postJournal(hospitalId, payment.paidOn, "AP_PAYMENT", checkNotNull(payment.id), "AP payment", lines, actorId) != null) posted++
    }

    return mapOf("period" to period, "posted" to posted)
  }

  // -- AP -------------------------------------------------------------------

  @Transactional
  fun createApInvoice(
    hospitalId: UUID,
    vendorName: String,
    number: String,
    invoiceDate: LocalDate,
    dueDate: LocalDate?,
    amount: BigDecimal,
    costCentre: String?,
    actorId: UUID?,
  ): ApInvoice {
    if (vendorName.isBlank() || number.isBlank()) throw IllegalArgumentException("AP_FIELDS_REQUIRED")
    if (amount.signum() <= 0) throw IllegalArgumentException("AP_AMOUNT_INVALID")
    if (apInvoiceRepository.findByHospitalIdAndNumber(hospitalId, number.trim()) != null) throw IllegalArgumentException("AP_INVOICE_EXISTS")
    return apInvoiceRepository.save(
      ApInvoice(
        hospitalId = hospitalId,
        vendorName = vendorName.trim(),
        number = number.trim(),
        invoiceDate = invoiceDate,
        dueDate = dueDate,
        amountQar = amount,
        costCentre = costCentre,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun payApInvoice(
    id: UUID,
    hospitalId: UUID,
    amount: BigDecimal,
    paidOn: LocalDate,
    method: String,
    actorId: UUID?,
  ): ApInvoice {
    if (amount.signum() <= 0) throw IllegalArgumentException("AP_AMOUNT_INVALID")
    if (method.isBlank()) throw IllegalArgumentException("AP_METHOD_REQUIRED")
    val invoice = apInvoiceRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("AP_INVOICE_NOT_FOUND")
    if (invoice.status == "PAID") throw IllegalStateException("AP_ALREADY_PAID")
    if (invoice.paidQar + amount > invoice.amountQar) throw IllegalArgumentException("AP_OVERPAY")
    apPaymentRepository.save(
      ApPayment(
        hospitalId = hospitalId,
        apInvoiceId = id,
        amountQar = amount,
        paidOn = paidOn,
        method = method.trim(),
      ).also { it.createdBy = actorId },
    )
    invoice.paidQar = invoice.paidQar + amount
    invoice.status = if (invoice.paidQar.compareTo(invoice.amountQar) == 0) "PAID" else "PARTIAL"
    invoice.updatedBy = actorId
    return apInvoiceRepository.save(invoice)
  }

  @Transactional(readOnly = true)
  fun apInvoices(hospitalId: UUID): List<ApInvoice> = apInvoiceRepository.findByHospitalIdOrderByInvoiceDateDesc(hospitalId)

  // -- bank reconciliation --------------------------------------------------

  @Transactional
  fun createBankAccount(
    hospitalId: UUID,
    code: String,
    name: String,
    openingBalance: BigDecimal,
    actorId: UUID?,
  ): BankAccount {
    if (code.isBlank() || name.isBlank()) throw IllegalArgumentException("BANK_FIELDS_REQUIRED")
    if (bankAccountRepository.findByHospitalIdAndCode(hospitalId, code.trim().uppercase()) != null) throw IllegalArgumentException("BANK_ACCOUNT_EXISTS")
    return bankAccountRepository.save(
      BankAccount(
        hospitalId = hospitalId,
        code = code.trim().uppercase(),
        name = name.trim(),
        openingBalance = openingBalance,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional(readOnly = true)
  fun bankAccounts(hospitalId: UUID): List<BankAccount> = bankAccountRepository.findByHospitalIdOrderByCodeAsc(hospitalId)

  @Transactional
  fun importStatementLine(
    hospitalId: UUID,
    bankAccountId: UUID,
    statementDate: LocalDate,
    amount: BigDecimal,
    reference: String?,
    actorId: UUID?,
  ): BankStatementLine {
    val account = bankAccountRepository.findByIdAndHospitalId(bankAccountId, hospitalId) ?: throw IllegalArgumentException("BANK_ACCOUNT_NOT_FOUND")
    return statementLineRepository.save(
      BankStatementLine(
        hospitalId = hospitalId,
        bankAccountId = checkNotNull(account.id),
        statementDate = statementDate,
        amountQar = amount,
        reference = reference?.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun autoMatchBank(hospitalId: UUID): Map<String, Any?> {
    val unmatched = statementLineRepository.findByHospitalIdAndStatusOrderByStatementDateAsc(hospitalId, "UNMATCHED")
    if (unmatched.isEmpty()) return mapOf("matched" to 0, "remaining" to 0)
    val min = unmatched.minOf { it.statementDate }.minusDays(3)
    val max = unmatched.maxOf { it.statementDate }.plusDays(3)
    val cashLines =
      lineRepository
        .findForPeriod(hospitalId, min, max)
        .filter { it.accountCode == CASH }
    val entryDate = entryRepository.findByHospitalIdAndEntryDateBetween(hospitalId, min, max).associateBy { it.id }
    val used = mutableSetOf<UUID>()
    var matched = 0
    unmatched.forEach { line ->
      val hit =
        cashLines.firstOrNull { cash ->
          val entry = entryDate[cash.entryId] ?: return@firstOrNull false
          val net = cash.debitQar - cash.creditQar
          cash.entryId !in used &&
            net.abs().compareTo(line.amountQar.abs()) == 0 &&
            !entry.entryDate.isBefore(line.statementDate.minusDays(3)) &&
            !entry.entryDate.isAfter(line.statementDate.plusDays(3))
        }
      if (hit != null) {
        used += hit.entryId
        line.status = "MATCHED"
        line.matchedEntryId = hit.entryId
        statementLineRepository.save(line)
        matched++
      }
    }
    return mapOf("matched" to matched, "remaining" to unmatched.size - matched)
  }

  @Transactional(readOnly = true)
  fun statementLines(
    hospitalId: UUID,
    bankAccountId: UUID?,
  ): List<BankStatementLine> =
    if (bankAccountId == null) {
      statementLineRepository.findByHospitalIdAndStatusOrderByStatementDateAsc(hospitalId, "UNMATCHED")
    } else {
      statementLineRepository.findByHospitalIdAndBankAccountIdOrderByStatementDateAsc(hospitalId, bankAccountId)
    }

  // -- budgets --------------------------------------------------------------

  @Transactional
  fun upsertBudget(
    hospitalId: UUID,
    accountCode: String,
    period: String,
    amount: BigDecimal,
    costCentre: String,
    actorId: UUID?,
  ): Budget {
    periodRange(period)
    if (amount.signum() < 0) throw IllegalArgumentException("BUDGET_AMOUNT_INVALID")
    ensureChart(hospitalId)
    accountRepository.findByHospitalIdAndCode(hospitalId, accountCode) ?: throw IllegalArgumentException("GL_ACCOUNT_NOT_FOUND")
    val row =
      budgetRepository.findByHospitalIdAndAccountCodeAndPeriodAndCostCentre(hospitalId, accountCode, period, costCentre)
        ?: Budget(hospitalId = hospitalId, accountCode = accountCode, period = period, amountQar = amount, costCentre = costCentre)
          .also { it.createdBy = actorId }
    row.amountQar = amount
    row.updatedBy = actorId
    return budgetRepository.save(row)
  }

  @Transactional(readOnly = true)
  fun budgetVsActual(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    val (start, end) = periodRange(period)
    val budgets = budgetRepository.findByHospitalIdAndPeriodOrderByAccountCodeAsc(hospitalId, period)
    val accounts = accountRepository.findByHospitalIdOrderByCodeAsc(hospitalId).associateBy { it.code }
    val lines = lineRepository.findForPeriod(hospitalId, start, end)
    val rows =
      budgets.map { budget ->
        val account = accounts[budget.accountCode]
        val actual =
          lines.filter { it.accountCode == budget.accountCode }.fold(BigDecimal.ZERO) { acc, line ->
            val net =
              if (account?.type == "REVENUE") line.creditQar - line.debitQar else line.debitQar - line.creditQar
            acc + net
          }
        mapOf(
          "accountCode" to budget.accountCode,
          "accountName" to (account?.name ?: budget.accountCode),
          "type" to (account?.type ?: "UNKNOWN"),
          "budget" to budget.amountQar.setScale(2, RoundingMode.HALF_UP),
          "actual" to actual.setScale(2, RoundingMode.HALF_UP),
          "variance" to (budget.amountQar - actual).setScale(2, RoundingMode.HALF_UP),
        )
      }
    return mapOf("period" to period, "rows" to rows)
  }

  // -- fixed assets ---------------------------------------------------------

  @Transactional
  fun createAsset(
    hospitalId: UUID,
    code: String,
    name: String,
    category: String,
    cost: BigDecimal,
    usefulLifeMonths: Int,
    acquiredOn: LocalDate,
    actorId: UUID?,
  ): FixedAsset {
    if (code.isBlank() || name.isBlank()) throw IllegalArgumentException("ASSET_FIELDS_REQUIRED")
    if (cost.signum() <= 0 || usefulLifeMonths <= 0) throw IllegalArgumentException("ASSET_VALUES_INVALID")
    if (assetRepository.findByHospitalIdAndCode(hospitalId, code.trim().uppercase()) != null) throw IllegalArgumentException("ASSET_EXISTS")
    ensureChart(hospitalId)
    val asset =
      assetRepository.save(
        FixedAsset(
          hospitalId = hospitalId,
          code = code.trim().uppercase(),
          name = name.trim(),
          category = category.trim().ifBlank { "equipment" },
          costQar = cost,
          usefulLifeMonths = usefulLifeMonths,
          acquiredOn = acquiredOn,
        ).also { it.createdBy = actorId },
      )
    postJournal(
      hospitalId,
      acquiredOn,
      "FIXED_ASSET",
      checkNotNull(asset.id),
      "Asset acquisition ${asset.code}",
      listOf(
        JournalLineInput("1500", cost, BigDecimal.ZERO, description = "Asset ${asset.code}"),
        JournalLineInput(AP, BigDecimal.ZERO, cost, description = "Asset ${asset.code}"),
      ),
      actorId,
    )
    return asset
  }

  @Transactional(readOnly = true)
  fun assets(hospitalId: UUID): List<FixedAsset> = assetRepository.findByHospitalIdOrderByCodeAsc(hospitalId)

  @Transactional
  fun runDepreciation(
    hospitalId: UUID,
    period: String,
    actorId: UUID?,
  ): DepreciationRun {
    val (_, end) = periodRange(period)
    depreciationRepository.findByHospitalIdAndPeriod(hospitalId, period)?.let { return it }
    ensureChart(hospitalId)
    val assets = assetRepository.findByHospitalIdOrderByCodeAsc(hospitalId).filter { it.status == "ACTIVE" && !it.acquiredOn.isAfter(end) }
    val charges =
      assets.mapNotNull { asset ->
        val monthly = asset.costQar.divide(BigDecimal.valueOf(asset.usefulLifeMonths.toLong()), 2, RoundingMode.HALF_UP)
        val remaining = asset.costQar - asset.accumulatedDepreciation
        val charge = monthly.min(remaining)
        if (charge.signum() <= 0) null else asset to charge
      }
    val total = charges.fold(BigDecimal.ZERO) { acc, (_, charge) -> acc + charge }
    val run =
      depreciationRepository.save(
        DepreciationRun(
          hospitalId = hospitalId,
          period = period,
          totalQar = total.setScale(2, RoundingMode.HALF_UP),
          postedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    if (total.signum() > 0) {
      postJournal(
        hospitalId,
        end,
        "DEPRECIATION",
        checkNotNull(run.id),
        "Depreciation $period",
        listOf(
          JournalLineInput(DEPRECIATION, total, BigDecimal.ZERO, description = "Depreciation $period"),
          JournalLineInput(ACCUM_DEPRECIATION, BigDecimal.ZERO, total, description = "Depreciation $period"),
        ),
        actorId,
      )
      charges.forEach { (asset, charge) ->
        asset.accumulatedDepreciation = asset.accumulatedDepreciation + charge
        asset.updatedBy = actorId
        assetRepository.save(asset)
      }
    }
    return run
  }

  @Transactional(readOnly = true)
  fun depreciationRuns(hospitalId: UUID): List<DepreciationRun> = depreciationRepository.findByHospitalIdOrderByPeriodDesc(hospitalId)

  // -- reports --------------------------------------------------------------

  @Transactional(readOnly = true)
  fun arAging(hospitalId: UUID): Map<String, Any?> {
    val today = LocalDate.now()
    val open =
      invoiceRepository
        .findAllForHospital(hospitalId)
        .filter { it.status != "cancelled" && it.amountDueQar.signum() > 0 }
    val buckets = mapOf("current" to 0, "days30" to 0, "days60" to 0, "days90" to 0).toMutableMap()
    var total = BigDecimal.ZERO
    open.forEach { invoice ->
      val age =
        java.time.temporal.ChronoUnit.DAYS
          .between(invoice.issuedAt.atZone(ZoneOffset.UTC).toLocalDate(), today)
      val key =
        when {
          age < 30 -> "current"
          age < 60 -> "days30"
          age < 90 -> "days60"
          else -> "days90"
        }
      buckets[key] = buckets.getValue(key) + 1
      total += invoice.amountDueQar
    }
    return mapOf(
      "totalDue" to total.setScale(2, RoundingMode.HALF_UP),
      "openInvoices" to open.size,
      "buckets" to buckets,
    )
  }

  @Transactional(readOnly = true)
  fun forecast(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    periodRange(period)
    val pnl = profitAndLoss(hospitalId, period)
    val revenue = pnl["revenue"] as BigDecimal
    val expense = pnl["expense"] as BigDecimal
    val months =
      (1..3).map { offset ->
        mapOf(
          "period" to
            LocalDate
              .parse("$period-01")
              .plusMonths(offset.toLong())
              .toString()
              .substring(0, 7),
          "revenue" to revenue,
          "expense" to expense,
          "net" to (revenue - expense),
        )
      }
    return mapOf(
      "basisPeriod" to period,
      "method" to "flat_run_rate",
      "months" to months,
    )
  }

  @Transactional(readOnly = true)
  fun trialBalance(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    val (start, end) = periodRange(period)
    val lines = lineRepository.findForPeriod(hospitalId, start, end)
    val accounts = accountRepository.findByHospitalIdOrderByCodeAsc(hospitalId).associateBy { it.code }
    val rows =
      lines
        .groupBy { it.accountCode }
        .toSortedMap()
        .map { (code, accountLines) ->
          val debit = accountLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.debitQar }
          val credit = accountLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.creditQar }
          mapOf(
            "accountCode" to code,
            "accountName" to (accounts[code]?.name ?: code),
            "type" to (accounts[code]?.type ?: "UNKNOWN"),
            "debit" to debit.setScale(2, RoundingMode.HALF_UP),
            "credit" to credit.setScale(2, RoundingMode.HALF_UP),
            "balance" to (debit - credit).setScale(2, RoundingMode.HALF_UP),
          )
        }
    val totalDebit = rows.fold(BigDecimal.ZERO) { acc, row -> acc + (row["debit"] as BigDecimal) }
    val totalCredit = rows.fold(BigDecimal.ZERO) { acc, row -> acc + (row["credit"] as BigDecimal) }
    return mapOf(
      "period" to period,
      "rows" to rows,
      "totalDebit" to totalDebit.setScale(2, RoundingMode.HALF_UP),
      "totalCredit" to totalCredit.setScale(2, RoundingMode.HALF_UP),
      "balanced" to ((totalDebit - totalCredit).abs().compareTo(MONEY) <= 0),
    )
  }

  @Transactional(readOnly = true)
  fun profitAndLoss(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    val (start, end) = periodRange(period)
    val lines = lineRepository.findForPeriod(hospitalId, start, end)
    val accounts = accountRepository.findByHospitalIdOrderByCodeAsc(hospitalId).associateBy { it.code }
    val revenue =
      lines
        .filter { accounts[it.accountCode]?.type == "REVENUE" }
        .fold(BigDecimal.ZERO) { acc, line -> acc + line.creditQar - line.debitQar }
    val expense =
      lines
        .filter { accounts[it.accountCode]?.type == "EXPENSE" }
        .fold(BigDecimal.ZERO) { acc, line -> acc + line.debitQar - line.creditQar }
    return mapOf(
      "period" to period,
      "revenue" to revenue.setScale(2, RoundingMode.HALF_UP),
      "expense" to expense.setScale(2, RoundingMode.HALF_UP),
      "net" to (revenue - expense).setScale(2, RoundingMode.HALF_UP),
    )
  }

  @Transactional(readOnly = true)
  fun balanceSheet(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    val (start, end) = periodRange(period)
    val lines = lineRepository.findForPeriod(hospitalId, start, end)
    val accounts = accountRepository.findByHospitalIdOrderByCodeAsc(hospitalId).associateBy { it.code }

    fun total(type: String): BigDecimal =
      lines
        .filter { accounts[it.accountCode]?.type == type }
        .fold(BigDecimal.ZERO) { acc, line ->
          val net = if (type == "ASSET") line.debitQar - line.creditQar else line.creditQar - line.debitQar
          acc + net
        }
    val assets = total("ASSET")
    val liabilities = total("LIABILITY")
    val equity = total("EQUITY")
    val revenue =
      lines
        .filter { accounts[it.accountCode]?.type == "REVENUE" }
        .fold(BigDecimal.ZERO) { acc, line -> acc + line.creditQar - line.debitQar }
    val expense =
      lines
        .filter { accounts[it.accountCode]?.type == "EXPENSE" }
        .fold(BigDecimal.ZERO) { acc, line -> acc + line.debitQar - line.creditQar }
    val earnings = revenue - expense
    val funding = liabilities + equity + earnings
    return mapOf(
      "period" to period,
      "assets" to assets.setScale(2, RoundingMode.HALF_UP),
      "liabilities" to liabilities.setScale(2, RoundingMode.HALF_UP),
      "equity" to equity.setScale(2, RoundingMode.HALF_UP),
      "currentEarnings" to earnings.setScale(2, RoundingMode.HALF_UP),
      "liabilitiesAndEquity" to funding.setScale(2, RoundingMode.HALF_UP),
      "balanced" to ((assets - funding).abs().compareTo(MONEY) <= 0),
    )
  }

  // -- GST pack -------------------------------------------------------------

  @Transactional
  fun generateGstReturn(
    hospitalId: UUID,
    period: String,
    actorId: UUID?,
  ): GstReturn {
    val (start, end) = periodRange(period)
    val existing = gstRepository.findByHospitalIdAndPeriod(hospitalId, period)
    if (existing?.status == "FILED") throw IllegalStateException("GST_RETURN_FILED")
    val fromInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC)
    val toInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val taxLines = taxLineRepository.findForInvoicePeriod(hospitalId, fromInstant, toInstant)
    val taxable = taxLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.taxableQar }
    val tax = taxLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.taxQar }
    val half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)
    val rows =
      taxLines
        .groupBy { Triple(it.hsnCode, it.taxCategory, it.ratePct) }
        .map { (key, group) ->
          mapOf(
            "hsnCode" to key.first,
            "taxCategory" to key.second,
            "ratePct" to key.third,
            "taxableValue" to group.fold(BigDecimal.ZERO) { acc, line -> acc + line.taxableQar }.setScale(2, RoundingMode.HALF_UP),
            "tax" to group.fold(BigDecimal.ZERO) { acc, line -> acc + line.taxQar }.setScale(2, RoundingMode.HALF_UP),
          )
        }
    val payload =
      objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(
        mapOf(
          "period" to period,
          "gstin" to taxProfileRepository.findByHospitalId(hospitalId)?.gstin,
          "rows" to rows,
        ),
      )
    val row = existing ?: GstReturn(hospitalId = hospitalId, period = period).also { it.createdBy = actorId }
    row.taxableValue = taxable.setScale(2, RoundingMode.HALF_UP)
    row.cgstQar = half
    row.sgstQar = half
    row.igstQar = BigDecimal.ZERO
    row.totalTaxQar = tax.setScale(2, RoundingMode.HALF_UP)
    row.payload = payload
    row.generatedAt = Instant.now()
    row.updatedBy = actorId
    return gstRepository.save(row)
  }

  @Transactional
  fun fileGstReturn(
    hospitalId: UUID,
    period: String,
    actorId: UUID?,
  ): GstReturn {
    val row = generateGstReturn(hospitalId, period, actorId)
    if (row.status == "FILED") throw IllegalStateException("GST_RETURN_ALREADY_FILED")
    row.status = "FILED"
    row.filedAt = Instant.now()
    row.updatedBy = actorId
    return gstRepository.save(row)
  }

  @Transactional(readOnly = true)
  fun gstReturns(hospitalId: UUID): List<GstReturn> = gstRepository.findByHospitalIdOrderByPeriodDesc(hospitalId)

  // -- month close ----------------------------------------------------------

  @Transactional
  fun closePeriod(
    hospitalId: UUID,
    period: String,
    actorId: UUID?,
  ): PeriodClose {
    val (start, end) = periodRange(period)
    if (closeRepository.findByHospitalIdAndPeriod(hospitalId, period) != null) throw IllegalStateException("PERIOD_ALREADY_CLOSED")
    postOperations(hospitalId, period, actorId)
    runDepreciation(hospitalId, period, actorId)
    val trial = trialBalance(hospitalId, period)
    if (trial["balanced"] != true) throw IllegalStateException("TRIAL_BALANCE_UNBALANCED")

    val fromInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC)
    val toInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val invoices =
      invoiceRepository
        .findAllForHospital(hospitalId)
        .filter { it.status != "cancelled" && !it.issuedAt.isBefore(fromInstant) && it.issuedAt.isBefore(toInstant) }
    val operationalRevenue = invoices.fold(BigDecimal.ZERO) { acc, invoice -> acc + invoice.totalQar - invoice.taxQar }
    val operationalTax = invoices.fold(BigDecimal.ZERO) { acc, invoice -> acc + invoice.taxQar }

    val lines = lineRepository.findForPeriod(hospitalId, start, end)
    val glRevenue =
      lines.filter { it.accountCode == REVENUE }.fold(BigDecimal.ZERO) { acc, line -> acc + line.creditQar - line.debitQar }
    val glTax =
      lines.filter { it.accountCode == GST }.fold(BigDecimal.ZERO) { acc, line -> acc + line.creditQar - line.debitQar }
    val variance =
      (operationalRevenue - glRevenue).abs().max((operationalTax - glTax).abs()).setScale(2, RoundingMode.HALF_UP)
    if (variance.compareTo(MONEY) > 0) throw IllegalStateException("FINANCE_CLOSE_VARIANCE")

    val close =
      PeriodClose(
        hospitalId = hospitalId,
        period = period,
        operationalTotal = (operationalRevenue + operationalTax).setScale(2, RoundingMode.HALF_UP),
        glTotal = (glRevenue + glTax).setScale(2, RoundingMode.HALF_UP),
        varianceQar = variance,
        trialBalance = objectMapper.valueToTree(trial),
        closedBy = actorId,
      ).also { it.createdBy = actorId }
    return closeRepository.save(close)
  }

  @Transactional(readOnly = true)
  fun closes(hospitalId: UUID): List<PeriodClose> = closeRepository.findByHospitalIdOrderByPeriodDesc(hospitalId)

  private fun periodRange(period: String): Pair<LocalDate, LocalDate> {
    if (!PERIOD.matches(period)) throw IllegalArgumentException("FINANCE_PERIOD_INVALID")
    val start = LocalDate.parse("$period-01")
    return start to start.plusMonths(1).minusDays(1)
  }
}
