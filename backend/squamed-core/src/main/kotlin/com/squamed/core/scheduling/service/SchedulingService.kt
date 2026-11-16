package com.squamed.core.scheduling.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
import com.squamed.core.identity.security.SquamedPermissionEvaluator
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.scheduling.api.ScheduleTemplateCreateDto
import com.squamed.core.scheduling.api.ScheduleTemplateUpdateDto
import com.squamed.core.scheduling.api.WaitlistCreateDto
import com.squamed.core.scheduling.api.WaitlistPromoteDto
import com.squamed.core.scheduling.domain.Appointment
import com.squamed.core.scheduling.domain.ScheduleTemplate
import com.squamed.core.scheduling.domain.SessionSlot
import com.squamed.core.scheduling.domain.WaitlistEntry
import com.squamed.core.scheduling.repository.AppointmentRepository
import com.squamed.core.scheduling.repository.ScheduleTemplateRepository
import com.squamed.core.scheduling.repository.SessionSlotRepository
import com.squamed.core.scheduling.repository.WaitlistRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 01.01 scheduling engine: session-based templates → lazily materialized session slots →
 * booking (slot capacity + overbook factor + no-show policy) → waitlist promote.
 * Slot times are treated as UTC instants (house style: appointment uses Instant end-to-end).
 */
@Service
class SchedulingService(
  private val templateRepository: ScheduleTemplateRepository,
  private val slotRepository: SessionSlotRepository,
  private val waitlistRepository: WaitlistRepository,
  private val appointmentRepository: AppointmentRepository,
  private val configDocumentService: ConfigDocumentService,
  private val permissionEvaluator: SquamedPermissionEvaluator,
  private val eventPublisher: DomainEventPublisher,
) {
  companion object {
    const val OVERRIDE_PERMISSION = "scheduling:override:booking"
  }

  data class SchedulingPolicy(
    val overbookFactor: Double,
    val noShowWindowDays: Long,
    val noShowLimit: Int,
  )

  /** Hospital scheduling config doc; defaults keep the engine usable before the first publish. */
  fun policy(hospitalId: UUID): SchedulingPolicy {
    val content: JsonNode? = configDocumentService.latest(hospitalId, "scheduling")?.content
    return SchedulingPolicy(
      overbookFactor = content?.path("overbookFactor")?.takeIf { !it.isMissingNode && it.isNumber }?.asDouble() ?: 1.0,
      noShowWindowDays = content?.path("noShowWindowDays")?.takeIf { !it.isMissingNode && it.isNumber }?.asLong() ?: 30,
      noShowLimit = content?.path("noShowLimit")?.takeIf { !it.isMissingNode && it.isNumber }?.asInt() ?: 3,
    )
  }

  @Transactional
  fun createTemplate(
    principal: SquamedPrincipal,
    dto: ScheduleTemplateCreateDto,
  ): ScheduleTemplate {
    val hid = requireHid(principal, dto.hospitalId)
    val doctorId = dto.doctorId ?: throw IllegalArgumentException("SCHEDULE_DOCTOR_REQUIRED")
    val weekday = dto.weekday ?: throw IllegalArgumentException("SCHEDULE_WEEKDAY_REQUIRED")
    val start = dto.startTime ?: throw IllegalArgumentException("SCHEDULE_START_REQUIRED")
    val end = dto.endTime ?: throw IllegalArgumentException("SCHEDULE_END_REQUIRED")
    require(weekday in 1..7) { "SCHEDULE_WEEKDAY_INVALID" }
    require(end.isAfter(start)) { "SCHEDULE_END_BEFORE_START" }
    val sessionType = dto.sessionType ?: "in_person"
    require(sessionType in setOf("in_person", "teleconsult")) { "SCHEDULE_SESSION_TYPE_INVALID" }
    require((dto.slotMinutes ?: 15) > 0) { "SCHEDULE_SLOT_MINUTES_INVALID" }
    require((dto.capacity ?: 1) > 0) { "SCHEDULE_CAPACITY_INVALID" }
    return templateRepository.save(
      ScheduleTemplate(
        hospitalId = hid,
        doctorId = doctorId,
        departmentId = dto.departmentId,
        sessionType = sessionType,
        weekday = weekday,
        startTime = start,
        endTime = end,
        slotMinutes = dto.slotMinutes ?: 15,
        capacity = dto.capacity ?: 1,
        roomId = dto.roomId,
        equipmentIds = dto.equipmentIds?.let { "[${it.joinToString(",") { id -> "\"$id\"" }}]" },
        validFrom = dto.validFrom,
        validTo = dto.validTo,
      ),
    )
  }

  @Transactional
  fun updateTemplate(
    principal: SquamedPrincipal,
    templateId: UUID,
    dto: ScheduleTemplateUpdateDto,
  ): ScheduleTemplate {
    val template =
      templateRepository.findById(templateId).orElseThrow { IllegalArgumentException("SCHEDULE_TEMPLATE_NOT_FOUND") }
    requireSameHospital(principal, template.hospitalId)
    dto.slotMinutes?.let {
      require(it > 0) { "SCHEDULE_SLOT_MINUTES_INVALID" }
      template.slotMinutes = it
    }
    dto.capacity?.let {
      require(it > 0) { "SCHEDULE_CAPACITY_INVALID" }
      template.capacity = it
    }
    dto.roomId?.let { template.roomId = it }
    dto.equipmentIds?.let { template.equipmentIds = "[${it.joinToString(",") { id -> "\"$id\"" }}]" }
    dto.validFrom?.let { template.validFrom = it }
    dto.validTo?.let { template.validTo = it }
    dto.active?.let { template.active = it }
    if (!template.endTime.isAfter(template.startTime)) throw IllegalArgumentException("SCHEDULE_END_BEFORE_START")
    return templateRepository.save(template)
  }

  @Transactional(readOnly = true)
  fun templates(
    principal: SquamedPrincipal,
    doctorId: UUID?,
  ): List<ScheduleTemplate> = templateRepository.findTemplates(principal.hospitalId, doctorId)

  /**
   * Idempotent slot generation for the fromDate..toDate window (clamped to 14 days): expands every active
   * template whose weekday + validity window covers the date, upserting slots on the unique
   * (hospital, doctor, date, start) key — repeated calls return the same rows.
   */
  @Transactional
  fun generateSlots(
    principal: SquamedPrincipal,
    doctorId: UUID,
    fromDate: LocalDate,
    toDate: LocalDate,
  ): List<SessionSlot> {
    val hid = principal.hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED")
    require(!toDate.isBefore(fromDate)) { "SCHEDULE_DATE_WINDOW_INVALID" }
    val effectiveTo =
      if (Duration.between(fromDate.atStartOfDay(), toDate.atStartOfDay()).toDays() > 13) {
        fromDate.plusDays(13)
      } else {
        toDate
      }
    val templates =
      templateRepository.findTemplates(hid, doctorId).filter { it.active }
    var date = fromDate
    while (!date.isAfter(effectiveTo)) {
      val weekday = date.dayOfWeek.value
      templates
        .filter { it.weekday == weekday && inValidityWindow(it, date) }
        .forEach { template ->
          var time = template.startTime
          while (time.isBefore(template.endTime)) {
            val existing = slotRepository.findByHospitalIdAndDoctorIdAndSlotDateAndStartTime(hid, doctorId, date, time)
            if (existing == null) {
              slotRepository.save(
                SessionSlot(
                  hospitalId = hid,
                  templateId = template.id ?: throw IllegalStateException("SCHEDULE_TEMPLATE_ID_MISSING"),
                  doctorId = doctorId,
                  departmentId = template.departmentId,
                  sessionType = template.sessionType,
                  roomId = template.roomId,
                  slotDate = date,
                  startTime = time,
                  endTime =
                    if (template.slotMinutes >= Duration.between(template.startTime, template.endTime).toMinutes()) {
                      template.endTime
                    } else {
                      time.plusMinutes(template.slotMinutes.toLong())
                    },
                  capacity = template.capacity,
                ),
              )
            }
            time = time.plusMinutes(template.slotMinutes.toLong())
          }
        }
      date = date.plusDays(1)
    }
    return slotRepository.findByHospitalIdAndDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(hid, doctorId, fromDate, effectiveTo)
  }

  private fun inValidityWindow(
    template: ScheduleTemplate,
    date: LocalDate,
  ): Boolean =
    (template.validFrom == null || !date.isBefore(template.validFrom)) &&
      (template.validTo == null || !date.isAfter(template.validTo))

  /** Slot start/end as UTC instants (house style: appointments are Instant-based). */
  fun slotStart(slot: SessionSlot): Instant = LocalDateTime.of(slot.slotDate, slot.startTime).toInstant(ZoneOffset.UTC)

  fun slotEnd(slot: SessionSlot): Instant = LocalDateTime.of(slot.slotDate, slot.endTime).toInstant(ZoneOffset.UTC)

  /** Booking request: the appointment fields that ride a slot booking (01.01). */
  data class BookingRequest(
    val patientId: UUID,
    val appointmentType: String,
    val reason: String? = null,
    val notes: String? = null,
    val visitType: String? = null,
    val priority: String? = null,
    val externalRef: String? = null,
    val source: String? = null,
    val cancellationFee: java.math.BigDecimal? = null,
  )

  /**
   * Slot booking (01.01): validates slot tenancy/openness/past, no-show policy (override requires
   * scheduling:override:booking), overbook factor ceiling, then persists the appointment and
   * consumes the slot in the same transaction.
   */
  @Transactional
  fun book(
    principal: SquamedPrincipal,
    slotId: UUID,
    request: BookingRequest,
    overrideNoShowLimit: Boolean,
  ): Appointment {
    val slot =
      slotRepository.findById(slotId).orElseThrow { IllegalArgumentException("SCHEDULE_SLOT_NOT_FOUND") }
    requireSameHospital(principal, slot.hospitalId)
    if (slot.status != "open") throw IllegalArgumentException("SCHEDULE_SLOT_CLOSED")
    if (!slotStart(slot).isAfter(Instant.now())) throw IllegalArgumentException("SCHEDULE_SLOT_IN_PAST")
    val hid = slot.hospitalId ?: throw IllegalStateException("SCHEDULE_SLOT_HOSPITAL_MISSING")
    val policy = policy(hid)
    if (overrideNoShowLimit && !permissionEvaluator.check(principal, OVERRIDE_PERMISSION)) {
      throw IllegalArgumentException("SCHEDULE_OVERRIDE_PERMISSION_REQUIRED")
    }
    val noShowCount =
      appointmentRepository.countByPatientIdAndStatusAndNoShowAtAfter(
        request.patientId,
        "no-show",
        Instant.now().minus(Duration.ofDays(policy.noShowWindowDays)),
      )
    if (noShowCount >= policy.noShowLimit && !overrideNoShowLimit) {
      throw IllegalArgumentException("SCHEDULE_NOSHOW_LIMIT")
    }
    val max = ceilFactor(slot.capacity, policy.overbookFactor)
    if (slot.bookedCount >= max) throw IllegalArgumentException("SCHEDULE_SLOT_FULL")
    val saved =
      appointmentRepository.save(
        Appointment(
          hospitalId = hid,
          patientId = request.patientId,
          departmentId = slot.departmentId,
          doctorId = slot.doctorId,
          appointmentType = request.appointmentType,
          scheduledStart = slotStart(slot),
          scheduledEnd = slotEnd(slot),
          durationMinutes = Duration.between(slotStart(slot), slotEnd(slot)).toMinutes().toInt(),
          reason = request.reason,
          notes = request.notes,
          visitType = request.visitType ?: "outpatient",
          priority = request.priority ?: "routine",
          telemedicine = slot.sessionType == "teleconsult",
          roomId = slot.roomId,
          externalRef = request.externalRef,
          source = request.source ?: "clinic",
          cancellationFee = request.cancellationFee,
          slotId = slot.id,
        ),
      )
    slot.bookedCount += 1
    slotRepository.save(slot)
    publish(
      "appointment.created",
      saved,
      hid,
      mapOf(
        "appointmentId" to saved.id,
        "patientId" to saved.patientId,
        "doctorId" to saved.doctorId,
        "slotId" to slot.id,
        "sessionType" to slot.sessionType,
      ),
    )
    return saved
  }

  @Transactional
  fun releaseOnCancel(appointment: Appointment) {
    val slotId = appointment.slotId ?: return
    val slot = slotRepository.findById(slotId).orElse(null) ?: return
    if (slot.bookedCount > 0) slot.bookedCount -= 1
    slotRepository.save(slot)
    appointment.slotId = null
    publish(
      "appointment.cancelled",
      appointment,
      appointment.hospitalId,
      mapOf("appointmentId" to appointment.id, "slotId" to slotId),
    )
  }

  @Transactional
  fun markNoShow(appointment: Appointment) {
    val slotId = appointment.slotId ?: return
    val slot = slotRepository.findById(slotId).orElse(null) ?: return
    slot.noShowCount += 1
    slotRepository.save(slot)
  }

  /** Reschedule = release the held slot, consume the new one (same capacity/policy checks). */
  @Transactional
  fun reschedule(
    principal: SquamedPrincipal,
    appointment: Appointment,
    newSlotId: UUID,
  ): Appointment {
    if (appointment.status in setOf("cancelled", "no-show")) {
      throw IllegalArgumentException("APPOINTMENT_NOT_RESCHEDULABLE")
    }
    val oldSlotId = appointment.slotId
    val appointmentId = appointment.id
    // Re-book through the same guard path: build the booking base from the existing appointment.
    if (oldSlotId != null) {
      releaseOnCancelWithoutEvent(appointment)
    }
    appointment.slotId = null
    val slot =
      slotRepository.findById(newSlotId).orElseThrow { IllegalArgumentException("SCHEDULE_SLOT_NOT_FOUND") }
    requireSameHospital(principal, slot.hospitalId)
    if (slot.status != "open") throw IllegalArgumentException("SCHEDULE_SLOT_CLOSED")
    if (!slotStart(slot).isAfter(Instant.now())) throw IllegalArgumentException("SCHEDULE_SLOT_IN_PAST")
    val hid = slot.hospitalId ?: throw IllegalStateException("SCHEDULE_SLOT_HOSPITAL_MISSING")
    val policy = policy(hid)
    val max = ceilFactor(slot.capacity, policy.overbookFactor)
    if (slot.bookedCount >= max) throw IllegalArgumentException("SCHEDULE_SLOT_FULL")
    appointment.scheduledStart = slotStart(slot)
    appointment.scheduledEnd = slotEnd(slot)
    appointment.durationMinutes = Duration.between(slotStart(slot), slotEnd(slot)).toMinutes().toInt()
    appointment.doctorId = slot.doctorId
    appointment.roomId = slot.roomId
    appointment.telemedicine = slot.sessionType == "teleconsult"
    appointment.slotId = slot.id
    slot.bookedCount += 1
    slotRepository.save(slot)
    val saved = appointmentRepository.save(appointment)
    publish(
      "appointment.rescheduled",
      saved,
      hid,
      mapOf("appointmentId" to appointmentId, "fromSlotId" to oldSlotId, "toSlotId" to slot.id),
    )
    return saved
  }

  /** Release without publishing the appointment.cancelled event (reschedule keeps the booking alive). */
  private fun releaseOnCancelWithoutEvent(appointment: Appointment) {
    val slotId = appointment.slotId ?: return
    val slot = slotRepository.findById(slotId).orElse(null) ?: return
    if (slot.bookedCount > 0) slot.bookedCount -= 1
    slotRepository.save(slot)
  }

  @Transactional
  fun addToWaitlist(
    principal: SquamedPrincipal,
    dto: WaitlistCreateDto,
  ): WaitlistEntry {
    val hid = requireHid(principal, dto.hospitalId)
    val patientId = dto.patientId ?: throw IllegalArgumentException("WAITLIST_PATIENT_REQUIRED")
    val priority = dto.priority ?: "routine"
    require(priority in setOf("routine", "urgent", "vip")) { "WAITLIST_PRIORITY_INVALID" }
    return waitlistRepository.save(
      WaitlistEntry(
        hospitalId = hid,
        patientId = patientId,
        doctorId = dto.doctorId,
        departmentId = dto.departmentId,
        priority = priority,
        desiredDate = dto.desiredDate,
        notes = dto.notes,
      ),
    )
  }

  @Transactional(readOnly = true)
  fun waitlist(
    principal: SquamedPrincipal,
    doctorId: UUID?,
  ): List<WaitlistEntry> = waitlistRepository.findWaiting(principal.hospitalId, doctorId)

  @Transactional
  fun cancelWaitlistEntry(
    principal: SquamedPrincipal,
    entryId: UUID,
  ): WaitlistEntry {
    val entry =
      waitlistRepository.findByHospitalIdAndId(requireHid(principal, null), entryId)
        ?: throw IllegalArgumentException("WAITLIST_ENTRY_NOT_FOUND")
    entry.status = "cancelled"
    return waitlistRepository.save(entry)
  }

  /** Promote a waiting entry into a concrete slot (same booking guards as a direct booking). */
  @Transactional
  fun promoteFromWaitlist(
    principal: SquamedPrincipal,
    entryId: UUID,
    dto: WaitlistPromoteDto,
  ): Appointment {
    val entry =
      waitlistRepository.findByHospitalIdAndId(requireHid(principal, null), entryId)
        ?: throw IllegalArgumentException("WAITLIST_ENTRY_NOT_FOUND")
    if (entry.status != "waiting") throw IllegalArgumentException("WAITLIST_ENTRY_NOT_WAITING")
    val slotId = dto.slotId ?: throw IllegalArgumentException("SCHEDULE_SLOT_REQUIRED")
    val appointment =
      book(
        principal,
        slotId,
        BookingRequest(patientId = entry.patientId, appointmentType = "OPD"),
        overrideNoShowLimit = false,
      )
    entry.status = "booked"
    entry.bookedAppointmentId = appointment.id
    waitlistRepository.save(entry)
    return appointment
  }

  private fun requireHid(
    principal: SquamedPrincipal,
    dtoHospitalId: UUID?,
  ): UUID = principal.hospitalId ?: dtoHospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED")

  private fun ceilFactor(
    capacity: Int,
    factor: Double,
  ): Int = (capacity * factor).let { Math.ceil(it).toInt() }

  private fun publish(
    eventType: String,
    appointment: Appointment,
    hospitalId: UUID?,
    payload: Map<String, Any?>,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "scheduling-service",
        entityType = "appointment",
        entityId = appointment.id ?: throw IllegalStateException("APPOINTMENT_ID_MISSING"),
        hospitalId = hospitalId,
        payload = payload,
      ),
    )
  }
}
