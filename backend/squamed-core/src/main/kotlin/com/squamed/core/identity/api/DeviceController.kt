package com.squamed.core.identity.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.identity.service.DeviceRegistryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 00.03 hardening (chunk-00 item 4): device session management.
 * Deliberately not part of AuthController so the AuditAspect audits every call.
 */
@RestController
@RequestMapping("/api/v1/auth/devices")
class DeviceController(
  private val deviceRegistryService: DeviceRegistryService,
) {
  @GetMapping
  fun listDevices(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ResponseEntity<ApiResponse<List<DeviceRegistryService.DeviceView>>> =
    ResponseEntity.ok(ApiResponse.ok(deviceRegistryService.listDevices(principal.userId)))

  @DeleteMapping("/{deviceId}")
  fun revokeDevice(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable deviceId: UUID,
  ): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
    deviceRegistryService.revokeDevice(principal.userId, deviceId)
    return ResponseEntity.ok(ApiResponse.ok(mapOf("revoked" to true)))
  }
}
