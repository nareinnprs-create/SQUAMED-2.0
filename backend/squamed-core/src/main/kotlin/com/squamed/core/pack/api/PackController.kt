package com.squamed.core.pack.api

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.pack.service.PackComponentInput
import com.squamed.core.pack.service.SpecialtyPackService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PackComponentDto(
  val componentType: String,
  val componentKey: String,
  val payload: JsonNode,
)

data class PackCreateDto(
  val code: String,
  val name: String,
  val specialty: String,
  val description: String? = null,
  val components: List<PackComponentDto>,
)

@RestController
@RequestMapping("/api/v1/packs")
class PackController(
  private val service: SpecialtyPackService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @RequiresPermission("pack:manage:pack")
  fun create(
    @RequestBody dto: PackCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      service.create(
        dto.code,
        dto.name,
        dto.specialty,
        dto.description,
        dto.components.map { PackComponentInput(it.componentType, it.componentKey, it.payload) },
        principal.userId,
      ),
    )

  @GetMapping
  @RequiresPermission("pack:read:pack")
  fun packs(): ApiResponse<Any> = ApiResponse.ok(service.packs())

  @PostMapping("/{code}/install")
  @RequiresPermission("pack:install:pack")
  fun install(
    @PathVariable code: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.install(tenantResolver.pinOrDefault(principal, null), code, principal.userId))

  @GetMapping("/installs")
  @RequiresPermission("pack:read:pack")
  fun installs(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(service.installs(tenantResolver.pinOrDefault(principal, null)))
}
