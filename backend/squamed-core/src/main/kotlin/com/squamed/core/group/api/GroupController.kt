package com.squamed.core.group.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.group.service.GroupConfigService
import com.squamed.core.group.service.GroupReferralService
import com.squamed.core.group.service.GroupService
import com.squamed.core.group.service.GroupShareService
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/group")
class GroupController(
  private val groupService: GroupService,
  private val configService: GroupConfigService,
  private val shareService: GroupShareService,
  private val referralService: GroupReferralService,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping("/groups")
  @RequiresPermission("group:manage:group")
  fun createGroup(
    @RequestBody dto: GroupCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<GroupView> = ApiResponse.ok(groupService.create(dto.code, dto.name, principal.userId))

  @PostMapping("/groups/{id}/hospitals/{hospitalId}")
  @RequiresPermission("group:manage:group")
  fun attachHospital(
    @PathVariable id: UUID,
    @PathVariable hospitalId: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<GroupView> = ApiResponse.ok(groupService.attachHospital(id, hospitalId, principal.userId))

  @GetMapping("/groups")
  @RequiresPermission("group:read:group")
  fun groups(): ApiResponse<List<GroupView>> = ApiResponse.ok(groupService.groups())

  @GetMapping("/groups/{id}")
  @RequiresPermission("group:read:group")
  fun group(
    @PathVariable id: UUID,
  ): ApiResponse<GroupView> = ApiResponse.ok(groupService.group(id))

  @GetMapping("/groups/{id}/directory")
  @RequiresPermission("group:read:group")
  fun directory(
    @PathVariable id: UUID,
  ): ApiResponse<List<DirectoryEntryView>> = ApiResponse.ok(groupService.directory(id))

  @PutMapping("/groups/{id}/config")
  @RequiresPermission("group:manage:group")
  fun publishConfig(
    @PathVariable id: UUID,
    @RequestBody dto: GroupConfigPublishDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConfigResolveView> = ApiResponse.ok(configService.publish(id, dto.type, dto.content, principal.userId))

  @GetMapping("/config/{type}")
  @RequiresPermission("group:read:group")
  fun resolveConfig(
    @PathVariable type: String,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ConfigResolveView> = ApiResponse.ok(configService.resolve(tenantResolver.pinOrDefault(principal, hospitalId), type))

  @GetMapping("/patients/{patientId}/share-candidates")
  @RequiresPermission("group:share:record")
  fun shareCandidates(
    @PathVariable patientId: UUID,
    @RequestParam(name = "toHospitalId") toHospitalId: UUID,
  ): ApiResponse<List<ShareCandidateView>> = ApiResponse.ok(shareService.candidates(patientId, toHospitalId))

  @PostMapping("/patients/{patientId}/share")
  @RequiresPermission("group:share:record")
  fun share(
    @PathVariable patientId: UUID,
    @RequestBody dto: ShareCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ShareView> = ApiResponse.ok(shareService.share(patientId, dto.toHospitalId, dto.consentId, principal.userId))

  @PostMapping("/shares/{id}/revoke")
  @RequiresPermission("group:share:record")
  fun revoke(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ShareView> = ApiResponse.ok(shareService.revoke(id, principal.userId))

  @GetMapping("/patients/{patientId}/access")
  @RequiresPermission("group:read:group")
  fun access(
    @PathVariable patientId: UUID,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AccessView> {
    val hid = tenantResolver.pinOrDefault(principal, hospitalId)
    if (principal.hospitalId != null && principal.hospitalId != hid) throw AccessDeniedException("GROUP_ACCESS_DENIED: hospital mismatch")
    return ApiResponse.ok(shareService.access(patientId, hid))
  }

  @PostMapping("/referrals")
  @RequiresPermission("group:create:referral")
  fun createReferral(
    @RequestBody dto: ReferralCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReferralView> =
    ApiResponse.ok(referralService.create(dto.patientId, dto.toHospitalId, dto.reason, dto.notes, principal.userId, principal.hospitalId))

  @PatchMapping("/referrals/{id}/status")
  @RequiresPermission("group:decide:referral")
  fun decideReferral(
    @PathVariable id: UUID,
    @RequestBody dto: ReferralStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ReferralView> = ApiResponse.ok(referralService.decide(id, dto.status, principal.userId, principal.hospitalId))

  @GetMapping("/referrals")
  @RequiresPermission("group:read:group")
  fun referrals(
    @RequestParam(name = "box", required = false) box: String?,
    @RequestParam(name = "status", required = false) status: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ReferralView>> {
    val hospitalId = tenantResolver.pinOrDefault(principal, null)
    return ApiResponse.ok(if (box.equals("sent", ignoreCase = true)) referralService.sent(hospitalId) else referralService.inbox(hospitalId, status))
  }
}
