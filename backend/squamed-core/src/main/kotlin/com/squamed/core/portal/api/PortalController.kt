package com.squamed.core.portal.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.portal.service.PortalService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/portal")
class PortalController(
  private val portalService: PortalService,
) {
  @PostMapping("/accounts")
  @RequiresPermission("portal:manage:account")
  fun linkAccount(
    @RequestBody dto: PortalAccountLinkDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PortalMeView> = ApiResponse.ok(portalService.linkAccount(dto, principal.userId))

  @GetMapping("/me")
  @RequiresPermission("portal:read:self")
  fun me(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PortalMeView> = ApiResponse.ok(portalService.meFor(principal.userId))

  @GetMapping("/appointments")
  @RequiresPermission("portal:read:self")
  fun appointments(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalAppointmentView>> = ApiResponse.ok(portalService.appointments(principal.userId))

  @GetMapping("/slots")
  @RequiresPermission("portal:book:self")
  fun slots(
    @RequestParam(name = "doctorId") doctorId: UUID,
    @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
    @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalSlotView>> = ApiResponse.ok(portalService.slots(principal.userId, doctorId, from, to))

  @PostMapping("/appointments")
  @RequiresPermission("portal:book:self")
  fun book(
    @RequestBody dto: PortalBookDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PortalAppointmentView> = ApiResponse.ok(portalService.book(principal.userId, principal, dto.slotId, dto.appointmentType, dto.reason))

  @GetMapping("/reports")
  @RequiresPermission("portal:read:self")
  fun reports(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalReportView>> = ApiResponse.ok(portalService.reports(principal.userId))

  @GetMapping("/prescriptions")
  @RequiresPermission("portal:read:self")
  fun prescriptions(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalPrescriptionView>> = ApiResponse.ok(portalService.prescriptions(principal.userId))

  @GetMapping("/bills")
  @RequiresPermission("portal:read:self")
  fun bills(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalBillView>> = ApiResponse.ok(portalService.bills(principal.userId))

  @PostMapping("/bills/{id}/pay")
  @RequiresPermission("portal:pay:self")
  fun pay(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PortalBillView> = ApiResponse.ok(portalService.pay(principal.userId, id))

  @GetMapping("/discharge")
  @RequiresPermission("portal:read:self")
  fun discharge(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalDischargeView>> = ApiResponse.ok(portalService.discharge(principal.userId))

  @GetMapping("/careplans")
  @RequiresPermission("portal:read:self")
  fun carePlans(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalCarePlanView>> = ApiResponse.ok(portalService.carePlans(principal.userId))

  @GetMapping("/notifications")
  @RequiresPermission("portal:read:self")
  fun notifications(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalNotificationView>> = ApiResponse.ok(portalService.notifications(principal.userId))

  @PostMapping("/notifications/{id}/read")
  @RequiresPermission("portal:read:self")
  fun readNotification(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PortalNotificationView> = ApiResponse.ok(portalService.readNotification(principal.userId, id))

  @PostMapping("/push/subscribe")
  @RequiresPermission("portal:read:self")
  fun subscribePush(
    @RequestBody dto: PortalPushSubscribeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<PortalPushSubscriptionView> = ApiResponse.ok(portalService.subscribePush(principal.userId, dto.endpoint, dto.p256dh, dto.auth))

  @GetMapping("/push/subscriptions")
  @RequiresPermission("portal:read:self")
  fun pushSubscriptions(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<PortalPushSubscriptionView>> = ApiResponse.ok(portalService.pushSubscriptions(principal.userId))

  @GetMapping("/push/status")
  @RequiresPermission("portal:read:self")
  fun pushStatus(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(portalService.pushStatus(principal.userId))
}
