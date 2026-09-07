package com.squamed.core.scribe.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.scribe.service.ScribeSegment
import com.squamed.core.scribe.service.ScribeService
import com.squamed.core.scribe.service.SegmentJsonCodec
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

data class ScribeCreateRequest(
  val patientId: UUID? = null,
  val encounterId: UUID? = null,
  val patientName: String? = null,
  val chiefComplaint: String? = null,
)

data class ScribeStopRecordingRequest(
  val transcriptText: String? = null,
  val durationSeconds: Int? = null,
)

data class ScribeTranscriptRequest(
  val text: String,
  val segments: List<ScribeSegmentDto>? = null,
)

data class ScribeSegmentDto(
  val speaker: String,
  val text: String,
)

data class ScribeDraftRequest(
  val draft: String,
)

@RestController
@RequestMapping("/api/v1/scribe")
class ScribeController(
  private val scribeService: ScribeService,
  private val segmentCodec: SegmentJsonCodec,
) {
  @GetMapping("/sessions")
  @RequiresPermission("ai:read:agent")
  fun list(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(scribeService.list().map { it.toView(segmentCodec) })

  @GetMapping("/sessions/{id}")
  @RequiresPermission("ai:read:agent")
  fun detail(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.find(id).toView(segmentCodec))

  @PostMapping("/sessions")
  @RequiresPermission("ai:manage:agent")
  fun create(
    @RequestBody req: ScribeCreateRequest,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<Map<String, Any?>> {
    val session = scribeService.create(req.patientId, req.encounterId, req.patientName, req.chiefComplaint)
    session.hospitalId = principal.hospitalId
    return ApiResponse.ok(session.toView(segmentCodec))
  }

  @PostMapping("/sessions/{id}/start-recording")
  @RequiresPermission("ai:manage:agent")
  fun startRecording(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.startRecording(id).toView(segmentCodec))

  @PostMapping("/sessions/{id}/stop-recording")
  @RequiresPermission("ai:manage:agent")
  fun stopRecording(
    @PathVariable id: UUID,
    @RequestBody req: ScribeStopRecordingRequest,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.stopRecording(id, req.transcriptText, req.durationSeconds).toView(segmentCodec))

  @PostMapping("/sessions/{id}/audio", consumes = ["multipart/form-data"])
  @RequiresPermission("ai:manage:agent")
  fun transcribeAudio(
    @PathVariable id: UUID,
    @RequestParam("file") file: MultipartFile,
    @RequestParam("contentType", required = false) contentType: String?,
  ): ApiResponse<Map<String, Any?>> =
    ApiResponse.ok(
      scribeService.processAudio(id, file.bytes, contentType ?: file.contentType).toView(segmentCodec),
    )

  @PostMapping("/sessions/{id}/transcript")
  @RequiresPermission("ai:manage:agent")
  fun setTranscript(
    @PathVariable id: UUID,
    @RequestBody req: ScribeTranscriptRequest,
  ): ApiResponse<Map<String, Any?>> {
    val segments = req.segments?.map { ScribeSegment(it.speaker, it.text) }
    return ApiResponse.ok(scribeService.setTranscript(id, req.text, segments).toView(segmentCodec))
  }

  @PostMapping("/sessions/{id}/generate")
  @RequiresPermission("ai:manage:agent")
  fun generate(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.generateDraft(id).toView(segmentCodec))

  @PostMapping("/sessions/{id}/draft")
  @RequiresPermission("ai:manage:agent")
  fun updateDraft(
    @PathVariable id: UUID,
    @RequestBody req: ScribeDraftRequest,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.updateDraft(id, req.draft).toView(segmentCodec))

  @PostMapping("/sessions/{id}/sign")
  @RequiresPermission("ai:manage:agent")
  fun sign(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.sign(id).toView(segmentCodec))

  @DeleteMapping("/sessions/{id}")
  @RequiresPermission("ai:manage:agent")
  fun delete(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> {
    scribeService.delete(id)
    return ApiResponse.ok(mapOf("deleted" to true))
  }

  @GetMapping("/pipeline")
  @RequiresPermission("ai:read:agent")
  fun pipeline(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(scribeService.pipeline())
}

private fun com.squamed.core.scribe.domain.ScribeSession.toView(codec: SegmentJsonCodec): Map<String, Any?> =
  mapOf(
    "id" to id,
    "patientId" to patientId,
    "encounterId" to encounterId,
    "patientName" to patientName,
    "chiefComplaint" to chiefComplaint,
    "source" to source,
    "status" to status,
    "stage" to stage,
    "transcript" to transcript,
    "segments" to codec.decode(segmentsJson),
    "speakerMap" to codec.decodeMaps(speakerMapJson),
    "draft" to draft,
    "durationSeconds" to durationSeconds,
    "signedAt" to signedAt,
    "createdAt" to createdAt,
  )
