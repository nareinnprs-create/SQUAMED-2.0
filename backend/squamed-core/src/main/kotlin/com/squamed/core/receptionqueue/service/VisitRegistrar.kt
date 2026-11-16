package com.squamed.core.receptionqueue.service

import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.receptionqueue.api.ConsultReceiptDto
import com.squamed.core.receptionqueue.api.ConsultReceiptIssueDto
import com.squamed.core.receptionqueue.api.VisitCreateDto
import com.squamed.core.receptionqueue.api.VisitCreateResult
import com.squamed.core.receptionqueue.api.VisitDto
import com.squamed.core.receptionqueue.api.VisitPassDto
import com.squamed.core.receptionqueue.api.VisitPassIssueDto
import com.squamed.core.receptionqueue.domain.ConsultReceipt
import com.squamed.core.receptionqueue.domain.VisitPass
import com.squamed.core.receptionqueue.domain.VisitRegistration
import com.squamed.core.receptionqueue.repository.ConsultReceiptRepository
import com.squamed.core.receptionqueue.repository.VisitPassRepository
import com.squamed.core.receptionqueue.repository.VisitRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.Year
import java.util.UUID

@Service
class VisitRegistrar(
  private val visitRegistrationRepository: VisitRegistrationRepository,
  private val queueService: QueueService,
  private val visitPassRepository: VisitPassRepository,
  private val consultReceiptRepository: ConsultReceiptRepository,
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
  fun getVisit(
    id: UUID,
    hospitalId: UUID?,
  ): VisitDto = VisitDto.from(findVisit(id, hospitalId))

  @Transactional(readOnly = true)
  fun listVisits(
    status: String?,
    patientId: UUID?,
    from: Instant,
    hospitalId: UUID?,
  ): List<VisitDto> {
    val visits =
      when {
        patientId != null -> visitRegistrationRepository.findByPatientId(hospitalId, patientId)
        status != null -> visitRegistrationRepository.findByStatus(hospitalId, status)
        else -> visitRegistrationRepository.findAllForHospital(hospitalId)
      }
    return visits
      .filter { (it.createdAt ?: Instant.EPOCH) >= from }
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { VisitDto.from(it) }
  }

  @Transactional
  fun checkIn(
    visitId: UUID,
    hospitalId: UUID?,
  ): VisitDto {
    val visit = findVisit(visitId, hospitalId)
    visit.status = "checked-in"
    visitRegistrationRepository.save(visit)
    return VisitDto.from(visit)
  }

  @Transactional
  fun complete(
    visitId: UUID,
    hospitalId: UUID?,
  ): VisitDto {
    val visit = findVisit(visitId, hospitalId)
    visit.status = "completed"
    visitRegistrationRepository.save(visit)
    return VisitDto.from(visit)
  }

  @Transactional
  fun cancelVisit(
    visitId: UUID,
    reason: String?,
    hospitalId: UUID?,
  ): VisitDto {
    val visit = findVisit(visitId, hospitalId)
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

  /** 01.03 visitor/attendant pass: issue a numbered pass for a registered visit. */
  @Transactional
  fun issuePass(
    visitId: UUID,
    dto: VisitPassIssueDto,
    hospitalId: UUID?,
  ): VisitPassDto {
    val visit = findVisit(visitId, hospitalId)
    if (visit.status == "cancelled") throw IllegalStateException("VISIT_CANCELLED")
    val visitorName = dto.visitorName?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("VISITOR_NAME_REQUIRED")
    // Compute the pass number before any mutation (01.02 auto-flush lesson).
    val sequence =
      visitPassRepository
        .findTopForHospital(visit.hospitalId)
        ?.passNumber
        ?.substringAfterLast('-')
        ?.toIntOrNull() ?: 0
    val pass =
      visitPassRepository.save(
        VisitPass(
          hospitalId = visit.hospitalId ?: throw IllegalStateException("VISIT_HOSPITAL_MISSING"),
          visitId = visitId,
          patientId = visit.patientId,
          passNumber = String.format("VPS-%04d", sequence + 1),
          visitorName = visitorName,
          relation = dto.relation ?: "attendant",
          phone = dto.phone,
          idReference = dto.idReference,
          issuedAt = Instant.now(),
        ),
      )
    return VisitPassDto.from(pass)
  }

  @Transactional(readOnly = true)
  fun listPasses(
    visitId: UUID,
    hospitalId: UUID?,
  ): List<VisitPassDto> {
    findVisit(visitId, hospitalId)
    return visitPassRepository
      .findByVisitId(hospitalId, visitId)
      .sortedBy { it.issuedAt }
      .map { VisitPassDto.from(it) }
  }

  /** Returning a pass closes it; only active passes return (idempotence guard → 409). */
  @Transactional
  fun returnPass(
    passId: UUID,
    hospitalId: UUID?,
  ): VisitPassDto {
    val pass =
      visitPassRepository.findById(passId).orElseThrow { IllegalArgumentException("VISIT_PASS_NOT_FOUND") }
    requireSameHospital(hospitalId, pass.hospitalId)
    if (pass.status != "active") throw IllegalStateException("VISIT_PASS_NOT_ACTIVE")
    pass.status = "returned"
    pass.returnedAt = Instant.now()
    visitPassRepository.save(pass)
    return VisitPassDto.from(pass)
  }

  /** 01.03 consult-fee receipt: point-of-service receipt for a visit's consult fee. */
  @Transactional
  fun issueReceipt(
    visitId: UUID,
    dto: ConsultReceiptIssueDto,
    hospitalId: UUID?,
  ): ConsultReceiptDto {
    val visit = findVisit(visitId, hospitalId)
    if (visit.status == "cancelled") throw IllegalStateException("VISIT_CANCELLED")
    val amount = dto.amountQar ?: throw IllegalArgumentException("AMOUNT_REQUIRED")
    if (amount <= BigDecimal.ZERO) throw IllegalArgumentException("AMOUNT_POSITIVE_REQUIRED")
    // Compute the receipt number before any mutation (01.02 auto-flush lesson).
    val sequence =
      consultReceiptRepository
        .findTopForHospital(visit.hospitalId)
        ?.receiptNumber
        ?.substringAfterLast('-')
        ?.toIntOrNull() ?: 0
    val receipt =
      consultReceiptRepository.save(
        ConsultReceipt(
          hospitalId = visit.hospitalId ?: throw IllegalStateException("VISIT_HOSPITAL_MISSING"),
          visitId = visitId,
          patientId = visit.patientId,
          receiptNumber = String.format("RCP-%04d", sequence + 1),
          amountQar = amount,
          mode = dto.mode ?: "cash",
          issuedAt = Instant.now(),
        ),
      )
    return ConsultReceiptDto.from(receipt)
  }

  @Transactional(readOnly = true)
  fun listReceipts(
    visitId: UUID,
    hospitalId: UUID?,
  ): List<ConsultReceiptDto> {
    findVisit(visitId, hospitalId)
    return consultReceiptRepository
      .findByVisitId(hospitalId, visitId)
      .sortedBy { it.issuedAt }
      .map { ConsultReceiptDto.from(it) }
  }

  /** Voiding a receipt keeps the audit trail: status flips to voided, never deleted. */
  @Transactional
  fun voidReceipt(
    receiptId: UUID,
    hospitalId: UUID?,
  ): ConsultReceiptDto {
    val receipt =
      consultReceiptRepository.findById(receiptId).orElseThrow { IllegalArgumentException("CONSULT_RECEIPT_NOT_FOUND") }
    requireSameHospital(hospitalId, receipt.hospitalId)
    if (receipt.status != "issued") throw IllegalStateException("CONSULT_RECEIPT_NOT_ISSUED")
    receipt.status = "voided"
    receipt.voidedAt = Instant.now()
    consultReceiptRepository.save(receipt)
    return ConsultReceiptDto.from(receipt)
  }

  private fun findVisit(
    id: UUID,
    hospitalId: UUID?,
  ): VisitRegistration {
    val visit = visitRegistrationRepository.findById(id).orElseThrow { IllegalArgumentException("VISIT_NOT_FOUND") }
    requireSameHospital(hospitalId, visit.hospitalId)
    return visit
  }
}
