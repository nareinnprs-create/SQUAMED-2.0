package com.squamed.core.ipd.service

import com.squamed.core.billing.repository.InvoiceRepository
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
import com.squamed.core.ipd.api.BedBoardRowView
import com.squamed.core.ipd.api.DischargeChecklistView
import com.squamed.core.ipd.api.DischargeSummaryUpsertDto
import com.squamed.core.ipd.api.DischargeSummaryView
import com.squamed.core.ipd.api.PlanDischargeDto
import com.squamed.core.ipd.api.SummarySignDto
import com.squamed.core.ipd.domain.Admission
import com.squamed.core.ipd.domain.AdmissionTransfer
import com.squamed.core.ipd.domain.Attendant
import com.squamed.core.ipd.domain.DischargeChecklist
import com.squamed.core.ipd.domain.DischargeSummary
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.ipd.repository.AdmissionTransferRepository
import com.squamed.core.ipd.repository.AttendantRepository
import com.squamed.core.ipd.repository.DischargeChecklistRepository
import com.squamed.core.ipd.repository.DischargeSummaryRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
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
  private val dischargeChecklistRepository: DischargeChecklistRepository,
  private val bedRepository: BedRepository,
  private val invoiceRepository: InvoiceRepository,
  private val patientRecordRepository: PatientRecordRepository,
  private val taskService: TaskService,
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
    val isolation = (dto.isolationType ?: "NONE").uppercase()
    if (isolation !in ISOLATION_TYPES) throw IllegalArgumentException("ADMISSION_ISOLATION_INVALID")

    val bed = allocateBed(hospitalId, dto.bedId, dto.wardId, isolation, patientId)
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
          isolationType = isolation,
        ).also { it.createdBy = actorId },
      )
    occupyBed(bed)
    val admissionId = admission.id ?: throw IllegalStateException("ADMISSION_ID_MISSING")
    // 04.01: created + admitted + bed assignment are separate cataloged events.
    publish(admission, "admission.created", now, mapOf("patientId" to patientId, "admissionType" to admissionType))
    publish(admission, "admission.admitted", now, mapOf("patientId" to patientId, "bedId" to bed.id, "admissionType" to admissionType))
    publish(admission, "admission.bed_assigned", now, mapOf("bedId" to bed.id, "isolationType" to isolation))
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
    // 04.01: transfers keep the isolation + gender rules.
    if (toBed.isolationType != admission.isolationType) throw IllegalArgumentException("ADMISSION_BED_RULE_VIOLATION")
    val patientGender =
      patientRecordRepository
        .findById(admission.patientId)
        .orElse(null)
        ?.gender
        ?.lowercase()
    if (toBed.genderPolicy != "ANY" && patientGender != null && toBed.genderPolicy.lowercase() != patientGender) {
      throw IllegalArgumentException("ADMISSION_BED_RULE_VIOLATION")
    }

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
    publish(admission, "admission.bed_assigned", now, mapOf("bedId" to toBedId, "fromBedId" to currentBedId))
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
    // 04.01: the discharge checklist gates the state machine.
    val checklist = dischargeChecklistRepository.findByAdmissionId(admissionId).orElse(null)
    val summary = dischargeSummaryRepository.findByAdmissionId(admissionId).orElse(null)
    val hasUnpaid = invoiceRepository.findByPatientId(null, admission.patientId).any { it.status == "unpaid" || it.status == "partial" }
    if (checklist?.pharmacyReturnDone != true || checklist.finalBillVerified != true || summary?.status != "SIGNED" || hasUnpaid) {
      throw IllegalStateException("DISCHARGE_CHECKLIST_INCOMPLETE")
    }
    val now = Instant.now()
    admission.status = "discharged"
    admission.dischargedAt = now
    admission.dischargeType = dto.dischargeType
    admission.dischargeNotes = dto.dischargeNotes
    admission.updatedBy = actorId
    admissionRepository.save(admission)
    // 04.01: the released bed goes to housekeeping (cleaning), not straight back to available.
    releaseBedForHousekeeping(admission, actorId)
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
    publish(admission, "admission.discharged", now, mapOf("patientId" to admission.patientId, "dischargeType" to dto.dischargeType))
    return AdmissionView.from(admission)
  }

  // -- 04.01 expected-discharge planning + checklist + summary signature --

  @Transactional
  fun planDischarge(
    admissionId: UUID,
    dto: PlanDischargeDto,
    actorId: UUID,
  ): AdmissionView {
    val admission = findAdmission(admissionId)
    if (admission.status != "admitted") throw IllegalArgumentException("ADMISSION_NOT_ADMITTED")
    admission.expectedDischargeAt = dto.expectedDischargeAt
    admission.updatedBy = actorId
    admissionRepository.save(admission)
    publish(admission, "admission.discharge_planned", Instant.now(), mapOf("expectedDischargeAt" to dto.expectedDischargeAt.toString()))
    return AdmissionView.from(admission)
  }

  @Transactional
  fun getChecklist(
    admissionId: UUID,
    hospitalId: UUID?,
  ): DischargeChecklistView {
    requireSameHospital(hospitalId, findAdmission(admissionId).hospitalId)
    return dischargeChecklistRepository.findByAdmissionId(admissionId).map { DischargeChecklistView.from(it) }.orElseGet {
      DischargeChecklistView(admissionId, false, null, false, null, null)
    }
  }

  @Transactional
  fun completeChecklistItem(
    admissionId: UUID,
    item: String,
    actorId: UUID,
  ): DischargeChecklistView {
    val admission = findAdmission(admissionId)
    if (admission.status != "admitted") throw IllegalArgumentException("ADMISSION_NOT_ADMITTED")
    val checklist = dischargeChecklistRepository.findByAdmissionId(admissionId).orElseGet { DischargeChecklist(admissionId = admissionId) }
    when (item.lowercase()) {
      "pharmacy_return" -> {
        checklist.pharmacyReturnDone = true
        checklist.pharmacyReturnAt = Instant.now()
      }

      "final_bill" -> {
        val hasUnpaid = invoiceRepository.findByPatientId(null, admission.patientId).any { it.status == "unpaid" || it.status == "partial" }
        if (hasUnpaid) throw IllegalStateException("DISCHARGE_BILL_UNSETTLED")
        checklist.finalBillVerified = true
      }

      else -> {
        throw IllegalArgumentException("DISCHARGE_CHECKLIST_ITEM_INVALID")
      }
    }
    if (checklist.pharmacyReturnDone && checklist.finalBillVerified) {
      checklist.completedBy = actorId
      checklist.completedAt = Instant.now()
    }
    return DischargeChecklistView.from(dischargeChecklistRepository.save(checklist))
  }

  @Transactional
  fun signSummary(
    admissionId: UUID,
    dto: SummarySignDto,
    actorId: UUID,
  ): DischargeSummaryView {
    val summary = dischargeSummaryRepository.findByAdmissionId(admissionId).orElseThrow { IllegalArgumentException("ADMISSION_SUMMARY_MISSING") }
    if (summary.status == "SIGNED") throw IllegalArgumentException("SUMMARY_ALREADY_SIGNED")
    if (dto.icdCodes.isEmpty() || dto.icdCodes.any { it.isBlank() }) throw IllegalArgumentException("SUMMARY_ICD_REQUIRED")
    if (summary.summaryText.isNullOrBlank()) throw IllegalArgumentException("SUMMARY_TEXT_REQUIRED")
    summary.status = "SIGNED"
    summary.icdCodes = dto.icdCodes.joinToString(",") { it.trim() }
    summary.signedBy = actorId
    summary.signedAt = Instant.now()
    val saved = dischargeSummaryRepository.save(summary)
    val admission = findAdmission(admissionId)
    publish(admission, "admission.summary_signed", Instant.now(), mapOf("icdCodes" to saved.icdCodes))
    return DischargeSummaryView.from(saved)
  }

  // -- 04.01 bed board + housekeeping transitions --

  @Transactional(readOnly = true)
  fun bedBoard(hospitalId: UUID?): List<BedBoardRowView> {
    val beds = if (hospitalId == null) bedRepository.findAll() else bedRepository.findAll().filter { it.hospitalId == hospitalId }
    val occupants =
      admissionRepository
        .findAllByHospital(hospitalId)
        .filter { it.status == "admitted" && it.bedId != null }
        .associateBy { it.bedId }
    return beds
      .sortedWith(compareBy({ it.wardId?.toString() ?: "" }, { it.label }))
      .map { bed ->
        val occupant = occupants[bed.id]
        BedBoardRowView(
          bedId = bed.id,
          label = bed.label,
          wardId = bed.wardId,
          roomId = bed.roomId,
          status = bed.status,
          isolationType = bed.isolationType,
          genderPolicy = bed.genderPolicy,
          admissionId = occupant?.id,
          patientId = occupant?.patientId,
          admittedAt = occupant?.admittedAt,
        )
      }
  }

  @Transactional
  fun markBed(
    bedId: UUID,
    status: String,
    hospitalId: UUID?,
  ): BedBoardRowView {
    val bed = bedRepository.findById(bedId).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") }
    requireSameHospital(hospitalId, bed.hospitalId)
    if (status !in setOf("cleaning", "available")) throw IllegalArgumentException("BED_STATUS_INVALID")
    bed.status = status
    val saved = bedRepository.save(bed)
    return BedBoardRowView(saved.id, saved.label, saved.wardId, saved.roomId, saved.status, saved.isolationType, saved.genderPolicy, null, null, null)
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
    // 04.01: a signed summary is immutable.
    if (summary.status == "SIGNED") throw IllegalArgumentException("SUMMARY_ALREADY_SIGNED")
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
    isolation: String,
    patientId: UUID,
  ): Bed {
    val patientGender =
      patientRecordRepository
        .findById(patientId)
        .orElse(null)
        ?.gender
        ?.lowercase()

    fun allowed(bed: Bed): Boolean {
      // Isolation beds serve isolation patients; non-isolation patients stay in NONE beds.
      if (bed.isolationType != isolation) return false
      // Gender policy: ANY or the patient's gender.
      if (bed.genderPolicy != "ANY" && patientGender != null && bed.genderPolicy.lowercase() != patientGender) return false
      return true
    }
    if (requestedBedId != null) {
      val bed = bedRepository.findById(requestedBedId).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") }
      if (bed.status != "available") throw IllegalArgumentException("ADMISSION_BED_NOT_AVAILABLE")
      if (!allowed(bed)) throw IllegalArgumentException("ADMISSION_BED_RULE_VIOLATION")
      return bed
    }
    val candidates =
      bedRepository
        .findByStatus("available")
        .filter { bed -> bed.hospitalId == hospitalId && allowed(bed) }
    val bed = if (wardId != null) candidates.firstOrNull { it.wardId == wardId } else candidates.firstOrNull()
    return bed ?: throw IllegalArgumentException("ADMISSION_NO_BED_AVAILABLE")
  }

  private fun occupyBed(bed: Bed) {
    bed.status = "occupied"
    bedRepository.save(bed)
  }

  // 04.01: bed release = cleaning + a housekeeping task; the board shows cleaning until cleared.
  private fun releaseBedForHousekeeping(
    admission: Admission,
    actorId: UUID,
  ) {
    val bedId = admission.bedId ?: return
    val bed = bedRepository.findById(bedId).orElseThrow { IllegalArgumentException("ADMISSION_BED_NOT_FOUND") }
    bed.status = "cleaning"
    bedRepository.save(bed)
    val task =
      taskService.createTask(
        TaskCreateDto(
          hospitalId = admission.hospitalId,
          title = "Housekeeping: bed ${bed.label}",
          description = "Terminal cleaning for bed ${bed.label} after discharge of admission ${admission.id}",
          priority = "normal",
          relatedType = "HOUSEKEEPING",
          relatedId = bed.id,
        ),
        actorId,
      )
    publish(admission, "housekeeping.task_created", Instant.now(), mapOf("bedId" to bed.id, "taskId" to task.id))
  }

  private fun publish(
    admission: Admission,
    eventType: String,
    at: Instant,
    payload: Map<String, Any?>,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = at,
        source = "ipd-service",
        entityType = "admission",
        entityId = admission.id,
        hospitalId = admission.hospitalId,
        payload = payload,
      ),
    )
  }

  private fun findAdmission(id: UUID): Admission = admissionRepository.findById(id).orElseThrow { IllegalArgumentException("ADMISSION_NOT_FOUND") }

  companion object {
    val ADMISSION_TYPES = setOf("emergency", "elective", "day_care", "transfer", "observation")
    val ADMISSION_SOURCES = setOf("reception", "er", "opd", "transfer")
    val ISOLATION_TYPES = setOf("NONE", "CONTACT", "DROPLET", "AIRBORNE")
  }
}
