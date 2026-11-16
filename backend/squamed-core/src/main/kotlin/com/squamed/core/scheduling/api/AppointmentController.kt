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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController(
  private val appointmentRepository: AppointmentRepository,
  private val eventPublisher: DomainEventPublisher,
  private val tenantResolver: TenantResolver,
) {
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("scheduling:write:appointment")
  @Transactional
  fun createAppointment(
    @RequestBody dto: AppointmentCreateDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<AppointmentView> {
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

  @GetMapping
  @RequiresPermission("scheduling:read:appointment")
  @Transactional(readOnly = true)
  fun listAppointments(
    @RequestParam(name = "patientId", required = false) patientId: UUID?,
    @RequestParam(name = "doctorId", required = false) doctorId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
    @RequestParam(name = "from", required = false) from: String?,
    @RequestParam(name = "to", required = false) to: String?,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<AppointmentView>> {
    val hid = principal.hospitalId
    val appointments =
      when {
        from != null && to != null -> {
          appointmentRepository.findByScheduledStartBetween(hid, Instant.parse(from), Instant.parse(to))
        }

        patientId != null -> {
          appointmentRepository.findByPatientId(hid, patientId)
        }

        doctorId != null -> {
          appointmentRepository.findByDoctorId(hid, doctorId)
        }

        status != null -> {
          appointmentRepository.findByStatus(hid, status)
        }

        else -> {
          appointmentRepository.findAllByOrderByScheduledStartAsc(hid)
        }
      }
    return ApiResponse.ok(appointments.map { AppointmentView.from(it) })
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
      }

      "completed" -> {
        appointment.status = status
        appointment.completedAt = now
      }

      "no-show" -> {
        appointment.status = status
        appointment.noShowAt = now
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
}
