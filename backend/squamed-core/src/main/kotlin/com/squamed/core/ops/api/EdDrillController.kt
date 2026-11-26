package com.squamed.core.ops.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.ops.service.DrillCasualtyInput
import com.squamed.core.ops.service.EdDrillService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class DrillCasualtyDto(
  val patientId: UUID,
  val triageLevel: Int,
  val chiefComplaint: String,
)

data class DrillRunDto(
  val name: String,
  val casualties: List<DrillCasualtyDto>,
)

@RestController
@RequestMapping("/api/v1/ops/ed/drills")
class EdDrillController(
  private val service: EdDrillService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @RequiresPermission("ops:write:ed")
  fun run(
    @RequestBody dto: DrillRunDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.run(
        tenantResolver.pinOrDefault(principal, null),
        dto.name,
        dto.casualties.map { DrillCasualtyInput(it.patientId, it.triageLevel, it.chiefComplaint) },
        principal.userId,
      ),
    )

  @GetMapping
  @RequiresPermission("ops:read:ops")
  fun drills(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.drills(tenantResolver.pinOrDefault(principal, null)))
}
