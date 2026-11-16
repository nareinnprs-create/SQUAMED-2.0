package com.squamed.core.scheduling.api

import com.squamed.core.scheduling.domain.Appointment
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AppointmentCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val departmentId: UUID? = null,
  val specialtyId: UUID? = null,
  val doctorId: UUID? = null,
  val appointmentType: String? = null,
  val scheduledStart: Instant? = null,
  val scheduledEnd: Instant? = null,
  val durationMinutes: Int? = null,
  val reason: String? = null,
  val notes: String? = null,
  val visitType: String? = null,
  val priority: String? = null,
  val telemedicine: Boolean? = null,
  val roomId: UUID? = null,
  val externalRef: String? = null,
  val source: String? = null,
  val cancellationFee: BigDecimal? = null,
  // 01.01: slot-based booking; null = legacy direct-time booking (chunk-0 compat)
  val slotId: UUID? = null,
  // 01.01: book past the no-show policy limit; requires scheduling:override:booking
  val overrideNoShowLimit: Boolean? = null,
)

data class AppointmentUpdateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val departmentId: UUID? = null,
  val specialtyId: UUID? = null,
  val doctorId: UUID? = null,
  val appointmentType: String? = null,
  val scheduledStart: Instant? = null,
  val scheduledEnd: Instant? = null,
  val durationMinutes: Int? = null,
  val reason: String? = null,
  val notes: String? = null,
  val visitType: String? = null,
  val priority: String? = null,
  val telemedicine: Boolean? = null,
  val roomId: UUID? = null,
  val externalRef: String? = null,
  val source: String? = null,
  val cancellationFee: BigDecimal? = null,
)

data class AppointmentView(
  val id: UUID?,
  val patientId: UUID?,
  val doctorId: UUID?,
  val scheduledStart: Instant?,
  val scheduledEnd: Instant?,
  val status: String?,
  val type: String?,
  val reason: String?,
  val priority: String?,
  val visitType: String?,
  val telemedicine: Boolean?,
  val roomId: UUID?,
  val slotId: UUID?,
  val source: String?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: Appointment) =
      AppointmentView(
        id = entity.id,
        patientId = entity.patientId,
        doctorId = entity.doctorId,
        scheduledStart = entity.scheduledStart,
        scheduledEnd = entity.scheduledEnd,
        status = entity.status,
        type = entity.appointmentType,
        reason = entity.reason,
        priority = entity.priority,
        visitType = entity.visitType,
        telemedicine = entity.telemedicine,
        roomId = entity.roomId,
        slotId = entity.slotId,
        source = entity.source,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class AppointmentStatusDto(
  val status: String?,
  val cancelledReason: String? = null,
)
