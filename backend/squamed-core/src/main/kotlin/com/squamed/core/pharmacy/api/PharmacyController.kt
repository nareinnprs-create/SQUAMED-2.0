package com.squamed.core.pharmacy.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.pharmacy.service.PharmacyService
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
@RequestMapping("/api/v1/pharmacy")
class PharmacyController(
  private val pharmacyService: PharmacyService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping("/stock")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listStock(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PharmacyStockView>> = ApiResponse.ok(pharmacyService.listStock(search, active, principal.hospitalId))

  @PostMapping("/stock")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun addStock(
    @RequestBody dto: StockRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PharmacyStockView> = ApiResponse.ok(pharmacyService.addStock(dto, principal))

  @PutMapping("/stock/{id}")
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun updateStock(
    @PathVariable id: UUID,
    @RequestBody dto: StockUpdateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PharmacyStockView> = ApiResponse.ok(pharmacyService.updateStock(id, dto, principal.hospitalId))

  @PostMapping("/dispensations")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("pharmacy:dispense:prescription")
  @Transactional
  fun dispense(
    @RequestBody dto: DispenseRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DispensationView> = ApiResponse.ok(pharmacyService.dispense(dto, principal.userId, tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/dispensations")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listDispensations(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<DispensationView>> = ApiResponse.ok(pharmacyService.listDispensations(patientId, status, principal.hospitalId))

  @PostMapping("/dispensations/{id}/verify")
  @RequiresPermission("pharmacy:dispense:prescription")
  @Transactional
  fun verifyDispensation(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DispensationView> = ApiResponse.ok(pharmacyService.verifyDispensation(id, principal.userId, principal.hospitalId))

  @PostMapping("/dispensations/{id}/return")
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun returnOrCancel(
    @PathVariable id: UUID,
    @RequestBody(required = false) dto: ReturnDispensationRequest?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DispensationView> = ApiResponse.ok(pharmacyService.returnOrCancel(id, dto?.reason, principal.hospitalId))
}
