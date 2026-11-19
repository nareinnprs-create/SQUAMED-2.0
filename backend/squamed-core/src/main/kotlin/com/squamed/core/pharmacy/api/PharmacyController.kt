package com.squamed.core.pharmacy.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.pharmacy.service.PharmacyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
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

  // --- 02.06: formulary / indents / stock ledger / alerts / wastage / controlled register ---

  @GetMapping("/formulary")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listFormulary(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<FormularyEntryView>> = ApiResponse.ok(pharmacyService.listFormulary(principal.hospitalId))

  @PostMapping("/formulary")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun addToFormulary(
    @RequestBody dto: FormularyRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<FormularyEntryView> = ApiResponse.ok(pharmacyService.addToFormulary(dto, principal))

  @DeleteMapping("/formulary/{id}")
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun removeFromFormulary(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<FormularyEntryView> = ApiResponse.ok(pharmacyService.removeFromFormulary(id, principal.hospitalId))

  @GetMapping("/indents")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listIndents(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<IndentView>> = ApiResponse.ok(pharmacyService.listIndents(principal.hospitalId))

  @PostMapping("/indents")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun createIndent(
    @RequestBody dto: IndentRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IndentView> = ApiResponse.ok(pharmacyService.createIndent(dto, principal))

  @PostMapping("/indents/{id}/receive")
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun receiveIndent(
    @PathVariable id: UUID,
    @RequestBody dto: IndentReceiveRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<IndentView> = ApiResponse.ok(pharmacyService.receiveIndent(id, dto, principal.hospitalId, principal))

  @GetMapping("/stock/ledger")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listLedger(
    @RequestParam(name = "stockId", required = false) stockId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<StockLedgerView>> = ApiResponse.ok(pharmacyService.listLedger(stockId, principal.hospitalId))

  @GetMapping("/stock/alerts")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listAlerts(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PharmacyStockView>> = ApiResponse.ok(pharmacyService.listAlerts(principal.hospitalId))

  @PostMapping("/stock/{id}/wastage")
  @RequiresPermission("pharmacy:write:stock")
  @Transactional
  fun recordWastage(
    @PathVariable id: UUID,
    @RequestBody dto: WastageRequestDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PharmacyStockView> = ApiResponse.ok(pharmacyService.recordWastage(id, dto, principal.hospitalId, principal))

  @GetMapping("/controlled-register")
  @RequiresPermission("pharmacy:read:stock")
  @Transactional(readOnly = true)
  fun listControlledRegister(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ControlledRegisterView>> = ApiResponse.ok(pharmacyService.listControlledRegister(principal.hospitalId))
}
