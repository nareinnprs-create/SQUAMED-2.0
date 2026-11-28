package com.squamed.core.telemedicine.service

import com.squamed.core.encounter.service.EncounterService
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.masterdata.repository.EmployeeRepository
import com.squamed.core.telemedicine.domain.TelemedicineConsult
import com.squamed.core.telemedicine.repository.TelemedicineConsultRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ConsultView(
  val id: UUID?,
  val hospitalId: UUID,
  val patientId: UUID,
  val doctorUserId: UUID,
  val rmpEmployeeId: UUID,
  val consultType: String,
  val mode: String,
  val status: String,
  val scheduledAt: Instant,
  val startedAt: Instant?,
  val endedAt: Instant?,
  val encounterId: UUID?,
  val meetingRef: String?,
  val escapeReason: String?,
) {
  companion object {
    fun from(entity: TelemedicineConsult): ConsultView =
      ConsultView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        doctorUserId = entity.doctorUserId,
        rmpEmployeeId = entity.rmpEmployeeId,
        consultType = entity.consultType,
        mode = entity.mode,
        status = entity.status,
        scheduledAt = entity.scheduledAt,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
        encounterId = entity.encounterId,
        meetingRef = entity.meetingRef,
        escapeReason = entity.escapeReason,
      )
  }
}

// 06.02: the India Telemedicine Guidelines 2020 checks the platform can enforce on its own. The
// video transport itself is a vendor SDK (exp:E9) and the payment link a PSP (exp:E4); neither is
// faked here - a consult carries a meetingRef for whatever transport is wired in.
@Service
class TelemedicineService(
  private val repository: TelemedicineConsultRepository,
  private val employeeRepository: EmployeeRepository,
  private val encounterService: EncounterService,
) {
  companion object {
    private val CONSULT_TYPES = setOf("FIRST_CONSULT", "FOLLOW_UP")
    private val MODES = setOf("VIDEO", "AUDIO", "CHAT")
  }

  @Transactional
  fun schedule(
    hospitalId: UUID,
    patientId: UUID,
    doctorUserId: UUID,
    rmpEmployeeId: UUID,
    consultType: String,
    mode: String,
    scheduledAt: Instant,
    meetingRef: String?,
    actorId: UUID?,
  ): ConsultView {
    val type = consultType.uppercase()
    val transport = mode.uppercase()
    require(type in CONSULT_TYPES) { "TELEMEDICINE_CONSULT_TYPE_INVALID" }
    require(transport in MODES) { "TELEMEDICINE_MODE_INVALID" }
    // Guideline: a first consult is video. Audio/chat is only for a follow-up of a known patient.
    require(type != "FIRST_CONSULT" || transport == "VIDEO") { "TELEMEDICINE_FIRST_CONSULT_REQUIRES_VIDEO" }
    verifyRmp(hospitalId, rmpEmployeeId)
    val consult =
      repository.save(
        TelemedicineConsult(
          hospitalId = hospitalId,
          patientId = patientId,
          doctorUserId = doctorUserId,
          rmpEmployeeId = rmpEmployeeId,
          consultType = type,
          mode = transport,
          scheduledAt = scheduledAt,
          meetingRef = meetingRef,
        ).also { it.createdBy = actorId },
      )
    return ConsultView.from(consult)
  }

  fun list(hospitalId: UUID): List<ConsultView> = repository.findByHospitalIdOrderByScheduledAtDesc(hospitalId).map(ConsultView::from)

  fun get(
    id: UUID,
    hospitalId: UUID,
  ): ConsultView = ConsultView.from(find(id, hospitalId))

  // The consult is documented as a normal encounter, so notes, diagnoses and prescriptions land in
  // the same clinical record as an in-person visit rather than a parallel telemedicine silo.
  @Transactional
  fun start(
    id: UUID,
    hospitalId: UUID,
    principal: SquamedPrincipal,
  ): ConsultView {
    val consult = find(id, hospitalId)
    require(consult.status == "SCHEDULED") { "TELEMEDICINE_CONSULT_NOT_SCHEDULED" }
    verifyRmp(hospitalId, consult.rmpEmployeeId)
    val encounter =
      encounterService.startEncounter(
        patientId = consult.patientId,
        doctorId = consult.doctorUserId,
        reason = "Telemedicine ${consult.consultType.lowercase().replace('_', ' ')}",
        hospitalId = hospitalId,
        principal = principal,
        encounterType = "telemedicine",
      )
    consult.encounterId = encounter.id
    consult.startedAt = Instant.now()
    consult.status = "IN_PROGRESS"
    return ConsultView.from(repository.save(consult))
  }

  @Transactional
  fun complete(
    id: UUID,
    hospitalId: UUID,
  ): ConsultView {
    val consult = find(id, hospitalId)
    require(consult.status == "IN_PROGRESS") { "TELEMEDICINE_CONSULT_NOT_IN_PROGRESS" }
    consult.endedAt = Instant.now()
    consult.status = "COMPLETED"
    return ConsultView.from(repository.save(consult))
  }

  // Tech-support escape path: when the transport fails, the consult is closed as an escape with a
  // reason instead of being left open or silently cancelled.
  @Transactional
  fun escape(
    id: UUID,
    hospitalId: UUID,
    reason: String,
  ): ConsultView {
    val consult = find(id, hospitalId)
    require(consult.status in setOf("SCHEDULED", "IN_PROGRESS")) { "TELEMEDICINE_CONSULT_CLOSED" }
    require(reason.isNotBlank()) { "TELEMEDICINE_ESCAPE_REASON_REQUIRED" }
    consult.escapeReason = reason.take(300)
    consult.endedAt = Instant.now()
    consult.status = "SUPPORT_ESCAPE"
    return ConsultView.from(repository.save(consult))
  }

  @Transactional
  fun cancel(
    id: UUID,
    hospitalId: UUID,
  ): ConsultView {
    val consult = find(id, hospitalId)
    require(consult.status == "SCHEDULED") { "TELEMEDICINE_CONSULT_NOT_SCHEDULED" }
    consult.status = "CANCELLED"
    return ConsultView.from(repository.save(consult))
  }

  private fun find(
    id: UUID,
    hospitalId: UUID,
  ): TelemedicineConsult = repository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("TELEMEDICINE_CONSULT_NOT_FOUND")

  // RMP = registered medical practitioner. Without a verified registration the consult cannot be
  // scheduled at all, which is the guideline's blocking rule.
  private fun verifyRmp(
    hospitalId: UUID,
    employeeId: UUID,
  ) {
    val employee = employeeRepository.findById(employeeId).orElseThrow { IllegalArgumentException("TELEMEDICINE_RMP_NOT_FOUND") }
    require(employee.hospitalId == hospitalId) { "TELEMEDICINE_RMP_NOT_FOUND" }
    require(employee.isActive) { "TELEMEDICINE_RMP_INACTIVE" }
    require(!employee.licenseNumber.isNullOrBlank()) { "TELEMEDICINE_RMP_UNVERIFIED" }
    require(employee.licenseExpiry == null || !employee.licenseExpiry!!.isBefore(LocalDate.now())) {
      "TELEMEDICINE_RMP_LICENCE_EXPIRED"
    }
  }
}
