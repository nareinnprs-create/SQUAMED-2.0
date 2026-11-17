package com.squamed.core.receptionqueue.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.receptionqueue.service.KioskService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 01.08 kiosk-lite: self-service OPD check-in terminal. */
@RestController
@RequestMapping("/api/v1/kiosk")
class KioskController(
  private val kioskService: KioskService,
) {
  // The kiosk device logs in with the KIOSK role, which pins it to one hospital (ADR-13).
  @PostMapping("/check-in")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun checkIn(
    @RequestBody dto: KioskCheckInRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<KioskSlipDto> =
    ApiResponse.ok(
      kioskService.checkIn(
        dto,
        principal.hospitalId ?: throw IllegalArgumentException("HOSPITAL_CONTEXT_REQUIRED"),
      ),
    )
}
