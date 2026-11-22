package com.squamed.core.ipd.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.ipd.service.AdmissionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// 04.01 bed board: live status per bed with its current occupant.
@RestController
@RequestMapping("/api/v1/beds")
class BedController(
  private val admissionService: AdmissionService,
) {
  @GetMapping("/board")
  @RequiresPermission("ipd:read:admission")
  @Transactional(readOnly = true)
  fun board(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<BedBoardRowView>> = ApiResponse.ok(admissionService.bedBoard(principal.hospitalId))

  @PostMapping("/{id}/cleaning")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun markCleaning(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<BedBoardRowView> = ApiResponse.ok(admissionService.markBed(id, "cleaning", principal.hospitalId))

  @PostMapping("/{id}/available")
  @RequiresPermission("ipd:write:admission")
  @Transactional
  fun markAvailable(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<BedBoardRowView> = ApiResponse.ok(admissionService.markBed(id, "available", principal.hospitalId))
}
