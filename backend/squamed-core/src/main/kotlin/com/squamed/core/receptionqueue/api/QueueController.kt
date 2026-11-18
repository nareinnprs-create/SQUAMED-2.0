package com.squamed.core.receptionqueue.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.receptionqueue.service.AnnouncementService
import com.squamed.core.receptionqueue.service.QueueService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueController(
  private val queueService: QueueService,
  private val announcementService: AnnouncementService,
  private val tenantResolver: TenantResolver,
) {
  // ADR-13: hospital users are pinned to their tenant; admins may choose via dto.hospitalId.
  @PostMapping("/entries")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun addToQueue(
    @RequestBody dto: QueueCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> =
    ApiResponse.ok(
      queueService.addToQueue(
        queueType = dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"),
        patientId = dto.patientId ?: throw IllegalArgumentException("PATIENT_ID_REQUIRED"),
        patientName = dto.patientName,
        visitId = dto.visitId,
        priority = dto.priority,
        servicePointId = dto.servicePointId,
        departmentId = dto.departmentId,
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
      ),
    )

  @GetMapping("/board")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun board(
    @RequestParam(name = "queueType") queueType: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<BoardEntry>> = ApiResponse.ok(queueService.board(queueType, principal.hospitalId))

  // 01.06 TV display app: whole-hospital board grouped per counter for the waiting-room screen.
  @GetMapping("/display-board")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun displayBoard(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DisplayBoardView> = ApiResponse.ok(queueService.displayBoard(principal.hospitalId))

  @PostMapping("/entries/call-next")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun callNext(
    @RequestBody dto: QueueTypeDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<BoardEntry> =
    ApiResponse.ok(queueService.callNext(dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"), principal.hospitalId))

  // 01.02: transfer moves a waiting entry to another queue; it gets a fresh token from the target sequence.
  @PostMapping("/entries/{queueType}/{tokenNumber}/transfer")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun transfer(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @RequestBody dto: QueueTransferDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> =
    ApiResponse.ok(
      queueService.transfer(
        queueType,
        tokenNumber,
        dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"),
        principal.hospitalId,
      ),
    )

  // 01.02: emergency bump raises the entry to emergency priority and notifies displays over WS (<2 s).
  @PostMapping("/entries/{queueType}/{tokenNumber}/bump")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun bump(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.bump(queueType, tokenNumber, principal.hospitalId))

  // 01.09 emergency fast-path: walk-in triage-lite tag boosts the live queue entry
  // (red -> emergency + queue.bumped + queue.urgent_arrival, yellow -> urgent, green -> no change).
  @PostMapping("/entries/{queueType}/{tokenNumber}/triage-lite")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun triageLite(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @RequestBody dto: QueueTriageLiteDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> =
    ApiResponse.ok(
      queueService.triageLite(
        queueType,
        tokenNumber,
        dto.tag ?: throw IllegalArgumentException("TRIAGE_TAG_REQUIRED"),
        principal.hospitalId,
      ),
    )

  // 01.02 re-print: stateless view fetch for kiosk/thermal printers (GET → no audit write).
  @GetMapping("/entries/{queueType}/{tokenNumber}/reprint")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun reprint(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.getEntry(queueType, tokenNumber, principal.hospitalId))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/call")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun call(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.call(queueType, tokenNumber, principal.hospitalId))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/start")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun start(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @RequestBody dto: QueueStartDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.start(queueType, tokenNumber, dto.servedBy, principal.hospitalId))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/complete")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun complete(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.complete(queueType, tokenNumber, principal.hospitalId))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/skip")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun skip(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.skip(queueType, tokenNumber, principal.hospitalId))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/postpone")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun postpone(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.postpone(queueType, tokenNumber, principal.hospitalId))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/no-show")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun noShow(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.noShow(queueType, tokenNumber, principal.hospitalId))

  @GetMapping("/entries/{queueType}/{tokenNumber}")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun getEntry(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.getEntry(queueType, tokenNumber, principal.hospitalId))

  @GetMapping("/service-points")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun listServicePoints(
    @RequestParam(name = "queueType", required = false) queueType: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<ServicePointDto>> = ApiResponse.ok(queueService.listServicePoints(queueType, principal.hospitalId))

  @PostMapping("/service-points")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun createServicePoint(
    @RequestBody dto: ServicePointCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<ServicePointDto> =
    ApiResponse.ok(
      queueService.createServicePoint(
        name = dto.name ?: throw IllegalArgumentException("SERVICE_POINT_NAME_REQUIRED"),
        code = dto.code,
        queueType = dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"),
        counterNumber = dto.counterNumber,
        status = dto.status,
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
      ),
    )

  @GetMapping("/statistics")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun statistics(
    @RequestParam(name = "queueType") queueType: String,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<QueueStatsView> = ApiResponse.ok(queueService.statistics(queueType, principal.hospitalId))

  // 01.07 voice announcements: zones map audio output to displays/counters; rules carry repeat + languages.
  @GetMapping("/zones")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun listZones(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<DisplayZoneDto>> = ApiResponse.ok(announcementService.listZones(principal.hospitalId))

  @PostMapping("/zones")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun createZone(
    @RequestBody dto: DisplayZoneCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<DisplayZoneDto> =
    ApiResponse.ok(
      announcementService.createZone(
        name = dto.name ?: throw IllegalArgumentException("ZONE_NAME_REQUIRED"),
        servicePointId = dto.servicePointId,
        volume = dto.volume,
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
      ),
    )

  @GetMapping("/announcement-rules")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun listRules(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AnnouncementRuleDto>> = ApiResponse.ok(announcementService.listRules(principal.hospitalId))

  @PostMapping("/announcement-rules")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun createRule(
    @RequestBody dto: AnnouncementRuleCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AnnouncementRuleDto> =
    ApiResponse.ok(
      announcementService.createRule(
        queueType = dto.queueType,
        repeatCount = dto.repeatCount,
        languages = dto.languages,
        zoneId = dto.zoneId,
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
      ),
    )

  // 01.07 contract /announcements:enqueue — manual TTS enqueue for chimes/staff-call use cases.
  @PostMapping("/announcements")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun enqueue(
    @RequestBody dto: AnnouncementEnqueueDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AnnouncementView> =
    ApiResponse.ok(
      announcementService.enqueue(
        hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
        texts = dto.texts ?: throw IllegalArgumentException("ANNOUNCEMENT_TEXT_REQUIRED"),
        zoneId = dto.zoneId,
        repeatCount = dto.repeatCount,
      ),
    )
}
