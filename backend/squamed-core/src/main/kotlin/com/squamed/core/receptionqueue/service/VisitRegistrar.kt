package com.squamed.core.receptionqueue.service

import com.squamed.core.receptionqueue.api.VisitCreateDto
import com.squamed.core.receptionqueue.api.VisitCreateResult
import com.squamed.core.receptionqueue.api.VisitDto
import com.squamed.core.receptionqueue.domain.VisitRegistration
import com.squamed.core.receptionqueue.repository.VisitRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Year
import java.util.UUID

@Service
class VisitRegistrar(
  private val visitRegistrationRepository: VisitRegistrationRepository,
  private val queueService: QueueService,
) {
  @Transactional
  fun createVisit(
    patientId: UUID,
    dto: VisitCreateDto,
    hospitalId: UUID,
  ): VisitCreateResult {
    val now = Instant.now()
    val visit =
      visitRegistrationRepository.save(
        VisitRegistration(
          hospitalId = hospitalId,
          patientId = patientId,
          appointmentId = dto.appointmentId,
          visitNumber = nextVisitNumber(),
          visitType = dto.visitType ?: "outpatient",
          departmentId = dto.departmentId,
          doctorId = dto.doctorId,
          specialtyId = dto.specialtyId,
          reason = dto.reason,
          priority = dto.priority ?: "routine",
          source = dto.source ?: "walk-in",
          registrationStartedAt = now,
          registeredAt = if (dto.registered == true) now else null,
        ),
      )
    val visitId = visit.id ?: throw IllegalStateException("VISIT_ID_MISSING")
    val queueEntry =
      if (dto.addToQueue == true) {
        queueService.addToQueue(
          queueType = dto.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"),
          patientId = patientId,
          patientName = null,
          visitId = visitId,
          priority = dto.priority,
          servicePointId = null,
          departmentId = dto.departmentId,
          hospitalId = hospitalId,
        )
      } else {
        null
      }
    return VisitCreateResult(visit = VisitDto.from(visit), queueEntry = queueEntry)
  }

  @Transactional(readOnly = true)
  fun getVisit(id: UUID): VisitDto = VisitDto.from(findVisit(id))

  @Transactional(readOnly = true)
  fun listVisits(
    status: String?,
    patientId: UUID?,
    from: Instant,
  ): List<VisitDto> {
    val visits =
      when {
        patientId != null -> visitRegistrationRepository.findByPatientId(patientId)
        status != null -> visitRegistrationRepository.findByStatus(status)
        else -> visitRegistrationRepository.findAll()
      }
    return visits
      .filter { (it.createdAt ?: Instant.EPOCH) >= from }
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { VisitDto.from(it) }
  }

  @Transactional
  fun checkIn(visitId: UUID): VisitDto {
    val visit = findVisit(visitId)
    visit.status = "checked-in"
    visitRegistrationRepository.save(visit)
    return VisitDto.from(visit)
  }

  @Transactional
  fun complete(visitId: UUID): VisitDto {
    val visit = findVisit(visitId)
    visit.status = "completed"
    visitRegistrationRepository.save(visit)
    return VisitDto.from(visit)
  }

  @Transactional
  fun cancelVisit(
    visitId: UUID,
    reason: String?,
  ): VisitDto {
    val visit = findVisit(visitId)
    visit.status = "cancelled"
    if (!reason.isNullOrBlank()) {
      visit.reason = if (visit.reason.isNullOrBlank()) reason else "${visit.reason} | CANCELLED: $reason"
    }
    visitRegistrationRepository.save(visit)
    return VisitDto.from(visit)
  }

  private fun nextVisitNumber(): String {
    val year = Year.now().value
    val latest = visitRegistrationRepository.findTopByOrderByVisitNumberDesc()
    val sequence =
      latest
        ?.visitNumber
        ?.split("-")
        ?.lastOrNull()
        ?.toIntOrNull() ?: 0
    return String.format("VN-%d-%04d", year, sequence + 1)
  }

  private fun findVisit(id: UUID): VisitRegistration = visitRegistrationRepository.findById(id).orElseThrow { IllegalArgumentException("VISIT_NOT_FOUND") }
}
