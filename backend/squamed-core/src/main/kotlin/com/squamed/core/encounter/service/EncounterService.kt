package com.squamed.core.encounter.service

import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.encounter.api.ClinicalNoteRequestDto
import com.squamed.core.encounter.api.ClinicalNoteView
import com.squamed.core.encounter.api.DiagnosisCatalogEntryView
import com.squamed.core.encounter.api.DiagnosisRequestDto
import com.squamed.core.encounter.api.DiagnosisView
import com.squamed.core.encounter.api.EncounterDetailView
import com.squamed.core.encounter.api.EncounterView
import com.squamed.core.encounter.api.FollowUpRequestDto
import com.squamed.core.encounter.api.NoteTemplateView
import com.squamed.core.encounter.api.OrderRequestDto
import com.squamed.core.encounter.api.OrderView
import com.squamed.core.encounter.api.PrescriptionRequestDto
import com.squamed.core.encounter.api.PrescriptionView
import com.squamed.core.encounter.api.ProcedureRequestDto
import com.squamed.core.encounter.api.ProcedureView
import com.squamed.core.encounter.api.ReferralRequestDto
import com.squamed.core.encounter.api.ReferralView
import com.squamed.core.encounter.api.SignNoteRequest
import com.squamed.core.encounter.api.VitalSignRequestDto
import com.squamed.core.encounter.api.VitalSignView
import com.squamed.core.encounter.domain.ClinicalNote
import com.squamed.core.encounter.domain.Diagnosis
import com.squamed.core.encounter.domain.DiagnosisCatalogEntry
import com.squamed.core.encounter.domain.Encounter
import com.squamed.core.encounter.domain.NoteTemplate
import com.squamed.core.encounter.domain.OrderEntry
import com.squamed.core.encounter.domain.Prescription
import com.squamed.core.encounter.domain.ProcedureEntry
import com.squamed.core.encounter.domain.Referral
import com.squamed.core.encounter.domain.VitalSign
import com.squamed.core.encounter.repository.ClinicalNoteRepository
import com.squamed.core.encounter.repository.DiagnosisCatalogRepository
import com.squamed.core.encounter.repository.DiagnosisRepository
import com.squamed.core.encounter.repository.EncounterRepository
import com.squamed.core.encounter.repository.NoteTemplateRepository
import com.squamed.core.encounter.repository.OrderEntryRepository
import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.encounter.repository.ProcedureRepository
import com.squamed.core.encounter.repository.ReferralRepository
import com.squamed.core.encounter.repository.VitalSignRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.identity.security.SquamedPermissionEvaluator
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.medsafety.api.MedSafetyOverrideDto
import com.squamed.core.medsafety.service.MedicationSafetyService
import com.squamed.core.patient.domain.Allergy
import com.squamed.core.patient.repository.AllergyRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.scheduling.service.SchedulingService
import com.squamed.core.search.SearchIndexer
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class EncounterService(
  private val encounterRepository: EncounterRepository,
  private val vitalSignRepository: VitalSignRepository,
  private val clinicalNoteRepository: ClinicalNoteRepository,
  private val orderEntryRepository: OrderEntryRepository,
  private val diagnosisRepository: DiagnosisRepository,
  private val prescriptionRepository: PrescriptionRepository,
  private val eventPublisher: DomainEventPublisher,
  private val searchIndexer: SearchIndexer,
  private val patientRecordRepository: PatientRecordRepository,
  private val allergyRepository: AllergyRepository,
  private val noteTemplateRepository: NoteTemplateRepository,
  private val diagnosisCatalogRepository: DiagnosisCatalogRepository,
  private val procedureRepository: ProcedureRepository,
  private val referralRepository: ReferralRepository,
  private val schedulingService: SchedulingService,
  private val meterRegistry: MeterRegistry,
  private val permissionEvaluator: SquamedPermissionEvaluator,
  private val medicationSafetyService: MedicationSafetyService, // 02.07 safety gate
) {
  @Transactional
  fun startEncounter(
    patientId: UUID,
    visitId: UUID? = null,
    doctorId: UUID? = null,
    departmentId: UUID? = null,
    reason: String? = null,
    chiefComplaint: String? = null,
    hospitalId: UUID,
    principal: SquamedPrincipal,
  ): EncounterView {
    // SAFETY L40 ABAC: the owner is the session doctor; a DOCTOR cannot assign the encounter to someone else.
    val ownerDoctorId =
      if (permissionEvaluator.hasRole(principal, "DOCTOR") && !permissionEvaluator.hasRole(principal, "SYSTEM_ADMIN")) {
        if (doctorId != null && doctorId != principal.userId) throw AccessDeniedException("ENCOUNTER_OWNER_MISMATCH")
        principal.userId
      } else {
        doctorId
      }
    val encounter =
      encounterRepository.save(
        Encounter(
          hospitalId = hospitalId,
          patientId = patientId,
          visitId = visitId,
          doctorId = ownerDoctorId,
          departmentId = departmentId,
          reason = reason,
          chiefComplaint = chiefComplaint,
          status = "in-progress",
          startedAt = Instant.now(),
        ),
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "encounter.started",
        occurredAt = Instant.now(),
        source = "encounter-service",
        entityType = "encounter",
        entityId = encounter.id,
      ),
    )
    searchIndexer.indexEncounter(encounter)
    return EncounterView.from(encounter)
  }

  @Transactional
  fun completeEncounter(
    encounterId: UUID,
    hospitalId: UUID?,
    principal: SquamedPrincipal,
  ): EncounterView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    encounter.status = "completed"
    encounter.endedAt = Instant.now()
    encounterRepository.save(encounter)
    searchIndexer.indexEncounter(encounter)
    return EncounterView.from(encounter)
  }

  @Transactional
  fun recordVitals(
    encounterId: UUID,
    dto: VitalSignRequestDto,
    hospitalId: UUID?,
  ): VitalSignView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    // CS-01 two-identifier confirm. No owner check: vitals are the nursing path.
    requireMrn(encounter.patientId, dto.patientMrn)
    val height = dto.heightCm
    val weight = dto.weightKg
    val bmi =
      if (height != null && weight != null && height.compareTo(BigDecimal.ZERO) > 0) {
        val meters = height.movePointLeft(2)
        weight.divide(meters.multiply(meters), 4, RoundingMode.HALF_UP).setScale(1, RoundingMode.HALF_UP)
      } else {
        null
      }
    val vitalSign =
      vitalSignRepository.save(
        VitalSign(
          encounterId = encounterId,
          patientId = encounter.patientId,
          recordedAt = dto.recordedAt ?: Instant.now(),
          recordedBy = dto.recordedBy,
          bloodPressureSystolic = dto.bloodPressureSystolic,
          bloodPressureDiastolic = dto.bloodPressureDiastolic,
          heartRate = dto.heartRate,
          respiratoryRate = dto.respiratoryRate,
          temperatureC = dto.temperatureC,
          spo2Percent = dto.spo2Percent,
          heightCm = height,
          weightKg = weight,
          bmi = bmi,
          painScore = dto.painScore,
          notes = dto.notes,
        ),
      )
    return VitalSignView.from(vitalSign)
  }

  @Transactional(readOnly = true)
  fun recentVitals(
    patientId: UUID,
    hospitalId: UUID?,
  ): List<VitalSignView> {
    // 00.04 tenant sweep (U5): the parent patient record carries the tenant; vitals rows do not.
    val patient = patientRecordRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    requireSameHospital(hospitalId, patient.hospitalId)
    return vitalSignRepository
      .findTop10ByPatientIdOrderByRecordedAtDesc(patientId)
      .map { VitalSignView.from(it) }
  }

  @Transactional
  fun addNote(
    encounterId: UUID,
    dto: ClinicalNoteRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): ClinicalNoteView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    val note =
      clinicalNoteRepository
        .save(
          ClinicalNote(
            encounterId = encounterId,
            patientId = encounter.patientId,
            noteType = dto.noteType ?: "progress",
            title = dto.title,
            subjective = dto.subjective,
            objective = dto.objective,
            assessment = dto.assessment,
            plan = dto.plan,
            isSigned = false,
          ),
        ).also { it.createdBy = principal.userId }
    return ClinicalNoteView.from(note)
  }

  @Transactional
  fun signNote(
    noteId: UUID,
    dto: SignNoteRequest,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): ClinicalNoteView {
    val note = clinicalNoteRepository.findById(noteId).orElseThrow { IllegalArgumentException("NOTE_NOT_FOUND") }
    // 00.04 tenant sweep (D7): notes carry no hospital_id — verify via the parent encounter.
    val encounter = findEncounter(note.encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    if (note.isSigned) throw IllegalStateException("NOTE_ALREADY_SIGNED")
    when (dto.method?.uppercase()) {
      "ESIGN" -> {
        val tx = dto.eSignTransactionId?.trim()
        if (tx.isNullOrBlank()) throw IllegalArgumentException("ESIGN_TRANSACTION_REQUIRED")
        note.signatureMethod = "ESIGN"
        note.esignTransactionId = tx
      }

      else -> {
        // E5 aging fallback: PIN is the live signing path while the external eSign contract is pending.
        val pin = dto.pin?.trim()
        if (pin.isNullOrBlank()) throw IllegalArgumentException("SIGNATURE_PIN_REQUIRED")
        note.signatureMethod = "PIN"
        // shortcut: unsalted SHA-256 — the PIN is a signing marker, not a login credential; salt only if E5 makes it auth-grade.
        note.signatureData =
          MessageDigest
            .getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
      }
    }
    note.isSigned = true
    note.signedAt = Instant.now()
    note.signedBy = principal.userId
    clinicalNoteRepository.save(note)
    return ClinicalNoteView.from(note)
  }

  @Transactional(readOnly = true)
  fun listNotes(
    encounterId: UUID,
    hospitalId: UUID?,
  ): List<ClinicalNoteView> {
    findEncounter(encounterId, hospitalId)
    return clinicalNoteRepository
      .findByEncounterId(encounterId)
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { ClinicalNoteView.from(it) }
  }

  @Transactional
  fun addDiagnosis(
    encounterId: UUID,
    dto: DiagnosisRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): DiagnosisView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    val system = dto.system ?: "ICD-10"
    val catalogEntry =
      diagnosisCatalogRepository.findBySystemIgnoreCaseAndCodeIgnoreCaseAndHospitalIdIsNullAndIsActiveTrue(
        system,
        dto.code.trim(),
      ) ?: throw IllegalArgumentException("DIAGNOSIS_CODE_UNKNOWN")
    val name = dto.name?.trim()?.takeUnless { it.isEmpty() } ?: catalogEntry.display
    val diagnosis =
      diagnosisRepository.save(
        Diagnosis(
          encounterId = encounterId,
          patientId = encounter.patientId,
          code = dto.code.trim(),
          system = catalogEntry.system,
          name = name,
          type = dto.type ?: "working",
          notes = dto.notes,
        ),
      )
    return DiagnosisView.from(diagnosis)
  }

  @Transactional(readOnly = true)
  fun listDiagnoses(
    encounterId: UUID,
    hospitalId: UUID?,
  ): List<DiagnosisView> {
    findEncounter(encounterId, hospitalId)
    return diagnosisRepository.findByEncounterIdAndIsActiveTrue(encounterId).map { DiagnosisView.from(it) }
  }

  @Transactional
  fun removeDiagnosis(
    encounterId: UUID,
    diagnosisId: UUID,
    patientMrn: String?,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ) {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, patientMrn)
    val diagnosis =
      diagnosisRepository
        .findById(diagnosisId)
        .orElseThrow { IllegalArgumentException("DIAGNOSIS_NOT_FOUND") }
    if (diagnosis.encounterId != encounterId) {
      throw IllegalArgumentException("DIAGNOSIS_NOT_FOUND")
    }
    // Dual review: removals deactivate so the clinical record keeps its history.
    diagnosis.isActive = false
    diagnosisRepository.save(diagnosis)
  }

  @Transactional
  fun placeOrder(
    encounterId: UUID,
    dto: OrderRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): OrderView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    val order =
      orderEntryRepository.save(
        OrderEntry(
          encounterId = encounterId,
          patientId = encounter.patientId,
          hospitalId = encounter.hospitalId ?: throw IllegalStateException("ENCOUNTER_HOSPITAL_MISSING"),
          orderType = dto.orderType,
          orderCode = dto.orderCode,
          orderName = dto.orderName,
          status = "pending",
          priority = dto.priority ?: "routine",
          instructions = dto.instructions,
          orderedBy = dto.orderedBy,
          orderedAt = dto.orderedAt ?: Instant.now(),
        ),
      )
    // 01.04 orders→tasks contract: the chunk-2 task engine consumes this outbox event.
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "encounter.order.placed",
        occurredAt = Instant.now(),
        source = "encounter-service",
        entityType = "order",
        entityId = order.id,
        hospitalId = order.hospitalId,
        payload =
          mapOf(
            "orderType" to order.orderType,
            "orderName" to order.orderName,
            "priority" to order.priority,
            "patientId" to order.patientId.toString(),
          ),
      ),
    )
    return OrderView.from(order)
  }

  @Transactional
  fun cancelOrder(
    orderId: UUID,
    encounterId: UUID?,
    patientMrn: String?,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): OrderView {
    val order = orderEntryRepository.findById(orderId).orElseThrow { IllegalArgumentException("ORDER_NOT_FOUND") }
    // Orders cancel only through their open owning encounter (cross-context orders are not visible here).
    if (order.encounterId == null || (encounterId != null && order.encounterId != encounterId)) {
      throw IllegalArgumentException("ORDER_NOT_FOUND")
    }
    val encounter = findEncounter(order.encounterId!!, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, patientMrn)
    order.status = "cancelled"
    orderEntryRepository.save(order)
    return OrderView.from(order)
  }

  @Transactional(readOnly = true)
  fun listOrders(
    encounterId: UUID,
    hospitalId: UUID?,
  ): List<OrderView> {
    findEncounter(encounterId, hospitalId)
    return orderEntryRepository.findByEncounterId(encounterId).map { OrderView.from(it) }
  }

  @Transactional
  fun prescribe(
    encounterId: UUID,
    dto: PrescriptionRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): PrescriptionView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    val drug = dto.drugName.trim().lowercase()
    val active = allergyRepository.findByPatientIdAndIsActiveTrue(encounter.patientId)
    // SAFETY L49: the NKA sentinel counts as documented; it never matches a drug.
    val nkaDocumented = active.any { it.allergen.trim().equals(Allergy.NKA_ALLERGEN, ignoreCase = true) }
    val realAllergies = active.filterNot { it.allergen.trim().equals(Allergy.NKA_ALLERGEN, ignoreCase = true) }
    if (realAllergies.isEmpty() && !nkaDocumented) throw IllegalStateException("ALLERGY_NOT_DOCUMENTED")
    // CS-02/CS-03: match = exact token OR >=4-char substring (unchanged); severity arbitrates the outcome.
    val matched =
      realAllergies.firstOrNull { allergy ->
        val allergen = allergy.allergen.trim().lowercase()
        allergen.isNotEmpty() &&
          (
            allergen == drug ||
              (allergen.length >= 4 && drug.length >= 4 && (drug.contains(allergen) || allergen.contains(drug)))
          )
      }
    var overrideReason: String? = null
    var allergyStatus = "checked"
    if (matched != null) {
      // Severity arbitrates: a block-severity match hard-fails with no override; all else needs a typed reason.
      if (matched.severity == "block") throw IllegalStateException("ALLERGY_BLOCK")
      val reason = dto.overrideReason?.trim()
      if (reason.isNullOrEmpty()) throw IllegalStateException("ALLERGY_WARN_REASON_REQUIRED")
      meterRegistry.counter("squamed.rx.allergy.warn.overrides").increment()
      overrideReason = reason
      allergyStatus = "warn_override"
    }
    // 02.07 medication safety engine: interactions/duplicate/dose/renal (allergy gate above).
    val safetyDrug = medicationSafetyService.resolveDrug(dto.drugId, dto.drugName)
    if (safetyDrug != null) {
      val screen = medicationSafetyService.screenForPrescribing(encounter.patientId, safetyDrug.id!!)
      val blockFindings = screen.findings.filter { it.severity == "BLOCK" }
      if (blockFindings.isNotEmpty()) {
        val reason = dto.overrideReason?.trim()
        if (reason.isNullOrEmpty()) throw IllegalStateException("MEDICATION_SAFETY_BLOCK")
        val top = blockFindings.first()
        medicationSafetyService.recordOverride(
          MedSafetyOverrideDto(encounter.patientId, safetyDrug.code, top.type, top.severity, reason),
          principal.userId,
          hospitalId,
        )
      }
    }
    val prescription =
      prescriptionRepository.save(
        Prescription(
          encounterId = encounterId,
          patientId = encounter.patientId,
          drugId = dto.drugId,
          drugName = dto.drugName,
          strength = dto.strength,
          form = dto.form,
          dose = dto.dose,
          frequency = dto.frequency,
          route = dto.route,
          duration = dto.duration,
          instructions = dto.instructions,
          quantity = dto.quantity,
          isPrn = dto.isPrn ?: false,
          status = "prescribed",
          prescribedBy = dto.prescribedBy,
          prescribedAt = dto.prescribedAt ?: Instant.now(),
          overrideReason = overrideReason,
          allergyStatus = allergyStatus,
        ),
      )
    return PrescriptionView.from(prescription)
  }

  @Transactional(readOnly = true)
  fun listPrescriptions(
    encounterId: UUID,
    hospitalId: UUID?,
  ): List<PrescriptionView> {
    findEncounter(encounterId, hospitalId)
    return prescriptionRepository.findByEncounterId(encounterId).map { PrescriptionView.from(it) }
  }

  @Transactional(readOnly = true)
  fun getEncounterDetail(
    encounterId: UUID,
    hospitalId: UUID?,
  ): EncounterDetailView {
    val encounter = findEncounter(encounterId, hospitalId)
    val latestVital = vitalSignRepository.findTopByPatientIdOrderByRecordedAtDesc(encounter.patientId)
    val notes =
      clinicalNoteRepository
        .findByEncounterId(encounterId)
        .sortedByDescending { it.createdAt ?: Instant.EPOCH }
        .map { ClinicalNoteView.from(it) }
    val diagnoses = diagnosisRepository.findByEncounterIdAndIsActiveTrue(encounterId).map { DiagnosisView.from(it) }
    val orders = orderEntryRepository.findByEncounterId(encounterId).map { OrderView.from(it) }
    val prescriptions = prescriptionRepository.findByEncounterId(encounterId).map { PrescriptionView.from(it) }
    val procedures = procedureRepository.findByEncounterIdOrderByCreatedAtDesc(encounterId).map { ProcedureView.from(it) }
    val referrals = referralRepository.findByEncounterIdOrderByCreatedAtDesc(encounterId).map { ReferralView.from(it) }
    return EncounterDetailView(
      encounter = EncounterView.from(encounter),
      latestVitals = latestVital?.let { VitalSignView.from(it) },
      notes = notes,
      diagnoses = diagnoses,
      orders = orders,
      prescriptions = prescriptions,
      procedures = procedures,
      referrals = referrals,
    )
  }

  /** 01.04 catalog-backed templates (global) for the /consult SOAP picker. */
  @Transactional(readOnly = true)
  fun noteTemplates(): List<NoteTemplateView> = noteTemplateRepository.findByIsActiveTrueOrderBySpecialtyAsc().map { NoteTemplateView.from(it) }

  @Transactional(readOnly = true)
  fun diagnosisCatalog(): List<DiagnosisCatalogEntryView> =
    diagnosisCatalogRepository.findByHospitalIdIsNullAndIsActiveTrueOrderBySystemAscCodeAsc().map { DiagnosisCatalogEntryView.from(it) }

  @Transactional
  fun addProcedure(
    encounterId: UUID,
    dto: ProcedureRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): ProcedureView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    val procedure =
      procedureRepository.save(
        ProcedureEntry(
          hospitalId =
            encounter.hospitalId
              ?: throw IllegalStateException("ENCOUNTER_HOSPITAL_MISSING"),
          encounterId = encounterId,
          patientId = encounter.patientId,
          procedureCode = dto.procedureCode,
          procedureName = dto.procedureName,
          status = dto.status ?: "planned",
          performedAt = dto.performedAt,
          performedBy = dto.performedBy,
          outcome = dto.outcome,
          notes = dto.notes,
        ),
      )
    return ProcedureView.from(procedure)
  }

  @Transactional(readOnly = true)
  fun listProcedures(
    encounterId: UUID,
    hospitalId: UUID?,
  ): List<ProcedureView> {
    findEncounter(encounterId, hospitalId)
    return procedureRepository.findByEncounterIdOrderByCreatedAtDesc(encounterId).map { ProcedureView.from(it) }
  }

  @Transactional(readOnly = true)
  fun listReferrals(
    encounterId: UUID,
    hospitalId: UUID?,
  ): List<ReferralView> {
    findEncounter(encounterId, hospitalId)
    return referralRepository.findByEncounterIdOrderByCreatedAtDesc(encounterId).map { ReferralView.from(it) }
  }

  @Transactional
  fun addReferral(
    encounterId: UUID,
    dto: ReferralRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): ReferralView {
    val encounter = findEncounter(encounterId, hospitalId)
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    if (dto.toDepartmentId == null && dto.toSpecialty.isNullOrBlank() && dto.targetFacility.isNullOrBlank()) {
      throw IllegalArgumentException("REFERRAL_TARGET_REQUIRED")
    }
    val referral =
      referralRepository.save(
        Referral(
          hospitalId =
            encounter.hospitalId
              ?: throw IllegalStateException("ENCOUNTER_HOSPITAL_MISSING"),
          encounterId = encounterId,
          patientId = encounter.patientId,
          fromDoctorId = principal.userId,
          toDepartmentId = dto.toDepartmentId,
          toSpecialty = dto.toSpecialty,
          targetFacility = dto.targetFacility,
          reason = dto.reason,
          urgency = dto.urgency ?: "routine",
          notes = dto.notes,
        ),
      )
    return ReferralView.from(referral)
  }

  /** 01.04 follow-up booking: delegates to the scheduling engine and stamps the encounter. */
  @Transactional
  fun bookFollowUp(
    encounterId: UUID,
    dto: FollowUpRequestDto,
    principal: SquamedPrincipal,
  ): EncounterView {
    val encounter = findEncounter(encounterId, principal.hospitalId)
    val appointment =
      schedulingService.book(
        principal,
        dto.slotId,
        SchedulingService.BookingRequest(
          patientId = encounter.patientId,
          appointmentType = "follow-up",
          reason = encounter.reason ?: "OPD follow-up",
          source = "encounter",
        ),
        dto.overrideNoShowLimit ?: false,
      )
    encounter.followUpAppointmentId = appointment.id
    encounterRepository.save(encounter)
    return EncounterView.from(encounter)
  }

  /** SAFETY L51: an amendment is a NEW unsigned note linked to the signed original via amends_note_id. */
  @Transactional
  fun addAmendment(
    noteId: UUID,
    dto: ClinicalNoteRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID?,
  ): ClinicalNoteView {
    val original = clinicalNoteRepository.findById(noteId).orElseThrow { IllegalArgumentException("NOTE_NOT_FOUND") }
    val encounter = findEncounter(original.encounterId, hospitalId)
    if (!original.isSigned) throw IllegalStateException("NOTE_NOT_SIGNED")
    requireOpenEncounter(encounter)
    requireOwner(encounter, principal)
    requireMrn(encounter.patientId, dto.patientMrn)
    val note =
      clinicalNoteRepository
        .save(
          ClinicalNote(
            encounterId = original.encounterId,
            patientId = original.patientId,
            noteType = dto.noteType ?: original.noteType,
            title = dto.title ?: original.title,
            subjective = dto.subjective,
            objective = dto.objective,
            assessment = dto.assessment,
            plan = dto.plan,
            isSigned = false,
            amendsNoteId = original.id,
          ),
        ).also { it.createdBy = principal.userId }
    return ClinicalNoteView.from(note)
  }

  /** Dual review: clinical writes land only on an open encounter. */
  private fun requireOpenEncounter(encounter: Encounter) {
    if (encounter.status != "in-progress") throw IllegalStateException("ENCOUNTER_NOT_ACTIVE")
  }

  /** SAFETY L40 ABAC: a doctor writes only to own encounters; SYSTEM_ADMIN bypasses; unclaimed encounters stay writable. */
  private fun requireOwner(
    encounter: Encounter,
    principal: SquamedPrincipal,
  ) {
    if (permissionEvaluator.hasRole(principal, "SYSTEM_ADMIN")) return
    if (!permissionEvaluator.hasRole(principal, "DOCTOR")) return
    val owner = encounter.doctorId ?: return
    if (owner != principal.userId) throw AccessDeniedException("ENCOUNTER_NOT_OWNED")
  }

  /** CS-01 two-identifier confirm: the request MRN must name the encounter's patient. */
  private fun requireMrn(
    patientId: UUID,
    patientMrn: String?,
  ) {
    val patient = patientRecordRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    if (patientMrn.isNullOrBlank() || !patientMrn.trim().equals(patient.mrn.trim(), ignoreCase = true)) {
      throw IllegalArgumentException("PATIENT_MISMATCH")
    }
  }

  private fun findEncounter(
    id: UUID,
    hospitalId: UUID?,
  ): Encounter {
    val encounter = encounterRepository.findById(id).orElseThrow { IllegalArgumentException("ENCOUNTER_NOT_FOUND") }
    requireSameHospital(hospitalId, encounter.hospitalId)
    return encounter
  }
}
