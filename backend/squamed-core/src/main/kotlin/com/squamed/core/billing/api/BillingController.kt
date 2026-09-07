package com.squamed.core.billing.api

import com.squamed.core.billing.service.BillingService
import com.squamed.core.common.api.ApiResponse
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
) {
  @GetMapping("/tariffs")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listTariffs(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
  ): ApiResponse<List<TariffDto>> = ApiResponse.ok(billingService.listTariffs(search, active))

  @PostMapping("/tariffs")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun createTariff(
    @RequestBody dto: TariffDto,
  ): ApiResponse<TariffDto> = ApiResponse.ok(billingService.createTariff(dto))

  @PutMapping("/tariffs/{id}")
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun updateTariff(
    @PathVariable id: UUID,
    @RequestBody dto: TariffDto,
  ): ApiResponse<TariffDto> = ApiResponse.ok(billingService.updateTariff(id, dto))

  @PostMapping("/charges")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun postCharge(
    @RequestBody dto: PostChargeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ChargeView> = ApiResponse.ok(billingService.postCharge(dto, principal.userId))

  @GetMapping("/charges")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listCharges(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "visitId", required = false) visitId: UUID?,
  ): ApiResponse<List<ChargeView>> = ApiResponse.ok(billingService.listCharges(patientId, visitId))

  @PostMapping("/invoices")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun issueInvoice(
    @RequestBody dto: IssueInvoiceDto,
  ): ApiResponse<InvoiceDetailView> {
    val patientId = dto.patientId ?: throw IllegalArgumentException("PATIENT_ID_REQUIRED")
    return ApiResponse.ok(billingService.issueInvoice(patientId, dto.visitId, dto.discountQar))
  }

  @GetMapping("/invoices")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun listInvoices(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
  ): ApiResponse<List<InvoiceView>> = ApiResponse.ok(billingService.listInvoices(patientId, status))

  @GetMapping("/invoices/{id}")
  @RequiresPermission("billing:read:invoice")
  @Transactional(readOnly = true)
  fun getInvoice(
    @PathVariable id: UUID,
  ): ApiResponse<InvoiceDetailView> = ApiResponse.ok(billingService.getInvoice(id))

  @PostMapping("/invoices/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("billing:collect:payment")
  @Transactional
  fun recordPayment(
    @PathVariable id: UUID,
    @RequestBody dto: PaymentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PaymentView> = ApiResponse.ok(billingService.recordPayment(id, dto, principal.userId))

  @PostMapping("/invoices/{id}/cancel")
  @RequiresPermission("billing:write:invoice")
  @Transactional
  fun cancelInvoice(
    @PathVariable id: UUID,
    @RequestBody(required = false) dto: CancelInvoiceDto?,
  ): ApiResponse<InvoiceDetailView> = ApiResponse.ok(billingService.cancelInvoice(id, dto?.reason))
}
