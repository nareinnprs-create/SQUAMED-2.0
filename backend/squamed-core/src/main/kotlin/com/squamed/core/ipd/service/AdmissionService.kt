package com.squamed.core.ipd.service

import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.domain.Bed
import com.squamed.core.hospitalconfig.repository.BedRepository
import com.squamed.core.ipd.api.AdmissionCreateDto
import com.squamed.core.ipd.api.AdmissionDischargeDto
import com.squamed.core.ipd.api.AdmissionTransferDto
import com.squamed.core.ipd.api.AdmissionTransferView
import com.squamed.core.ipd.api.AdmissionView
import com.squamed.core.ipd.api.AttendantCreateDto
import com.squamed.core.ipd.api.AttendantView
import com.squamed.core.ipd.api.DischargeSummaryUpsertDto
import com.squamed.core.ipd.api.DischargeSummaryView
import com.squamed.core.ipd.domain.Admission
import com.squamed.core.ipd.domain.AdmissionTransfer
import com.squamed.core.ipd.domain.Attendant
import com.squamed.core.ipd.domain.DischargeSummary
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.ipd.repository.AdmissionTransferRepository
import com.squamed.core.ipd.repository.AttendantRepository
import com.squamed.core.ipd.repository.DischargeSummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AdmissionService(
  private val admissionRepository: AdmissionRepository,
  private val transferRepository: AdmissionTransferRepository,
  private val dischargeSummaryRepository: DischargeSummaryRepository,
  private val attendantRepository: AttendantRepository,
  private val bedRepository: BedRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun admit(
    dto: AdmissionCreateDto,
    actorId: UUID,
  ): AdmissionView {
    val patientId = dto.patientId ?: throw IllegalArgumentException("ADMISSION_PATIENT_REQUIRED")
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("ADMISSION_HOSPITAL_REQUIRED")
    val admissionType = dto.admissionType ?: "elective"
    if (admissionType !in ADMISSION_TYPES) throw IllegalArgumentException("ADMISSION_INVALID_TYPE")
    val source = dto.source ?: "reception"
    if (source !in ADMISSION_SOURCES) throw IllegalArgumentException("ADMISSION_INVALID_SOURCE")

    val bed = allocateBed(hospitalId, dto.bedId, dto.wardId)
    val now = Instant.now()
    val admission =
      admissionRepository.save(
        Admission(
          hospitalId = hospitalId,
          patientId = patientId,
          admissionType = admissionType,
          status = "admitted",
          bedId = bed.id,
          wardId = dto.wardId ?: bed.wardId,
          roomId = bed.roomId,
          attendingDoctorId = dto.attendingDoctorId,
          admissionDiagnosis = dto.admissionDiagnosis,
          source = source,
          admittedAt = now,
          admittedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    occupyBed(bed)
    val admissionId = admission.id ?: throw IllegalStateException("ADMISSION_ID_MISSING")
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "admission.admitted",
        occurredAt = now,
        source = "ipd-service",
        entityType = "admission",
        entityId = admissionId,
        hospitalId = hospitalId,
        payload = mapOf("patientId" to patientId, "bedId" to bed.id, "admissionType" to admissionType),
      ),
    )
    return AdmissionView.from(admission)
  }

  @Transactional(readOnly = true)
  fun listAdmissions(
    patientId: UUID?,
    status: String?,
    wardId: UUID?,
    hospitalId: UUID?,
  ): List<AdmissionView> {
    val admissions =
      when {
        patientId != null -> admissionRepository.findByPatientId(patientId, hospitalId)
        status != null -> admissionRepository.findByStatus(status, hospitalId)
        wardId != null -> admissionRepository.findByWardId(wardId, hospitalId)
        else -> admissionRepository.findAllByHospital(hospitalId)
      }
    return admissions
      .sortedByDescending { it.admittedAt ?: Instant.EPOCH }
      .map { AdmissionView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getAdmission(
    id: UUID,
    hospitalId: UUID?,
  ): AdmissionView {
    val admission = findAdmission(id)
    // 00.04 tenant sweep: the parent admission's hospital gates this read.
    requireSameHospital(hospitalId, admission.hospitalId)
    return AdmissionView.from(admission)
  }

  @Transactional(readOnly = true)
  fun listTransfers(
    admissionId: UUID,
    hospitalId: UUID?,
  ): List<AdmissionTransferView> {
    // 00.04 tenant sweep: sub-resources are admission-keyed — one parent check covers them.
    requireSameHospital(hospitalId, findAdmission(admissionId).hospitalId)
    return transferRepository.findByAdmissionIdOrderByTransferredAtDesc(admissionId).map { AdmissionTransferView.from(it) }
  }

  @Transactional
  fun transfer(
    admissionId: UUID,
    dto: AdmissionTransferDto,
    actorId: UUID,
  ): AdmissionView {
    val admission = findAdmission(admissionId)
    if (admission.status != "admitted") throw IllegalArgumentException("ADMISSION_NOT_ADMITTED")
    val toBedId = dto.toBedId ?: throw IllegalArgumentException("ADMISSION_TO_BED_REQUIRED")
    val currentBedId = admission.bedId
    if (toBedId == currentBedId) throw IllegalArgumentException("ADMISSION_SAME_BED")
    val toBed = bedRepository.findById(toBedId).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") }
    if (toBed.status != "available") throw IllegalArgumentException("ADMISSION_BED_NOT_AVAILABLE")

    val oldBed = currentBedId?.let { bedRepository.findById(it).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") } }
    val now = Instant.now()
    transferRepository.save(
      AdmissionTransfer(
        admissionId = admissionId,
        fromBedId = currentBedId,
        toBedId = toBedId,
        reason = dto.reason,
        transferredAt = now,
        transferredBy = actorId,
      ),
    )
    admission.bedId = toBedId
    admission.wardId = toBed.wardId
    admission.roomId = toBed.roomId
    admission.updatedBy = actorId
    admissionRepository.save(admission)
    oldBed?.status = "available"
    toBed.status = "occupied"
    bedRepository.saveAll(listOfNotNull(oldBed, toBed))
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "admission.transferred",
        occurredAt = now,
        source = "ipd-service",
        entityType = "admission",
        entityId = admissionId,
        hospitalId = admission.hospitalId,
        payload = mapOf("fromBedId" to currentBedId, "toBedId" to toBedId, "reason" to dto.reason),
      ),
    )
    return AdmissionView.from(admission)
  }

  @Transactional
  fun discharge(
    admissionId: UUID,
    dto: AdmissionDischargeDto,
    actorId: UUID,
  ): AdmissionView {
    val admission = findAdmission(admissionId)
    if (admission.status != "admitted") throw IllegalArgumentException("ADMISSION_NOT_ADMITTED")
    val now = Instant.now()
    admission.status = "discharged"
    admission.dischargedAt = now
    admission.dischargeType = dto.dischargeType
    admission.dischargeNotes = dto.dischargeNotes
    admission.updatedBy = actorId
    admissionRepository.save(admission)
    dischargeBed(admission.bedId)
    if (dto.summaryText != null || dto.followUpPlan != null || dto.medicationsAtDischarge != null || dto.instructions != null) {
      upsertSummary(
        admissionId,
        DischargeSummaryUpsertDto(
          summaryText = dto.summaryText,
          followUpPlan = dto.followUpPlan,
          medicationsAtDischarge = dto.medicationsAtDischarge,
          instructions = dto.instructions,
        ),
        actorId,
      )
    }
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "admission.discharged",
        occurredAt = now,
        source = "ipd-service",
        entityType = "admission",
        entityId = admissionId,
        hospitalId = admission.hospitalId,
        payload = mapOf("patientId" to admission.patientId, "dischargeType" to dto.dischargeType),
      ),
    )
    return AdmissionView.from(admission)
  }

  @Transactional
  fun upsertDischargeSummary(
    admissionId: UUID,
    dto: DischargeSummaryUpsertDto,
    actorId: UUID,
  ): DischargeSummaryView = upsertSummary(admissionId, dto, actorId)

  @Transactional(readOnly = true)
  fun getDischargeSummary(
    admissionId: UUID,
    hospitalId: UUID?,
  ): DischargeSummaryView {
    requireSameHospital(hospitalId, findAdmission(admissionId).hospitalId)
    return dischargeSummaryRepository
      .findByAdmissionId(admissionId)
      .map { DischargeSummaryView.from(it) }
      .orElseThrow { IllegalArgumentException("ADMISSION_SUMMARY_MISSING") }
  }

  @Transactional(readOnly = true)
  fun listAttendants(
    admissionId: UUID,
    hospitalId: UUID?,
  ): List<AttendantView> {
    requireSameHospital(hospitalId, findAdmission(admissionId).hospitalId)
    return attendantRepository.findByAdmissionId(admissionId).map { AttendantView.from(it) }
  }

  @Transactional
  fun addAttendant(
    admissionId: UUID,
    dto: AttendantCreateDto,
    actorId: UUID,
  ): AttendantView {
    val name = dto.name ?: throw IllegalArgumentException("ATTENDANT_NAME_REQUIRED")
    val attendant =
      attendantRepository.save(
        Attendant(
          admissionId = admissionId,
          name = name,
          relationship = dto.relationship,
          phone = dto.phone,
          notes = dto.notes,
        ).also { it.createdBy = actorId },
      )
    return AttendantView.from(attendant)
  }

  private fun upsertSummary(
    admissionId: UUID,
    dto: DischargeSummaryUpsertDto,
    actorId: UUID,
  ): DischargeSummaryView {
    val summary =
      dischargeSummaryRepository.findByAdmissionId(admissionId).orElseGet {
        DischargeSummary(admissionId = admissionId, preparedAt = Instant.now())
      }
    summary.summaryText = dto.summaryText ?: summary.summaryText
    summary.followUpPlan = dto.followUpPlan ?: summary.followUpPlan
    summary.medicationsAtDischarge = dto.medicationsAtDischarge ?: summary.medicationsAtDischarge
    summary.instructions = dto.instructions ?: summary.instructions
    if (summary.createdAt == null) summary.preparedBy = actorId
    dischargeSummaryRepository.save(summary)
    return DischargeSummaryView.from(summary)
  }

  private fun allocateBed(
    hospitalId: UUID,
    requestedBedId: UUID?,
    wardId: UUID?,
  ): Bed {
    if (requestedBedId != null) {
      val bed = bedRepository.findById(requestedBedId).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") }
      if (bed.status != "available") throw IllegalArgumentException("ADMISSION_BED_NOT_AVAILABLE")
      return bed
    }
    val candidates = bedRepository.findByStatus("available").filter { bed -> bed.hospitalId == hospitalId }
    val bed = if (wardId != null) candidates.firstOrNull { it.wardId == wardId } else candidates.firstOrNull()
    return bed ?: throw IllegalArgumentException("ADMISSION_NO_BED_AVAILABLE")
  }

  private fun occupyBed(bed: Bed) {
    bed.status = "occupied"
    bedRepository.save(bed)
  }

  private fun dischargeBed(bedId: UUID?) {
    if (bedId == null) return
    val bed = bedRepository.findById(bedId).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") }
    bed.status = "available"
    bedRepository.save(bed)
  }

  private fun findAdmission(id: UUID): Admission = admissionRepository.findById(id).orElseThrow { IllegalArgumentException("ADMISSION_NOT_FOUND") }

  companion object {
    val ADMISSION_TYPES = setOf("emergency", "elective", "day_care", "transfer", "observation")
    val ADMISSION_SOURCES = setOf("reception", "er", "opd", "transfer")
  }
}
