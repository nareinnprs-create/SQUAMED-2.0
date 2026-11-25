package com.squamed.core.laboratory.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.laboratory.service.AstEntry
import com.squamed.core.laboratory.service.LabDepthService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class MicroCultureRequest(
  val labOrderId: UUID,
  val specimenType: String,
  val notes: String? = null,
)

data class OrganismRequest(
  val organismCode: String,
  val organismName: String,
)

data class AstResultDto(
  val antibioticCode: String,
  val antibioticName: String,
  val result: String,
  val mic: BigDecimal? = null,
)

data class AstRequest(
  val panel: String,
  val results: List<AstResultDto>,
)

data class PathSpecimenRequest(
  val patientId: UUID,
  val specimenType: String,
  val site: String? = null,
)

data class GrossRequest(
  val description: String,
)

data class SlideRequest(
  val stain: String,
)

data class PathSignOutRequest(
  val diagnosisCode: String,
  val diagnosisText: String,
)

@RestController
@RequestMapping("/api/v1/lab")
class LabDepthController(
  private val labDepthService: LabDepthService,
  private val tenantResolver: TenantResolver,
) {
  // -- microbiology ---------------------------------------------------------

  @PostMapping("/micro/cultures")
  @RequiresPermission("laboratory:write:lab")
  fun createCulture(
    @RequestBody dto: MicroCultureRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      labDepthService.createCulture(
        tenantResolver.pinOrDefault(principal, null),
        dto.labOrderId,
        dto.specimenType,
        dto.notes,
        principal.userId,
      ),
    )

  @GetMapping("/micro/cultures")
  @RequiresPermission("laboratory:read:lab")
  fun cultures(
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.cultures(tenantResolver.pinOrDefault(principal, null), status))

  @GetMapping("/micro/cultures/{id}")
  @RequiresPermission("laboratory:read:lab")
  fun cultureDetail(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.cultureDetail(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/micro/cultures/{id}/identify")
  @RequiresPermission("laboratory:write:lab")
  fun identifyOrganism(
    @PathVariable id: UUID,
    @RequestBody dto: OrganismRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      labDepthService.identifyOrganism(
        id,
        tenantResolver.pinOrDefault(principal, null),
        dto.organismCode,
        dto.organismName,
        principal.userId,
      ),
    )

  @PostMapping("/micro/cultures/{id}/ast")
  @RequiresPermission("laboratory:write:lab")
  fun recordAst(
    @PathVariable id: UUID,
    @RequestBody dto: AstRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      labDepthService.recordAst(
        id,
        tenantResolver.pinOrDefault(principal, null),
        dto.panel,
        dto.results.map { AstEntry(it.antibioticCode, it.antibioticName, it.result, it.mic) },
        principal.userId,
      ),
    )

  @PostMapping("/micro/cultures/{id}/finalize")
  @RequiresPermission("laboratory:verify:lab")
  fun finalizeCulture(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.finalizeCulture(id, tenantResolver.pinOrDefault(principal, null), principal.userId))

  @GetMapping("/micro/antibiogram")
  @RequiresPermission("laboratory:read:lab")
  fun antibiogram(
    @RequestParam(name = "organismCode") organismCode: String,
    @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
    @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.antibiogram(tenantResolver.pinOrDefault(principal, null), organismCode, from, to))

  // -- histopathology / cytology -------------------------------------------

  @PostMapping("/pathology/specimens")
  @RequiresPermission("laboratory:write:lab")
  fun accessionSpecimen(
    @RequestBody dto: PathSpecimenRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      labDepthService.accessionSpecimen(
        tenantResolver.pinOrDefault(principal, null),
        dto.patientId,
        dto.specimenType,
        dto.site,
        principal.userId,
      ),
    )

  @GetMapping("/pathology/specimens")
  @RequiresPermission("laboratory:read:lab")
  fun specimens(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.specimens(tenantResolver.pinOrDefault(principal, null)))

  @GetMapping("/pathology/specimens/{id}")
  @RequiresPermission("laboratory:read:lab")
  fun specimenDetail(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.specimenDetail(id, tenantResolver.pinOrDefault(principal, null)))

  @PostMapping("/pathology/specimens/{id}/gross")
  @RequiresPermission("laboratory:write:lab")
  fun gross(
    @PathVariable id: UUID,
    @RequestBody dto: GrossRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.gross(id, tenantResolver.pinOrDefault(principal, null), dto.description, principal.userId))

  @PostMapping("/pathology/specimens/{id}/slides")
  @RequiresPermission("laboratory:write:lab")
  fun addSlide(
    @PathVariable id: UUID,
    @RequestBody dto: SlideRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> = ApiResponse.ok(labDepthService.addSlide(id, tenantResolver.pinOrDefault(principal, null), dto.stain, principal.userId))

  @PostMapping("/pathology/specimens/{id}/slides/{slideId}/ready")
  @RequiresPermission("laboratory:write:lab")
  fun markSlideReady(
    @PathVariable id: UUID,
    @PathVariable slideId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      labDepthService.markSlideReady(id, slideId, tenantResolver.pinOrDefault(principal, null), principal.userId),
    )

  @PostMapping("/pathology/specimens/{id}/sign-out")
  @RequiresPermission("laboratory:verify:lab")
  fun signOut(
    @PathVariable id: UUID,
    @RequestBody dto: PathSignOutRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Any> =
    ApiResponse.ok(
      labDepthService.signOut(
        id,
        tenantResolver.pinOrDefault(principal, null),
        dto.diagnosisCode,
        dto.diagnosisText,
        principal.userId,
      ),
    )
}
