package com.squamed.core.scheduling.api

import com.squamed.core.scheduling.domain.ScheduleTemplate
import com.squamed.core.scheduling.domain.SessionSlot
import com.squamed.core.scheduling.domain.WaitlistEntry
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ScheduleTemplateCreateDto(
  val hospitalId: UUID? = null,
  val doctorId: UUID? = null,
  val departmentId: UUID? = null,
  val sessionType: String? = null,
  val weekday: Int? = null,
  val startTime: LocalTime? = null,
  val endTime: LocalTime? = null,
  val slotMinutes: Int? = null,
  val capacity: Int? = null,
  val roomId: UUID? = null,
  val equipmentIds: List<UUID>? = null,
  val validFrom: LocalDate? = null,
  val validTo: LocalDate? = null,
)

data class ScheduleTemplateUpdateDto(
  val slotMinutes: Int? = null,
  val capacity: Int? = null,
  val roomId: UUID? = null,
  val equipmentIds: List<UUID>? = null,
  val validFrom: LocalDate? = null,
  val validTo: LocalDate? = null,
  val active: Boolean? = null,
)

data class ScheduleTemplateView(
  val id: UUID?,
  val hospitalId: UUID?,
  val doctorId: UUID,
  val departmentId: UUID?,
  val sessionType: String,
  val weekday: Int,
  val startTime: LocalTime,
  val endTime: LocalTime,
  val slotMinutes: Int,
  val capacity: Int,
  val roomId: UUID?,
  val equipmentIds: List<UUID>?,
  val validFrom: LocalDate?,
  val validTo: LocalDate?,
  val active: Boolean,
  val createdAt: java.time.Instant?,
) {
  companion object {
    fun from(entity: ScheduleTemplate): ScheduleTemplateView {
      val equipmentIds =
        entity.equipmentIds
          ?.removePrefix("[")
          ?.removeSuffix("]")
          ?.split(',')
          ?.map { it.trim().removeSurrounding("\"") }
          ?.filter { it.isNotBlank() }
          ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
      return ScheduleTemplateView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        doctorId = entity.doctorId,
        departmentId = entity.departmentId,
        sessionType = entity.sessionType,
        weekday = entity.weekday,
        startTime = entity.startTime,
        endTime = entity.endTime,
        slotMinutes = entity.slotMinutes,
        capacity = entity.capacity,
        roomId = entity.roomId,
        equipmentIds = equipmentIds,
        validFrom = entity.validFrom,
        validTo = entity.validTo,
        active = entity.active,
        createdAt = entity.createdAt,
      )
    }
  }
}

data class SlotView(
  val id: UUID?,
  val templateId: UUID?,
  val doctorId: UUID,
  val departmentId: UUID?,
  val sessionType: String,
  val roomId: UUID?,
  val slotDate: LocalDate,
  val startTime: LocalTime,
  val endTime: LocalTime,
  val capacity: Int,
  val bookedCount: Int,
  val noShowCount: Int,
  val status: String,
) {
  companion object {
    fun from(entity: SessionSlot) =
      SlotView(
        id = entity.id,
        templateId = entity.templateId,
        doctorId = entity.doctorId,
        departmentId = entity.departmentId,
        sessionType = entity.sessionType,
        roomId = entity.roomId,
        slotDate = entity.slotDate,
        startTime = entity.startTime,
        endTime = entity.endTime,
        capacity = entity.capacity,
        bookedCount = entity.bookedCount,
        noShowCount = entity.noShowCount,
        status = entity.status,
      )
  }
}

data class WaitlistCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val doctorId: UUID? = null,
  val departmentId: UUID? = null,
  val priority: String? = null,
  val desiredDate: LocalDate? = null,
  val notes: String? = null,
)

data class WaitlistView(
  val id: UUID?,
  val patientId: UUID,
  val doctorId: UUID?,
  val departmentId: UUID?,
  val priority: String,
  val desiredDate: LocalDate?,
  val status: String,
  val bookedAppointmentId: UUID?,
  val notes: String?,
  val createdAt: java.time.Instant?,
) {
  companion object {
    fun from(entity: WaitlistEntry) =
      WaitlistView(
        id = entity.id,
        patientId = entity.patientId,
        doctorId = entity.doctorId,
        departmentId = entity.departmentId,
        priority = entity.priority,
        desiredDate = entity.desiredDate,
        status = entity.status,
        bookedAppointmentId = entity.bookedAppointmentId,
        notes = entity.notes,
        createdAt = entity.createdAt,
      )
  }
}

data class WaitlistPromoteDto(
  val slotId: UUID? = null,
)

data class RescheduleDto(
  val slotId: UUID? = null,
)
