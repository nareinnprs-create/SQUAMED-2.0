package com.squamed.core.surgery.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.surgery.api.ChecklistCreateDto
import com.squamed.core.surgery.api.ImplantCreateDto
import com.squamed.core.surgery.api.ImplantView
import com.squamed.core.surgery.api.InstrumentCreateDto
import com.squamed.core.surgery.api.InstrumentStatusDto
import com.squamed.core.surgery.api.InstrumentView
import com.squamed.core.surgery.api.IntraOpCreateDto
import com.squamed.core.surgery.api.IntraOpView
import com.squamed.core.surgery.api.OtAllocationCreateDto
import com.squamed.core.surgery.api.OtAllocationView
import com.squamed.core.surgery.api.OtRoomCreateDto
import com.squamed.core.surgery.api.OtRoomView
import com.squamed.core.surgery.api.PostOpCreateDto
import com.squamed.core.surgery.api.PostOpView
import com.squamed.core.surgery.api.PreOpChecklistView
import com.squamed.core.surgery.api.SurgeryScheduleCreateDto
import com.squamed.core.surgery.api.SurgeryScheduleView
import com.squamed.core.surgery.api.SurgeryStatusDto
import com.squamed.core.surgery.api.SurgicalTeamCreateDto
import com.squamed.core.surgery.api.SurgicalTeamView
import com.squamed.core.surgery.domain.ImplantRecord
import com.squamed.core.surgery.domain.InstrumentTracking
import com.squamed.core.surgery.domain.IntraOpRecord
import com.squamed.core.surgery.domain.OtAllocation
import com.squamed.core.surgery.domain.OtRoom
import com.squamed.core.surgery.domain.PostOpRecord
import com.squamed.core.surgery.domain.PreOpChecklist
import com.squamed.core.surgery.domain.SurgerySchedule
import com.squamed.core.surgery.domain.SurgicalTeam
import com.squamed.core.surgery.repository.ImplantRecordRepository
import com.squamed.core.surgery.repository.InstrumentTrackingRepository
import com.squamed.core.surgery.repository.IntraOpRecordRepository
import com.squamed.core.surgery.repository.OtAllocationRepository
import com.squamed.core.surgery.repository.OtRoomRepository
import com.squamed.core.surgery.repository.PostOpRecordRepository
import com.squamed.core.surgery.repository.PreOpChecklistRepository
import com.squamed.core.surgery.repository.SurgeryScheduleRepository
import com.squamed.core.surgery.repository.SurgicalTeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SurgeryService(
  private val scheduleRepository: SurgeryScheduleRepository,
  private val otRoomRepository: OtRoomRepository,
  private val allocationRepository: OtAllocationRepository,
  private val teamRepository: SurgicalTeamRepository,
  private val instrumentRepository: InstrumentTrackingRepository,
  private val implantRepository: ImplantRecordRepository,
  private val checklistRepository: PreOpChecklistRepository,
  private val intraOpRepository: IntraOpRecordRepository,
  private val postOpRepository: PostOpRecordRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createOtRoom(
    dto: OtRoomCreateDto,
    actorId: UUID,
  ): OtRoomView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("SURGERY_HOSPITAL_REQUIRED")
    val name = dto.name ?: throw IllegalArgumentException("SURGERY_OT_ROOM_NAME_REQUIRED")
    val code = dto.code ?: throw IllegalArgumentException("SURGERY_OT_ROOM_CODE_REQUIRED")
    val room = otRoomRepository.save(OtRoom(hospitalId = hospitalId, name = name, code = code).also { it.createdBy = actorId })
    return OtRoomView.from(room)
  }

  @Transactional(readOnly = true)
  fun listOtRooms(hospitalId: UUID?): List<OtRoomView> {
    val rooms = if (hospitalId != null) otRoomRepository.findByHospitalId(hospitalId) else otRoomRepository.findAll()
    return rooms.map { OtRoomView.from(it) }
  }

  @Transactional
  fun createSchedule(
    dto: SurgeryScheduleCreateDto,
    actorId: UUID,
  ): SurgeryScheduleView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("SURGERY_HOSPITAL_REQUIRED")
    val patientId = dto.patientId ?: throw IllegalArgumentException("SURGERY_PATIENT_REQUIRED")
    val procedureName = dto.procedureName ?: throw IllegalArgumentException("SURGERY_PROCEDURE_REQUIRED")
    val startAt = dto.scheduledStartAt ?: throw IllegalArgumentException("SURGERY_START_REQUIRED")
    val schedule =
      scheduleRepository.save(
        SurgerySchedule(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionId = dto.admissionId,
          bookingNumber = dto.bookingNumber,
          procedureName = procedureName,
          procedureCode = dto.procedureCode,
          specialty = dto.specialty,
          surgeonId = dto.surgeonId,
          scheduledStartAt = startAt,
          scheduledEndAt = dto.scheduledEndAt,
          notes = dto.notes,
        ).also { it.createdBy = actorId },
      )
    publishSurgeryEvent("surgery.scheduled", schedule, actorId)
    return SurgeryScheduleView.from(schedule)
  }

  @Transactional(readOnly = true)
  fun listSchedules(
    status: String?,
    patientId: UUID?,
  ): List<SurgeryScheduleView> {
    val rows =
      when {
        status != null -> scheduleRepository.findByStatus(status)
        patientId != null -> scheduleRepository.findByPatientId(patientId)
        else -> scheduleRepository.findAll()
      }
    return rows.sortedBy { it.scheduledStartAt }.map { SurgeryScheduleView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getSchedule(id: UUID): SurgeryScheduleView = SurgeryScheduleView.from(findSchedule(id))

  @Transactional
  fun updateStatus(
    id: UUID,
    dto: SurgeryStatusDto,
    actorId: UUID,
  ): SurgeryScheduleView {
    val schedule = findSchedule(id)
    val target = dto.status ?: throw IllegalArgumentException("SURGERY_STATUS_REQUIRED")
    if (target !in SCHEDULE_STATUSES) throw IllegalArgumentException("SURGERY_INVALID_STATUS")
    val now = Instant.now()
    when (target) {
      "started" -> {
        if (schedule.status !in setOf("scheduled", "confirmed", "checklist_done")) {
          throw IllegalArgumentException("SURGERY_CANNOT_START")
        }
        schedule.actualStartAt = now
      }

      "completed" -> {
        if (schedule.status != "started") throw IllegalArgumentException("SURGERY_CANNOT_COMPLETE")
        schedule.actualEndAt = now
        val allocation = allocationRepository.findBySurgeryScheduleId(id)
        if (allocation != null) {
          allocation.status = "completed"
          allocation.updatedBy = actorId
          allocationRepository.save(allocation)
        }
      }

      else -> {
        if (schedule.status == "completed" || schedule.status == "cancelled") throw IllegalArgumentException("SURGERY_STATUS_LOCKED")
      }
    }
    schedule.status = target
    schedule.updatedBy = actorId
    scheduleRepository.save(schedule)
    publishSurgeryEvent("surgery.${target.snakeToKebab()}", schedule, actorId)
    return SurgeryScheduleView.from(schedule)
  }

  @Transactional
  fun allocateOt(
    id: UUID,
    dto: OtAllocationCreateDto,
    actorId: UUID,
  ): OtAllocationView {
    val schedule = findSchedule(id)
    val otRoomId = dto.otRoomId ?: throw IllegalArgumentException("SURGERY_OT_ROOM_REQUIRED")
    otRoomRepository.findById(otRoomId).orElseThrow { IllegalArgumentException("SURGERY_OT_ROOM_NOT_FOUND") }
    val start = dto.startTime ?: throw IllegalArgumentException("SURGERY_ALLOCATION_START_REQUIRED")
    val end = dto.endTime
    val conflict =
      end == null || allocationRepository.existsByOtRoomIdAndStartTimeLessThanAndEndTimeGreaterThan(otRoomId, end, start)
    if (conflict) throw IllegalArgumentException("SURGERY_OT_CONFLICT")
    val existing = allocationRepository.findBySurgeryScheduleId(id)
    if (existing != null) {
      existing.otRoomId = otRoomId
      existing.startTime = start
      existing.endTime = end
      existing.updatedBy = actorId
      return OtAllocationView.from(allocationRepository.save(existing))
    }
    val allocation =
      allocationRepository.save(
        OtAllocation(
          hospitalId = schedule.hospitalId,
          otRoomId = otRoomId,
          surgeryScheduleId = id,
          startTime = start,
          endTime = end,
        ).also { it.createdBy = actorId },
      )
    return OtAllocationView.from(allocation)
  }

  @Transactional
  fun addTeamMember(
    id: UUID,
    dto: SurgicalTeamCreateDto,
    actorId: UUID,
  ): SurgicalTeamView {
    val schedule = findSchedule(id)
    val staffName = dto.staffName ?: throw IllegalArgumentException("SURGERY_TEAM_NAME_REQUIRED")
    val role = dto.role ?: throw IllegalArgumentException("SURGERY_TEAM_ROLE_REQUIRED")
    if (role !in TEAM_ROLES) throw IllegalArgumentException("SURGERY_TEAM_INVALID_ROLE")
    val member =
      teamRepository.save(
        SurgicalTeam(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = id,
          staffName = staffName,
          role = role,
        ).also { it.createdBy = actorId },
      )
    return SurgicalTeamView.from(member)
  }

  @Transactional(readOnly = true)
  fun listTeam(id: UUID): List<SurgicalTeamView> {
    findSchedule(id)
    return teamRepository.findBySurgeryScheduleId(id).map { SurgicalTeamView.from(it) }
  }

  @Transactional
  fun addInstrument(
    id: UUID,
    dto: InstrumentCreateDto,
    actorId: UUID,
  ): InstrumentView {
    val schedule = findSchedule(id)
    val name = dto.instrumentName ?: throw IllegalArgumentException("SURGERY_INSTRUMENT_NAME_REQUIRED")
    return InstrumentView.from(
      instrumentRepository.save(
        InstrumentTracking(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = id,
          instrumentName = name,
          setName = dto.setName,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional
  fun updateInstrumentStatus(
    scheduleId: UUID,
    instrumentId: UUID,
    dto: InstrumentStatusDto,
    actorId: UUID,
  ): InstrumentView {
    findSchedule(scheduleId)
    val instrument = findInstrument(instrumentId)
    val status = dto.status ?: throw IllegalArgumentException("SURGERY_INSTRUMENT_STATUS_REQUIRED")
    if (status !in INSTRUMENT_STATUSES) throw IllegalArgumentException("SURGERY_INSTRUMENT_INVALID_STATUS")
    instrument.status = status
    instrument.countedBy = if (status == "returned") actorId else null
    instrument.updatedBy = actorId
    return InstrumentView.from(instrumentRepository.save(instrument))
  }

  @Transactional(readOnly = true)
  fun listInstruments(scheduleId: UUID): List<InstrumentView> {
    findSchedule(scheduleId)
    return instrumentRepository.findBySurgeryScheduleId(scheduleId).map { InstrumentView.from(it) }
  }

  @Transactional
  fun addImplant(
    id: UUID,
    dto: ImplantCreateDto,
    actorId: UUID,
  ): ImplantView {
    val schedule = findSchedule(id)
    val name = dto.implantName ?: throw IllegalArgumentException("SURGERY_IMPLANT_NAME_REQUIRED")
    val implant =
      implantRepository.save(
        ImplantRecord(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = id,
          implantName = name,
          catalogNumber = dto.catalogNumber,
          lotNumber = dto.lotNumber,
          expiryDate = dto.expiryDate,
          quantity = dto.quantity ?: 1,
          implantedBy = actorId,
          notes = dto.notes,
        ).also { it.createdBy = actorId },
      )
    return ImplantView.from(implant)
  }

  @Transactional(readOnly = true)
  fun listImplants(scheduleId: UUID): List<ImplantView> {
    findSchedule(scheduleId)
    return implantRepository.findBySurgeryScheduleId(scheduleId).map { ImplantView.from(it) }
  }

  @Transactional
  fun addChecklistItem(
    id: UUID,
    dto: ChecklistCreateDto,
    actorId: UUID,
  ): PreOpChecklistView {
    val schedule = findSchedule(id)
    val item = dto.checklistItem ?: throw IllegalArgumentException("SURGERY_CHECKLIST_ITEM_REQUIRED")
    return PreOpChecklistView.from(
      checklistRepository.save(
        PreOpChecklist(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = id,
          checklistItem = item,
          category = dto.category,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional
  fun checkChecklistItem(
    scheduleId: UUID,
    itemId: UUID,
    actorId: UUID,
  ): PreOpChecklistView {
    findSchedule(scheduleId)
    val item =
      checklistRepository
        .findById(itemId)
        .orElseThrow { IllegalArgumentException("SURGERY_CHECKLIST_ITEM_NOT_FOUND") }
    item.checked = true
    item.checkedBy = actorId
    item.checkedAt = Instant.now()
    item.updatedBy = actorId
    return PreOpChecklistView.from(checklistRepository.save(item))
  }

  @Transactional(readOnly = true)
  fun listChecklist(scheduleId: UUID): List<PreOpChecklistView> {
    findSchedule(scheduleId)
    return checklistRepository.findBySurgeryScheduleId(scheduleId).map { PreOpChecklistView.from(it) }
  }

  @Transactional
  fun recordIntraOp(
    id: UUID,
    dto: IntraOpCreateDto,
    actorId: UUID,
  ): IntraOpView {
    val schedule = findSchedule(id)
    val eventName = dto.eventName ?: throw IllegalArgumentException("SURGERY_INTRAOP_EVENT_REQUIRED")
    return IntraOpView.from(
      intraOpRepository.save(
        IntraOpRecord(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = id,
          eventName = eventName,
          notes = dto.notes,
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional(readOnly = true)
  fun listIntraOp(scheduleId: UUID): List<IntraOpView> {
    findSchedule(scheduleId)
    return intraOpRepository.findBySurgeryScheduleId(scheduleId).map { IntraOpView.from(it) }
  }

  @Transactional
  fun recordPostOp(
    id: UUID,
    dto: PostOpCreateDto,
    actorId: UUID,
  ): PostOpView {
    val schedule = findSchedule(id)
    val destination = dto.destination ?: throw IllegalArgumentException("SURGERY_POSTOP_DESTINATION_REQUIRED")
    if (destination !in POSTOP_DESTINATIONS) throw IllegalArgumentException("SURGERY_POSTOP_INVALID_DESTINATION")
    return PostOpView.from(
      postOpRepository.save(
        PostOpRecord(
          hospitalId = schedule.hospitalId,
          surgeryScheduleId = id,
          patientCondition = dto.patientCondition,
          destination = destination,
          painScore = dto.painScore,
          drainOutputMl = dto.drainOutputMl,
          complications = dto.complications,
          recordedBy = actorId,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional(readOnly = true)
  fun getPostOp(scheduleId: UUID): PostOpView {
    findSchedule(scheduleId)
    val record =
      postOpRepository.findFirstBySurgeryScheduleIdOrderByRecordedAtDesc(scheduleId)
        ?: throw IllegalArgumentException("SURGERY_POSTOP_NOT_FOUND")
    return PostOpView.from(record)
  }

  private fun findSchedule(id: UUID): SurgerySchedule = scheduleRepository.findById(id).orElseThrow { IllegalArgumentException("SURGERY_SCHEDULE_NOT_FOUND") }

  private fun findInstrument(id: UUID): InstrumentTracking =
    instrumentRepository.findById(id).orElseThrow { IllegalArgumentException("SURGERY_INSTRUMENT_NOT_FOUND") }

  private fun publishSurgeryEvent(
    eventType: String,
    schedule: SurgerySchedule,
    actorId: UUID,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "surgery-service",
        entityType = "surgery_schedule",
        entityId = schedule.id,
        hospitalId = schedule.hospitalId,
        payload = mapOf("patientId" to schedule.patientId, "status" to schedule.status, "actorId" to actorId),
      ),
    )
  }

  private fun String.snakeToKebab(): String = this.replace("_", "-")

  companion object {
    val SCHEDULE_STATUSES = setOf("scheduled", "confirmed", "checklist_done", "started", "completed", "cancelled")
    val TEAM_ROLES = setOf("surgeon", "assistant", "anaesthetist", "nurse", "scout", "technician", "perfusionist")
    val INSTRUMENT_STATUSES = setOf("booked", "checked_in", "sterilized", "in_use", "returned", "decontaminated")
    val POSTOP_DESTINATIONS = setOf("ward", "icu", "observation", "recovery", "stepdown")
  }
}
