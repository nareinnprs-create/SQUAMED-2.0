package com.squamed.core.finance.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.finance.service.FinanceService
import com.squamed.core.finance.service.JournalLineInput
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CostCentreRequest(
  val code: String,
  val name: String,
)

data class JournalLineDto(
  val accountCode: String,
  val debitQar: BigDecimal = BigDecimal.ZERO,
  val creditQar: BigDecimal = BigDecimal.ZERO,
  val costCentre: String? = null,
  val description: String? = null,
)

data class ManualJournalRequest(
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val entryDate: LocalDate,
  val description: String,
  val lines: List<JournalLineDto>,
)

data class PeriodRequest(
  val period: String,
)

data class ApInvoiceRequest(
  val vendorName: String,
  val number: String,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val invoiceDate: LocalDate,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val dueDate: LocalDate? = null,
  val amountQar: BigDecimal,
  val costCentre: String? = null,
)

data class ApPaymentRequest(
  val amountQar: BigDecimal,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val paidOn: LocalDate,
  val method: String,
)

data class BankAccountRequest(
  val code: String,
  val name: String,
  val openingBalance: BigDecimal = BigDecimal.ZERO,
)

data class StatementLineRequest(
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val statementDate: LocalDate,
  val amountQar: BigDecimal,
  val reference: String? = null,
)

data class BudgetRequest(
  val accountCode: String,
  val period: String,
  val amountQar: BigDecimal,
  val costCentre: String = "",
)

data class AssetRequest(
  val code: String,
  val name: String,
  val category: String = "equipment",
  val costQar: BigDecimal,
  val usefulLifeMonths: Int,
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) val acquiredOn: LocalDate,
)

@RestController
@RequestMapping("/api/v1/finance")
class FinanceController(
  private val financeService: FinanceService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/accounts/seed")
  @RequiresPermission("finance:write:finance")
  fun seedAccounts(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.ensureChart(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/accounts")
  @RequiresPermission("finance:read:finance")
  fun accounts(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.accounts(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/cost-centres")
  @RequiresPermission("finance:write:finance")
  fun createCostCentre(
    @RequestBody dto: CostCentreRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.createCostCentre(
        tenantResolver.pinOrDefault(principal, null),
        dto.code,
        dto.name,
        principal.userId,
      ),
    )

  @GetMapping("/cost-centres")
  @RequiresPermission("finance:read:finance")
  fun costCentres(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.costCentres(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/journals")
  @RequiresPermission("finance:write:finance")
  fun postManualJournal(
    @RequestBody dto: ManualJournalRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.postJournal(
        tenantResolver.pinOrDefault(principal, null),
        dto.entryDate,
        "MANUAL",
        UUID.randomUUID(),
        dto.description,
        dto.lines.map { JournalLineInput(it.accountCode, it.debitQar, it.creditQar, it.costCentre, it.description) },
        principal.userId,
      ),
    )

  @PostMapping("/gl/post-operations")
  @RequiresPermission("finance:write:finance")
  fun postOperations(
    @RequestBody dto: PeriodRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.postOperations(tenantResolver.pinOrDefault(principal, null), dto.period, principal.userId))

  @PostMapping("/ap/invoices")
  @RequiresPermission("finance:write:finance")
  fun createApInvoice(
    @RequestBody dto: ApInvoiceRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.createApInvoice(
        tenantResolver.pinOrDefault(principal, null),
        dto.vendorName,
        dto.number,
        dto.invoiceDate,
        dto.dueDate,
        dto.amountQar,
        dto.costCentre,
        principal.userId,
      ),
    )

  @GetMapping("/ap/invoices")
  @RequiresPermission("finance:read:finance")
  fun apInvoices(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.apInvoices(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/ap/invoices/{id}/pay")
  @RequiresPermission("finance:write:finance")
  fun payApInvoice(
    @PathVariable id: UUID,
    @RequestBody dto: ApPaymentRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.payApInvoice(
        id,
        tenantResolver.pinOrDefault(principal, null),
        dto.amountQar,
        dto.paidOn,
        dto.method,
        principal.userId,
      ),
    )

  @PostMapping("/bank/accounts")
  @RequiresPermission("finance:write:finance")
  fun createBankAccount(
    @RequestBody dto: BankAccountRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.createBankAccount(
        tenantResolver.pinOrDefault(principal, null),
        dto.code,
        dto.name,
        dto.openingBalance,
        principal.userId,
      ),
    )

  @GetMapping("/bank/accounts")
  @RequiresPermission("finance:read:finance")
  fun bankAccounts(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.bankAccounts(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/bank/accounts/{id}/statement-lines")
  @RequiresPermission("finance:write:finance")
  fun importStatementLine(
    @PathVariable id: UUID,
    @RequestBody dto: StatementLineRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.importStatementLine(
        tenantResolver.pinOrDefault(principal, null),
        id,
        dto.statementDate,
        dto.amountQar,
        dto.reference,
        principal.userId,
      ),
    )

  @GetMapping("/bank/statement-lines")
  @RequiresPermission("finance:read:finance")
  fun statementLines(
    @RequestParam(name = "bankAccountId", required = false) bankAccountId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.statementLines(tenantResolver.pinOrDefault(principal, null), bankAccountId))

  @PostMapping("/bank/auto-match")
  @RequiresPermission("finance:write:finance")
  fun autoMatchBank(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.autoMatchBank(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/budgets")
  @RequiresPermission("finance:write:finance")
  fun upsertBudget(
    @RequestBody dto: BudgetRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.upsertBudget(
        tenantResolver.pinOrDefault(principal, null),
        dto.accountCode,
        dto.period,
        dto.amountQar,
        dto.costCentre,
        principal.userId,
      ),
    )

  @GetMapping("/budgets/vs-actual")
  @RequiresPermission("finance:read:finance")
  fun budgetVsActual(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.budgetVsActual(tenantResolver.pinOrDefault(principal, null), period))

  @PostMapping("/assets")
  @RequiresPermission("finance:write:finance")
  fun createAsset(
    @RequestBody dto: AssetRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      financeService.createAsset(
        tenantResolver.pinOrDefault(principal, null),
        dto.code,
        dto.name,
        dto.category,
        dto.costQar,
        dto.usefulLifeMonths,
        dto.acquiredOn,
        principal.userId,
      ),
    )

  @GetMapping("/assets")
  @RequiresPermission("finance:read:finance")
  fun assets(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.assets(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/depreciation/{period}")
  @RequiresPermission("finance:write:finance")
  fun runDepreciation(
    @PathVariable period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.runDepreciation(tenantResolver.pinOrDefault(principal, null), period, principal.userId))

  @GetMapping("/depreciation")
  @RequiresPermission("finance:read:finance")
  fun depreciationRuns(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.depreciationRuns(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/reports/ar-aging")
  @RequiresPermission("finance:read:finance")
  fun arAging(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.arAging(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/reports/forecast")
  @RequiresPermission("finance:read:finance")
  fun forecast(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.forecast(tenantResolver.pinOrDefault(principal, null), period))

  @GetMapping("/reports/trial-balance")
  @RequiresPermission("finance:read:finance")
  fun trialBalance(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.trialBalance(tenantResolver.pinOrDefault(principal, null), period))

  @GetMapping("/reports/pnl")
  @RequiresPermission("finance:read:finance")
  fun profitAndLoss(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.profitAndLoss(tenantResolver.pinOrDefault(principal, null), period))

  @GetMapping("/reports/balance-sheet")
  @RequiresPermission("finance:read:finance")
  fun balanceSheet(
    @RequestParam(name = "period") period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.balanceSheet(tenantResolver.pinOrDefault(principal, null), period))

  @PostMapping("/gst/{period}/generate")
  @RequiresPermission("finance:write:finance")
  fun generateGstReturn(
    @PathVariable period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.generateGstReturn(tenantResolver.pinOrDefault(principal, null), period, principal.userId))

  @PostMapping("/gst/{period}/file")
  @RequiresPermission("finance:write:finance")
  fun fileGstReturn(
    @PathVariable period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.fileGstReturn(tenantResolver.pinOrDefault(principal, null), period, principal.userId))

  @GetMapping("/gst")
  @RequiresPermission("finance:read:finance")
  fun gstReturns(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.gstReturns(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/close/{period}")
  @RequiresPermission("finance:close:finance")
  fun closePeriod(
    @PathVariable period: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.closePeriod(tenantResolver.pinOrDefault(principal, null), period, principal.userId))

  @GetMapping("/closes")
  @RequiresPermission("finance:read:finance")
  fun closes(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(financeService.closes(tenantResolver.pinOrDefault(principal, null)))
}
