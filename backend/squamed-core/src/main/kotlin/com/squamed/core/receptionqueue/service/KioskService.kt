package com.squamed.core.receptionqueue.service

import com.squamed.core.hospitalconfig.repository.DepartmentRepository
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.PatientIdentifierRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.receptionqueue.api.KioskCheckInRequest
import com.squamed.core.receptionqueue.api.KioskSlipDto
import com.squamed.core.receptionqueue.api.QueueEntryView
import com.squamed.core.receptionqueue.repository.QueueEntryRepository
import com.squamed.core.scheduling.domain.Appointment
import com.squamed.core.scheduling.repository.AppointmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 01.08 kiosk-lite: self-service check-in (ABHA / ABHA address / mobile) and QR re-entry. */
@Service
class KioskService(
  private val patientIdentifierRepository: PatientIdentifierRepository,
  private val patientRecordRepository: PatientRecordRepository,
  private val appointmentRepository: AppointmentRepository,
  private val queueEntryRepository: QueueEntryRepository,
  private val departmentRepository: DepartmentRepository,
  private val queueService: QueueService,
) {
  /** Live statuses that mean "already queued today" — re-check-in reuses the entry instead of duplicating it. */
  private val liveStatuses = listOf("waiting", "called", "in-progress", "postponed")

  @Transactional
  fun checkIn(
    dto: KioskCheckInRequest,
    hospitalId: UUID,
  ): KioskSlipDto {
    if (dto.identifierType.equals("qr", ignoreCase = true)) return reentryFromQr(dto.identifier, hospitalId)
    val patient = resolvePatient(dto.identifierType, dto.identifier, hospitalId)
    val appointment =
      findTodayAppointment(patient, hospitalId) ?: throw IllegalArgumentException("KIOSK_NO_APPOINTMENT")
    if (appointment.status != "arrived") {
      appointment.status = "arrived"
      appointment.checkedInAt = Instant.now()
      appointmentRepository.save(appointment)
    }
    val queueType = dto.queueType ?: "consult"
    val token =
      existingLiveEntry(patient, queueType, hospitalId)
        ?: queueService.addToQueue(
          queueType = queueType,
          patientId = patient.id ?: throw IllegalStateException("PATIENT_ID_MISSING"),
          patientName = fullName(patient),
          visitId = null,
          priority = appointment.priority,
          servicePointId = null,
          departmentId = appointment.departmentId,
          hospitalId = hospitalId,
        )
    return slip(patient, appointment, token)
  }

  /** QR mode is re-entry / re-print only: no state change, and the payload must not leak other hospitals. */
  private fun reentryFromQr(
    payload: String,
    hospitalId: UUID,
  ): KioskSlipDto {
    val parts = payload.split(":")
    if (parts.size != 5 || parts[0] != "SQM") throw IllegalArgumentException("KIOSK_QR_INVALID")
    val hid = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: throw IllegalArgumentException("KIOSK_QR_INVALID")
    val queueType = parts[2]
    val tokenNumber = parts[3]
    val queueDate = runCatching { LocalDate.parse(parts[4]) }.getOrNull() ?: throw IllegalArgumentException("KIOSK_QR_INVALID")
    // Foreign-hospital QR must be indistinguishable from garbage — one error code, no data leak.
    if (hid != hospitalId) throw IllegalArgumentException("KIOSK_QR_INVALID")
    val entry =
      queueEntryRepository.findByQueueTypeAndTokenNumberAndQueueDate(hospitalId, queueType, tokenNumber, queueDate)
        ?: throw IllegalArgumentException("KIOSK_QR_INVALID")
    val patient = patientRecordRepository.findById(entry.patientId).orElse(null) ?: throw IllegalArgumentException("KIOSK_QR_INVALID")
    return slip(patient, findTodayAppointment(patient, hospitalId), QueueEntryView.from(entry))
  }

  private fun resolvePatient(
    identifierType: String,
    identifier: String,
    hospitalId: UUID,
  ): PatientRecord =
    when (identifierType.lowercase()) {
      "abha" -> {
        if (!Regex("^\\d{14}$").matches(identifier.trim())) throw IllegalArgumentException("ABHA_NUMBER_INVALID")
        byIdentifiers("ABHA", identifier.trim(), hospitalId)
      }

      "abha_address" -> {
        val value = identifier.trim().lowercase()
        if (!Regex("^[a-z0-9._-]{4,20}@[a-z]{2,}$").matches(value)) throw IllegalArgumentException("ABHA_ADDRESS_INVALID")
        byIdentifiers("ABHA_ADDRESS", value, hospitalId)
      }

      "mobile" -> {
        single(
          patientRecordRepository
            .findByPhone(identifier.trim())
            .filter { usableIn(it, hospitalId) },
          identifier,
        )
      }

      else -> {
        throw IllegalArgumentException("KIOSK_IDENTIFIER_TYPE_INVALID")
      }
    }

  private fun byIdentifiers(
    idType: String,
    idValue: String,
    hospitalId: UUID,
  ): PatientRecord =
    single(
      patientIdentifierRepository
        .findByIdTypeAndIdValue(idType, idValue)
        .mapNotNull { it.patientId }
        .distinct()
        .mapNotNull { patientRecordRepository.findById(it).orElse(null) }
        .filter { usableIn(it, hospitalId) },
      idValue,
    )

  /** Kiosk patients must be live records of the kiosk's own hospital (merges and archives are invisible). */
  private fun usableIn(
    patient: PatientRecord,
    hospitalId: UUID,
  ): Boolean = patient.hospitalId == hospitalId && patient.mergedInto == null && patient.recordsStatus == "active"

  /** One match or none: more than one patient behind the same identifier is ambiguous for a self-service kiosk. */
  private fun single(
    matches: List<PatientRecord>,
    identifier: String,
  ): PatientRecord =
    when (matches.size) {
      1 -> matches.first()
      0 -> throw IllegalArgumentException("KIOSK_PATIENT_NOT_FOUND")
      else -> throw IllegalArgumentException("KIOSK_IDENTIFIER_AMBIGUOUS")
    }

  /** Today's live appointment at this hospital, earliest first; scheduled/confirmed/arrived all count. */
  private fun findTodayAppointment(
    patient: PatientRecord,
    hospitalId: UUID,
  ): Appointment? {
    val patientId = patient.id ?: return null
    val today = LocalDate.now()
    return appointmentRepository
      .findByPatientId(patientId)
      .filter {
        it.hospitalId == hospitalId &&
          it.status in listOf("scheduled", "confirmed", "arrived") &&
          it.scheduledStart.atZone(ZoneId.systemDefault()).toLocalDate() == today
      }.minByOrNull { it.scheduledStart }
  }

  private fun existingLiveEntry(
    patient: PatientRecord,
    queueType: String,
    hospitalId: UUID,
  ): QueueEntryView? =
    queueEntryRepository
      .findByHospitalIdAndStatusInAndQueueDate(hospitalId, liveStatuses, LocalDate.now())
      .firstOrNull { it.patientId == patient.id && it.queueType == queueType }
      ?.let(QueueEntryView::from)

  private fun fullName(patient: PatientRecord): String =
    listOfNotNull(patient.firstName, patient.middleName, patient.lastName)
      .filter { it.isNotBlank() }
      .joinToString(" ")

  private fun slip(
    patient: PatientRecord,
    appointment: Appointment?,
    token: QueueEntryView,
  ): KioskSlipDto =
    KioskSlipDto(
      patientId = patient.id ?: throw IllegalStateException("PATIENT_ID_MISSING"),
      patientMrn = patient.mrn,
      patientName = fullName(patient),
      appointmentId = appointment?.id,
      appointmentStart = appointment?.scheduledStart,
      departmentName =
        appointment?.departmentId?.let { deptId ->
          // Wayfinding line on the slip; a missing department row must not break the slip.
          departmentRepository.findById(deptId).orElse(null)?.name
        },
      token = token,
      printedAt = Instant.now(),
    )
}
