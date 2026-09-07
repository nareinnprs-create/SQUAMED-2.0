package com.squamed.core.receptionqueue.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.receptionqueue.service.QueueService
import org.springframework.http.HttpStatus
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
) {
  @PostMapping("/entries")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun addToQueue(
    @RequestBody dto: QueueCreateDto,
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
      ),
    )

  @GetMapping("/board")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun board(
    @RequestParam(name = "queueType") queueType: String,
  ): ApiResponse<List<BoardEntry>> = ApiResponse.ok(queueService.board(queueType))

  @PostMapping("/entries/call-next")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun callNext(
    @RequestBody dto: QueueTypeDto,
  ): ApiResponse<BoardEntry> = ApiResponse.ok(queueService.callNext(dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED")))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/call")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun call(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.call(queueType, tokenNumber))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/start")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun start(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
    @RequestBody dto: QueueStartDto,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.start(queueType, tokenNumber, dto.servedBy))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/complete")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun complete(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.complete(queueType, tokenNumber))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/skip")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun skip(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.skip(queueType, tokenNumber))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/postpone")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun postpone(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.postpone(queueType, tokenNumber))

  @PatchMapping("/entries/{queueType}/{tokenNumber}/no-show")
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun noShow(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.noShow(queueType, tokenNumber))

  @GetMapping("/entries/{queueType}/{tokenNumber}")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun getEntry(
    @PathVariable queueType: String,
    @PathVariable tokenNumber: String,
  ): ApiResponse<QueueEntryView> = ApiResponse.ok(queueService.getEntry(queueType, tokenNumber))

  @GetMapping("/service-points")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun listServicePoints(
    @RequestParam(name = "queueType", required = false) queueType: String?,
  ): ApiResponse<List<ServicePointDto>> = ApiResponse.ok(queueService.listServicePoints(queueType))

  @PostMapping("/service-points")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("queue:manage:queue")
  @Transactional
  fun createServicePoint(
    @RequestBody dto: ServicePointCreateDto,
  ): ApiResponse<ServicePointDto> =
    ApiResponse.ok(
      queueService.createServicePoint(
        name = dto.name ?: throw IllegalArgumentException("SERVICE_POINT_NAME_REQUIRED"),
        code = dto.code,
        queueType = dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"),
        counterNumber = dto.counterNumber,
        status = dto.status,
      ),
    )

  @GetMapping("/statistics")
  @RequiresPermission("queue:read:queue")
  @Transactional(readOnly = true)
  fun statistics(
    @RequestParam(name = "queueType") queueType: String,
  ): ApiResponse<QueueStatsView> = ApiResponse.ok(queueService.statistics(queueType))
}
