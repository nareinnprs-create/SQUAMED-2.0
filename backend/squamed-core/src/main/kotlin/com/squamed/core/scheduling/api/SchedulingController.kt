package com.squamed.core.scheduling.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.scheduling.service.SchedulingService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * 01.01 scheduling engine API: session-based doctor schedule templates, lazily generated session
 * slots (in_person + teleconsult), and the appointment waitlist. Booking rides POST /appointments
 * with a slotId (AppointmentController), keeping the booking guards in one place.
 */
@RestController
@RequestMapping("/api/v1/schedules")
class SchedulingController(
  private val schedulingService: SchedulingService,
) {
  @PostMapping("/templates")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("scheduling:write:schedule")
  @Transactional
  fun createTemplate(
    @RequestBody dto: ScheduleTemplateCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ScheduleTemplateView> = ApiResponse.ok(ScheduleTemplateView.from(schedulingService.createTemplate(principal, dto)))

  @GetMapping("/templates")
  @RequiresPermission("scheduling:read:schedule")
  @Transactional(readOnly = true)
  fun listTemplates(
    @RequestParam(name = "doctorId", required = false) doctorId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ScheduleTemplateView>> = ApiResponse.ok(schedulingService.templates(principal, doctorId).map { ScheduleTemplateView.from(it) })

  @PutMapping("/templates/{id}")
  @RequiresPermission("scheduling:write:schedule")
  @Transactional
  fun updateTemplate(
    @PathVariable id: UUID,
    @RequestBody dto: ScheduleTemplateUpdateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ScheduleTemplateView> = ApiResponse.ok(ScheduleTemplateView.from(schedulingService.updateTemplate(principal, id, dto)))

  /** Deactivation rides the update endpoint's active flag (templates are never deleted — history). */
  @DeleteMapping("/templates/{id}")
  @RequiresPermission("scheduling:write:schedule")
  @Transactional
  fun deactivateTemplate(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ScheduleTemplateView> =
    ApiResponse.ok(ScheduleTemplateView.from(schedulingService.updateTemplate(principal, id, ScheduleTemplateUpdateDto(active = false))))

  /**
   * Idempotent slot generation for the from..to date window (clamped to 14 days): expands active templates for
   * the requested weekday dates and returns all materialized slots with booking counts.
   */
  @GetMapping("/{doctorId}/slots")
  @RequiresPermission("scheduling:read:schedule")
  @Transactional
  fun slots(
    @PathVariable doctorId: UUID,
    @RequestParam(name = "from", required = false) from: LocalDate?,
    @RequestParam(name = "to", required = false) to: LocalDate?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<SlotView>> {
    val today = LocalDate.now()
    val fromDate = from ?: today
    val toDate = to ?: fromDate
    return ApiResponse.ok(
      schedulingService
        .generateSlots(principal, doctorId, fromDate, toDate)
        .map { SlotView.from(it) },
    )
  }

  @PostMapping("/waitlist")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun addToWaitlist(
    @RequestBody dto: WaitlistCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WaitlistView> = ApiResponse.ok(WaitlistView.from(schedulingService.addToWaitlist(principal, dto)))

  @GetMapping("/waitlist")
  @RequiresPermission("scheduling:read:appointment")
  @Transactional(readOnly = true)
  fun waitlist(
    @RequestParam(name = "doctorId", required = false) doctorId: UUID?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<WaitlistView>> = ApiResponse.ok(schedulingService.waitlist(principal, doctorId).map { WaitlistView.from(it) })

  @DeleteMapping("/waitlist/{id}")
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun cancelWaitlistEntry(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<WaitlistView> = ApiResponse.ok(WaitlistView.from(schedulingService.cancelWaitlistEntry(principal, id)))

  @PostMapping("/waitlist/{id}/promote")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun promoteWaitlistEntry(
    @PathVariable id: UUID,
    @RequestBody dto: WaitlistPromoteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> = ApiResponse.ok(AppointmentView.from(schedulingService.promoteFromWaitlist(principal, id, dto)))
}
