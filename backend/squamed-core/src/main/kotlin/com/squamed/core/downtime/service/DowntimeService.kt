package com.squamed.core.downtime.service

import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.downtime.api.DowntimeLogView
import com.squamed.core.downtime.api.DowntimeReentryDto
import com.squamed.core.downtime.api.DowntimeReentryResult
import com.squamed.core.downtime.domain.DowntimeLog
import com.squamed.core.downtime.repository.DowntimeLogRepository
import com.squamed.core.receptionqueue.api.VisitDto
import com.squamed.core.receptionqueue.service.QueueService
import com.squamed.core.receptionqueue.service.VisitRegistrar
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DowntimeService(
  private val downtimeLogRepository: DowntimeLogRepository,
  private val queueService: QueueService,
  private val visitRegistrar: VisitRegistrar,
) {
  /** 01.09 downtime pack v1 (CS-09): open the paper-mode window; one open window per hospital. */
  @Transactional
  fun openWindow(
    reason: String,
    hospitalId: UUID,
  ): DowntimeLogView {
    if (reason.isBlank()) throw IllegalArgumentException("DOWNTIME_REASON_REQUIRED")
    if (downtimeLogRepository.findFirstByHospitalIdAndEndedAtIsNullOrderByStartedAtDesc(hospitalId) != null) {
      throw IllegalStateException("DOWNTIME_ALREADY_OPEN")
    }
    return DowntimeLogView.from(downtimeLogRepository.save(DowntimeLog(hospitalId = hospitalId, reason = reason)))
  }

  @Transactional
  fun closeWindow(
    id: UUID,
    hospitalId: UUID?,
  ): DowntimeLogView {
    val log = downtimeLogRepository.findById(id).orElseThrow { IllegalArgumentException("DOWNTIME_WINDOW_NOT_FOUND") }
    requireSameHospital(hospitalId, log.hospitalId)
    if (log.endedAt != null) throw IllegalStateException("DOWNTIME_NOT_OPEN")
    log.endedAt = Instant.now()
    return DowntimeLogView.from(downtimeLogRepository.save(log))
  }

  @Transactional(readOnly = true)
  fun listWindows(hospitalId: UUID?): List<DowntimeLogView> =
    downtimeLogRepository.findByHospitalIdOrderByStartedAtDesc(hospitalId).map { DowntimeLogView.from(it) }

  /** CS-09 re-entry: paper tokens return with their original numbers (no loss); paper
   * check-ins re-stamp the pre-booked visits as checked-in (idempotent). */
  @Transactional
  fun reenter(
    dto: DowntimeReentryDto,
    hospitalId: UUID,
  ): DowntimeReentryResult {
    val tokens =
      (dto.tokens ?: emptyList()).map { item ->
        queueService.reenterToken(
          queueType = item.queueType ?: throw IllegalArgumentException("QUEUE_TYPE_REQUIRED"),
          tokenNumber = item.tokenNumber ?: throw IllegalArgumentException("TOKEN_NUMBER_REQUIRED"),
          patientId = item.patientId ?: throw IllegalArgumentException("PATIENT_ID_REQUIRED"),
          patientName = item.patientName,
          priority = item.priority,
          servicePointId = item.servicePointId,
          departmentId = item.departmentId,
          hospitalId = hospitalId,
        )
      }
    val visits: List<VisitDto> =
      (dto.visitIds ?: emptyList()).map { visitId ->
        val visit = visitRegistrar.checkIn(visitId, hospitalId)
        if (visit.status != "checked-in") throw IllegalStateException("VISIT_CHECKIN_FAILED")
        visit
      }
    return DowntimeReentryResult(tokens = tokens, visits = visits)
  }
}
