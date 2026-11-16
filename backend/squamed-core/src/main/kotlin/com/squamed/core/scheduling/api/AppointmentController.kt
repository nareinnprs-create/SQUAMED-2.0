package com.squamed.core.scheduling.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.scheduling.domain.Appointment
import com.squamed.core.scheduling.repository.AppointmentRepository
import com.squamed.core.scheduling.service.SchedulingService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController(
  private val appointmentRepository: AppointmentRepository,
  private val eventPublisher: DomainEventPublisher,
  private val tenantResolver: TenantResolver,
  private val schedulingService: SchedulingService,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun createAppointment(
    @RequestBody dto: AppointmentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> {
    // 01.01 slot-based booking: guards (tenancy, past, no-show policy, overbook factor) live in
    // SchedulingService.book, which also consumes the slot and publishes appointment.created.
    if (dto.slotId != null) {
      val slotId = dto.slotId
      val appointment =
        schedulingService.book(
          principal,
          slotId,
          SchedulingService.BookingRequest(
            patientId = dto.patientId ?: throw IllegalArgumentException("APPOINTMENT_PATIENT_REQUIRED"),
            appointmentType = dto.appointmentType ?: throw IllegalArgumentException("APPOINTMENT_TYPE_REQUIRED"),
            reason = dto.reason,
            notes = dto.notes,
            visitType = dto.visitType,
            priority = dto.priority,
            externalRef = dto.externalRef,
            source = dto.source,
            cancellationFee = dto.cancellationFee,
          ),
          overrideNoShowLimit = dto.overrideNoShowLimit ?: false,
        )
      return ApiResponse.ok(AppointmentView.from(appointment))
    }
    val start = dto.scheduledStart ?: throw IllegalArgumentException("APPOINTMENT_START_REQUIRED")
    val end = dto.scheduledEnd ?: throw IllegalArgumentException("APPOINTMENT_END_REQUIRED")
    if (!end.isAfter(start)) throw IllegalArgumentException("APPOINTMENT_END_BEFORE_START")
    val appointment =
      appointmentRepository.save(
        Appointment(
          hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId),
          patientId = dto.patientId ?: throw IllegalArgumentException("APPOINTMENT_PATIENT_REQUIRED"),
          departmentId = dto.departmentId,
          specialtyId = dto.specialtyId,
          doctorId = dto.doctorId ?: throw IllegalArgumentException("APPOINTMENT_DOCTOR_REQUIRED"),
          appointmentType = dto.appointmentType ?: throw IllegalArgumentException("APPOINTMENT_TYPE_REQUIRED"),
          scheduledStart = start,
          scheduledEnd = end,
          durationMinutes = dto.durationMinutes ?: 15,
          reason = dto.reason,
          notes = dto.notes,
          visitType = dto.visitType ?: "outpatient",
          priority = dto.priority ?: "routine",
          telemedicine = dto.telemedicine ?: false,
          roomId = dto.roomId,
          externalRef = dto.externalRef,
          source = dto.source ?: "clinic",
          cancellationFee = dto.cancellationFee,
        ),
      )
    return ApiResponse.ok(AppointmentView.from(appointment))
  }

  @GetMapping("/{id}")
  @RequiresPermission("scheduling:read:appointment")
  @Transactional(readOnly = true)
  fun getAppointment(
    @PathVariable id: UUID,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> {
    val appointment = appointmentRepository.findById(id).orElseThrow { IllegalArgumentException("APPOINTMENT_NOT_FOUND") }
    requireSameHospital(principal, appointment.hospitalId)
    return ApiResponse.ok(AppointmentView.from(appointment))
  }

  /** 01.01: keyset-paginated list (00.02 style guide cursor mode). Opaque cursor = base64url "<scheduledStart>|<id>". */
  @GetMapping
  @RequiresPermission("scheduling:read:appointment")
  @Transactional(readOnly = true)
  fun listAppointments(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "doctorId", required = false) doctorId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "limit", defaultValue = "50") limit: Int,
    @RequestParam(name = "cursor", required = false) cursor: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AppointmentView>> {
    val hid = principal.hospitalId
    val effectiveLimit = if (limit in 1..500) limit else 50
    val pageRequest = PageRequest.of(0, effectiveLimit + 1)
    val decoded = decodeCursor(cursor)
    val rows =
      if (decoded != null) {
        appointmentRepository.pageAfter(hid, patientId, doctorId, status, decoded.first, decoded.second, pageRequest)
      } else {
        appointmentRepository.pageFirst(hid, patientId, doctorId, status, pageRequest)
      }
    val hasNext = rows.size > effectiveLimit
    val page = if (hasNext) rows.take(effectiveLimit) else rows
    val last = page.lastOrNull()
    val nextCursor =
      if (hasNext && last != null) {
        Base64.getUrlEncoder().withoutPadding().encodeToString("${last.scheduledStart}|${last.id}".toByteArray())
      } else {
        null
      }
    val meta =
      buildMap {
        put("limit", effectiveLimit)
        if (nextCursor != null) put("nextCursor", nextCursor)
      }
    return ApiResponse.ok(page.map { AppointmentView.from(it) }, meta)
  }

  private fun decodeCursor(cursor: String?): Pair<Instant, UUID>? {
    if (cursor == null) return null
    val raw = String(Base64.getUrlDecoder().decode(cursor))
    val separator = raw.indexOf('|')
    require(separator > 0) { "INVALID_CURSOR" }
    return Instant.parse(raw.substring(0, separator)) to UUID.fromString(raw.substring(separator + 1))
  }

  @PutMapping("/{id}")
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun updateAppointment(
    @PathVariable id: UUID,
    @RequestBody dto: AppointmentUpdateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> {
    val appointment = appointmentRepository.findById(id).orElseThrow { IllegalArgumentException("APPOINTMENT_NOT_FOUND") }
    requireSameHospital(principal, appointment.hospitalId)
    appointment.hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId ?: appointment.hospitalId)
    appointment.patientId = dto.patientId ?: appointment.patientId
    appointment.departmentId = dto.departmentId ?: appointment.departmentId
    appointment.specialtyId = dto.specialtyId ?: appointment.specialtyId
    appointment.doctorId = dto.doctorId ?: appointment.doctorId
    appointment.appointmentType = dto.appointmentType ?: appointment.appointmentType
    appointment.scheduledStart = dto.scheduledStart ?: appointment.scheduledStart
    appointment.scheduledEnd = dto.scheduledEnd ?: appointment.scheduledEnd
    appointment.durationMinutes = dto.durationMinutes ?: appointment.durationMinutes
    appointment.reason = dto.reason ?: appointment.reason
    appointment.notes = dto.notes ?: appointment.notes
    appointment.visitType = dto.visitType ?: appointment.visitType
    appointment.priority = dto.priority ?: appointment.priority
    appointment.telemedicine = dto.telemedicine ?: appointment.telemedicine
    appointment.roomId = dto.roomId ?: appointment.roomId
    appointment.externalRef = dto.externalRef ?: appointment.externalRef
    appointment.source = dto.source ?: appointment.source
    appointment.cancellationFee = dto.cancellationFee ?: appointment.cancellationFee
    if (!appointment.scheduledEnd.isAfter(appointment.scheduledStart)) {
      throw IllegalArgumentException("APPOINTMENT_END_BEFORE_START")
    }
    appointmentRepository.save(appointment)
    return ApiResponse.ok(AppointmentView.from(appointment))
  }

  @PatchMapping("/{id}/status")
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun updateAppointmentStatus(
    @PathVariable id: UUID,
    @RequestBody dto: AppointmentStatusDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> {
    val appointment = appointmentRepository.findById(id).orElseThrow { IllegalArgumentException("APPOINTMENT_NOT_FOUND") }
    requireSameHospital(principal, appointment.hospitalId)
    val status = dto.status ?: throw IllegalArgumentException("APPOINTMENT_STATUS_REQUIRED")
    val now = Instant.now()
    when (status) {
      "confirmed" -> {
        appointment.status = status
        appointment.confirmedAt = now
      }

      "arrived" -> {
        appointment.status = status
        appointment.checkedInAt = now
      }

      "cancelled" -> {
        appointment.status = status
        appointment.cancelledAt = now
        appointment.cancelledReason = dto.cancelledReason
        // 01.01: cancelling a slot booking releases the slot (bookedCount--) + publishes appointment.cancelled.
        schedulingService.releaseOnCancel(appointment)
      }

      "completed" -> {
        appointment.status = status
        appointment.completedAt = now
      }

      "no-show" -> {
        appointment.status = status
        appointment.noShowAt = now
        // 01.01: no-show tracked per slot (noShowCount) for the waitlist/overbook reporting.
        schedulingService.markNoShow(appointment)
      }

      else -> {
        throw IllegalArgumentException("APPOINTMENT_INVALID_STATUS")
      }
    }
    appointmentRepository.save(appointment)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "appointment.status_changed",
        occurredAt = now,
        source = "scheduling-service",
        entityType = "appointment",
        entityId = appointment.id,
        hospitalId = appointment.hospitalId,
        payload = mapOf("status" to appointment.status),
      ),
    )
    return ApiResponse.ok(AppointmentView.from(appointment))
  }

  /** 01.01: move an appointment to a different session slot (release old, consume new, same guards). */
  @PostMapping("/{id}/reschedule")
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun rescheduleAppointment(
    @PathVariable id: UUID,
    @RequestBody dto: RescheduleDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> {
    val appointment = appointmentRepository.findById(id).orElseThrow { IllegalArgumentException("APPOINTMENT_NOT_FOUND") }
    requireSameHospital(principal, appointment.hospitalId)
    val slotId = dto.slotId ?: throw IllegalArgumentException("SCHEDULE_SLOT_REQUIRED")
    val rescheduled = schedulingService.reschedule(principal, appointment, slotId)
    return ApiResponse.ok(AppointmentView.from(rescheduled))
  }
}
