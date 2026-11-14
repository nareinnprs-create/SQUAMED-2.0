package com.squamed.core.notification.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.notification.service.NotificationDispatchService
import com.squamed.core.notification.service.NotificationService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
  private val notificationService: NotificationService,
  private val dispatchService: NotificationDispatchService,
  private val tenantResolver: TenantResolver,
) {
  @GetMapping
  @RequiresPermission("notification:read:notification")
  @Transactional(readOnly = true)
  fun listNotifications(
    @RequestParam(name = "unreadOnly", required = false) unreadOnly: Boolean?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<NotificationView>> = ApiResponse.ok(notificationService.listForUser(principal.userId, unreadOnly))

  @GetMapping("/unread-count")
  @RequiresPermission("notification:read:notification")
  @Transactional(readOnly = true)
  fun unreadCount(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Long> = ApiResponse.ok(notificationService.countUnread(principal.userId))

  @PostMapping("/{id}/read")
  @RequiresPermission("notification:read:notification")
  @Transactional
  fun markRead(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NotificationView> = ApiResponse.ok(notificationService.markRead(id, principal.userId))

  @PostMapping("/read-all")
  @RequiresPermission("notification:read:notification")
  @Transactional
  fun markAllRead(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Long> = ApiResponse.ok(notificationService.markAllRead(principal.userId))

  /** Chunk item 12 `/notifications:send`: outbound channels behind the per-channel consent + DND gates. */
  @PostMapping("/send")
  @RequiresPermission("notification:send:notification")
  @Transactional
  fun send(
    @RequestBody dto: NotificationDispatchDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<NotificationRequestView> =
    ApiResponse.ok(dispatchService.dispatch(dto.copy(hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId)), principal.userId))

  /** Dispatch ledger: every outbound attempt with its gate outcome (consent/DND/provider). */
  @GetMapping("/requests")
  @RequiresPermission("notification:send:notification")
  @Transactional(readOnly = true)
  fun listRequests(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<NotificationRequestView>> = ApiResponse.ok(dispatchService.listRequests(patientId, tenantResolver.pin(principal, null)))
}
