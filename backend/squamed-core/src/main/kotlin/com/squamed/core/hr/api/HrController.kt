package com.squamed.core.hr.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.hr.service.HrAttendanceService
import com.squamed.core.hr.service.HrCredentialService
import com.squamed.core.hr.service.HrLeaveService
import com.squamed.core.hr.service.HrReportService
import com.squamed.core.hr.service.HrRosterService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/hr")
class HrController(
  private val rosterService: HrRosterService,
  private val leaveService: HrLeaveService,
  private val attendanceService: HrAttendanceService,
  private val credentialService: HrCredentialService,
  private val reportService: HrReportService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/rosters")
  @RequiresPermission("hr:write:hr")
  fun createRoster(
    @RequestBody dto: RosterCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RosterView> = ApiResponse.ok(rosterService.createRoster(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/rosters")
  @RequiresPermission("hr:read:hr")
  fun rosters(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RosterView>> = ApiResponse.ok(rosterService.list(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/rosters/{id}")
  @RequiresPermission("hr:read:hr")
  fun roster(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RosterView> = ApiResponse.ok(rosterService.get(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/rosters/{id}/shifts")
  @RequiresPermission("hr:write:hr")
  fun addShift(
    @PathVariable id: UUID,
    @RequestBody dto: ShiftCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ShiftView> = ApiResponse.ok(rosterService.addShift(id, tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @PostMapping("/rosters/{id}/publish")
  @RequiresPermission("hr:write:hr")
  fun publishRoster(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<RosterView> = ApiResponse.ok(rosterService.publish(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @PostMapping("/leave")
  @RequiresPermission("hr:write:hr")
  fun requestLeave(
    @RequestBody dto: LeaveCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LeaveView> = ApiResponse.ok(leaveService.request(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @PatchMapping("/leave/{id}")
  @RequiresPermission("hr:approve:leave")
  fun decideLeave(
    @PathVariable id: UUID,
    @RequestBody dto: LeaveDecisionDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<LeaveView> = ApiResponse.ok(leaveService.decide(id, tenantResolver.pinOrDefault(principal, null), dto.status, principal.userId))

  @GetMapping("/leave")
  @RequiresPermission("hr:read:hr")
  fun leave(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<LeaveView>> = ApiResponse.ok(leaveService.list(tenantResolver.pinOrDefault(principal, null), status))

  @PostMapping("/attendance")
  @RequiresPermission("hr:write:hr")
  fun recordAttendance(
    @RequestBody dto: AttendanceDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AttendanceView> = ApiResponse.ok(attendanceService.record(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/attendance")
  @RequiresPermission("hr:read:hr")
  fun attendance(
    @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AttendanceView>> = ApiResponse.ok(attendanceService.list(tenantResolver.pinOrDefault(principal, null), date))

  @PostMapping("/credentials")
  @RequiresPermission("hr:write:hr")
  fun createCredential(
    @RequestBody dto: CredentialCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<CredentialView> = ApiResponse.ok(credentialService.create(tenantResolver.pinOrDefault(principal, null), dto, principal.userId))

  @GetMapping("/credentials")
  @RequiresPermission("hr:read:hr")
  fun credentials(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CredentialView>> = ApiResponse.ok(credentialService.list(tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/credentials/scan")
  @RequiresPermission("hr:write:hr")
  fun scanCredentials(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<CredentialView>> = ApiResponse.ok(credentialService.scan(tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/ratio")
  @RequiresPermission("hr:read:hr")
  fun ratio(
    @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<RatioRowView>> = ApiResponse.ok(reportService.nursePatientRatio(tenantResolver.pinOrDefault(principal, null), date))

  @GetMapping("/payroll/export")
  @RequiresPermission("hr:read:hr")
  fun payrollExport(
    @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
    @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PayrollExportView> = ApiResponse.ok(reportService.payrollExport(tenantResolver.pinOrDefault(principal, null), from, to))
}
