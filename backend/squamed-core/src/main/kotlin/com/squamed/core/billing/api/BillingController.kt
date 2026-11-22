package com.squamed.core.billing.api

import com.squamed.core.billing.service.BillingService
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/billing")
class BillingController(
  private val billingService: BillingService,
  private val tenantResolver: TenantResolver,
) {
  // ADR-13: hospital users are pinned to their tenant; admins may choose via dto.hospitalId.
  @GetMapping("/tariffs")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listTariffs(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<TariffDto>> = ApiResponse.ok(billingService.listTariffs(search, active, principal.hospitalId))

  @PostMapping("/tariffs")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun createTariff(
    @RequestBody dto: TariffDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TariffDto> = ApiResponse.ok(billingService.createTariff(dto.copy(hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId))))

  @PutMapping("/tariffs/{id}")
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun updateTariff(
    @PathVariable id: UUID,
    @RequestBody dto: TariffDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<TariffDto> = ApiResponse.ok(billingService.updateTariff(id, dto.copy(hospitalId = tenantResolver.pin(principal, dto.hospitalId))))

  @PostMapping("/charges")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun postCharge(
    @RequestBody dto: PostChargeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ChargeView> = ApiResponse.ok(billingService.postCharge(dto, principal.userId, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/charges")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listCharges(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "visitId", required = false) visitId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ChargeView>> = ApiResponse.ok(billingService.listCharges(patientId, visitId, principal.hospitalId))

  @PostMapping("/invoices")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun issueInvoice(
    @RequestBody dto: IssueInvoiceDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InvoiceDetailView> {
    val patientId = dto.patientId ?: throw IllegalArgumentException("PATIENT_ID_REQUIRED")
    return ApiResponse.ok(
      billingService.issueInvoice(
        patientId,
        dto.visitId,
        dto.discountQar,
        dto.payerClass,
        dto.discountReason,
        principal,
        principal.hospitalId,
      ),
    )
  }

  // 03.02 rate cards, packages, estimates, deposits.
  @PostMapping("/rate-cards")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun upsertRateCard(
    @RequestBody dto: RateCardDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RateCardDto> = ApiResponse.ok(billingService.upsertRateCard(dto, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/rate-cards")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listRateCards(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RateCardDto>> = ApiResponse.ok(billingService.listRateCards(principal.hospitalId))

  @PostMapping("/packages")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun createPackage(
    @RequestBody dto: PackageDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PackageDto> = ApiResponse.ok(billingService.createPackage(dto, principal.hospitalId))

  @GetMapping("/packages")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listPackages(): ApiResponse<List<PackageDto>> = ApiResponse.ok(billingService.listPackages())

  @PostMapping("/packages/{id}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun addPackageVersion(
    @PathVariable id: UUID,
    @RequestBody dto: PackageVersionRequestDto,
  ): ApiResponse<PackageVersionDto> = ApiResponse.ok(billingService.addPackageVersion(id, dto))

  @PostMapping("/estimates")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun createEstimate(
    @RequestBody dto: EstimateRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<EstimateView> = ApiResponse.ok(billingService.createEstimate(dto, principal, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/estimates")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listEstimates(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<EstimateView>> = ApiResponse.ok(billingService.listEstimates(patientId, principal.hospitalId))

  @PostMapping("/deposits")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:collect:payment")
  @Transactional
  fun takeDeposit(
    @RequestBody dto: DepositRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DepositView> = ApiResponse.ok(billingService.takeDeposit(dto, principal, tenantResolver.pinOrDefault(principal, dto.hospitalId)))

  @GetMapping("/deposits")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listDeposits(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<DepositView>> = ApiResponse.ok(billingService.listDeposits(patientId, principal.hospitalId))

  @PostMapping("/deposits/{id}/apply")
  @RequiresPermission("billing:collect:payment")
  @Transactional
  fun applyDeposit(
    @PathVariable id: UUID,
    @RequestBody dto: DepositApplyDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DepositView> = ApiResponse.ok(billingService.applyDeposit(id, dto.invoiceId, principal.hospitalId))

  @GetMapping("/invoices")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listInvoices(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<InvoiceView>> = ApiResponse.ok(billingService.listInvoices(patientId, status, principal.hospitalId))

  @GetMapping("/invoices/{id}")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun getInvoice(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InvoiceDetailView> = ApiResponse.ok(billingService.getInvoice(id, principal.hospitalId))

  @PostMapping("/invoices/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:collect:payment")
  @Transactional
  fun recordPayment(
    @PathVariable id: UUID,
    @RequestBody dto: PaymentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PaymentView> = ApiResponse.ok(billingService.recordPayment(id, dto, principal.userId, principal.hospitalId))

  @PostMapping("/invoices/{id}/cancel")
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun cancelInvoice(
    @PathVariable id: UUID,
    @RequestBody(required = false) dto: CancelInvoiceDto?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<InvoiceDetailView> = ApiResponse.ok(billingService.cancelInvoice(id, dto?.reason, principal.hospitalId))
}
