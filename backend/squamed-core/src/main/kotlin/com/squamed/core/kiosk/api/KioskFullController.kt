package com.squamed.core.kiosk.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.kiosk.service.KioskExtrasService
import com.squamed.core.kiosk.service.KioskPaymentLookupView
import com.squamed.core.kiosk.service.KioskRouteView
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

data class KioskIdentifierRequest(
  val identifierType: String,
  val identifier: String,
  val locale: String = "en",
)

data class KioskPayRequest(
  val identifierType: String,
  val identifier: String,
  val invoiceId: UUID,
  val amountQar: BigDecimal,
  val method: String,
  val locale: String = "en",
)

data class KioskCollectRequest(
  val identifierType: String,
  val identifier: String,
  val reportType: String,
  val reportId: UUID,
  val locale: String = "en",
)

data class KioskCampusRequest(
  val code: String,
  val name: String,
)

data class KioskNodeRequest(
  val code: String,
  val name: String,
  val nodeType: String = "ROOM",
  val floor: String? = null,
  val x: BigDecimal? = null,
  val y: BigDecimal? = null,
)

data class KioskEdgeRequest(
  val fromCode: String,
  val toCode: String,
  val distanceM: BigDecimal,
  val accessible: Boolean = true,
  val bidirectional: Boolean = true,
)

data class KioskRouteRequest(
  val campusId: UUID,
  val fromCode: String,
  val toCode: String,
  val accessibleOnly: Boolean = false,
  val locale: String = "en",
)

@RestController
@RequestMapping("/api/v1/kiosk")
class KioskFullController(
  private val service: KioskExtrasService,
) {
  @PostMapping("/payment/lookup")
  @RequiresPermission("kiosk:read:kiosk")
  fun paymentLookup(
    @RequestBody dto: KioskIdentifierRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<KioskPaymentLookupView> = ApiResponse.ok(service.paymentLookup(hospitalId(principal), dto.identifierType, dto.identifier))

  @PostMapping("/payment/pay")
  @RequiresPermission("kiosk:write:kiosk")
  fun pay(
    @RequestBody dto: KioskPayRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.pay(
        hospitalId(principal),
        dto.identifierType,
        dto.identifier,
        dto.invoiceId,
        dto.amountQar,
        dto.method,
        dto.locale,
        principal.userId,
      ),
    )

  @PostMapping("/reports/lookup")
  @RequiresPermission("kiosk:read:kiosk")
  fun reportLookup(
    @RequestBody dto: KioskIdentifierRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.reportLookup(hospitalId(principal), dto.identifierType, dto.identifier))

  @PostMapping("/reports/collect")
  @RequiresPermission("kiosk:write:kiosk")
  fun collectReport(
    @RequestBody dto: KioskCollectRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.collectReport(
        hospitalId(principal),
        dto.identifierType,
        dto.identifier,
        dto.reportType,
        dto.reportId,
        dto.locale,
        principal.userId,
      ),
    )

  @PostMapping("/map/campuses")
  @RequiresPermission("kiosk:write:kiosk")
  fun createCampus(
    @RequestBody dto: KioskCampusRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.createCampus(hospitalId(principal), dto.code, dto.name, principal.userId))

  @GetMapping("/map/campuses")
  @RequiresPermission("kiosk:read:kiosk")
  fun campuses(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.campuses(hospitalId(principal)))

  @PostMapping("/map/campuses/{id}/nodes")
  @RequiresPermission("kiosk:write:kiosk")
  fun addNode(
    @PathVariable id: UUID,
    @RequestBody dto: KioskNodeRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.addNode(
        hospitalId(principal),
        id,
        dto.code,
        dto.name,
        dto.nodeType,
        dto.floor,
        dto.x,
        dto.y,
        principal.userId,
      ),
    )

  @PostMapping("/map/campuses/{id}/edges")
  @RequiresPermission("kiosk:write:kiosk")
  fun addEdge(
    @PathVariable id: UUID,
    @RequestBody dto: KioskEdgeRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.addEdge(
        hospitalId(principal),
        id,
        dto.fromCode,
        dto.toCode,
        dto.distanceM,
        dto.accessible,
        dto.bidirectional,
        principal.userId,
      ),
    )

  @PostMapping("/map/route")
  @RequiresPermission("kiosk:read:kiosk")
  fun route(
    @RequestBody dto: KioskRouteRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<KioskRouteView> =
    ApiResponse.ok(
      service.route(hospitalId(principal), dto.campusId, dto.fromCode, dto.toCode, dto.accessibleOnly, dto.locale),
    )

  private fun hospitalId(principal: SquamedPrincipal): UUID = principal.hospitalId ?: throw IllegalArgumentException("HOSPITAL_CONTEXT_REQUIRED")
}
