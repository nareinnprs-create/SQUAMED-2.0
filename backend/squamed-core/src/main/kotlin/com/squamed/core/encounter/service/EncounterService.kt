package com.squamed.core.encounter.service

import com.squamed.core.encounter.api.ClinicalNoteRequestDto
import com.squamed.core.encounter.api.ClinicalNoteView
import com.squamed.core.encounter.api.DiagnosisRequestDto
import com.squamed.core.encounter.api.DiagnosisView
import com.squamed.core.encounter.api.EncounterDetailView
import com.squamed.core.encounter.api.EncounterView
import com.squamed.core.encounter.api.OrderRequestDto
import com.squamed.core.encounter.api.OrderView
import com.squamed.core.encounter.api.PrescriptionRequestDto
import com.squamed.core.encounter.api.PrescriptionView
import com.squamed.core.encounter.api.VitalSignRequestDto
import com.squamed.core.encounter.api.VitalSignView
import com.squamed.core.encounter.domain.ClinicalNote
import com.squamed.core.encounter.domain.Diagnosis
import com.squamed.core.encounter.domain.Encounter
import com.squamed.core.encounter.domain.OrderEntry
import com.squamed.core.encounter.domain.Prescription
import com.squamed.core.encounter.domain.VitalSign
import com.squamed.core.encounter.repository.ClinicalNoteRepository
import com.squamed.core.encounter.repository.DiagnosisRepository
import com.squamed.core.encounter.repository.EncounterRepository
import com.squamed.core.encounter.repository.OrderEntryRepository
import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.encounter.repository.VitalSignRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.search.SearchIndexer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
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
  ): EncounterView {
    val encounter =
      encounterRepository.save(
        Encounter(
          hospitalId = hospitalId,
          patientId = patientId,
          visitId = visitId,
          doctorId = doctorId,
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
  fun completeEncounter(encounterId: UUID): EncounterView {
    val encounter = findEncounter(encounterId)
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
  ): VitalSignView {
    val encounter = findEncounter(encounterId)
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
  fun recentVitals(patientId: UUID): List<VitalSignView> =
    vitalSignRepository
      .findTop10ByPatientIdOrderByRecordedAtDesc(patientId)
      .map { VitalSignView.from(it) }

  @Transactional
  fun addNote(
    encounterId: UUID,
    dto: ClinicalNoteRequestDto,
    authorId: UUID? = null,
  ): ClinicalNoteView {
    val encounter = findEncounter(encounterId)
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
        ).also { it.createdBy = authorId }
    return ClinicalNoteView.from(note)
  }

  @Transactional
  fun signNote(
    noteId: UUID,
    signerId: UUID,
  ): ClinicalNoteView {
    val note = clinicalNoteRepository.findById(noteId).orElseThrow { IllegalArgumentException("NOTE_NOT_FOUND") }
    note.isSigned = true
    note.signedAt = Instant.now()
    note.signedBy = signerId
    clinicalNoteRepository.save(note)
    return ClinicalNoteView.from(note)
  }

  @Transactional(readOnly = true)
  fun listNotes(encounterId: UUID): List<ClinicalNoteView> =
    clinicalNoteRepository
      .findByEncounterId(encounterId)
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { ClinicalNoteView.from(it) }

  @Transactional
  fun addDiagnosis(
    encounterId: UUID,
    dto: DiagnosisRequestDto,
  ): DiagnosisView {
    val encounter = findEncounter(encounterId)
    val diagnosis =
      diagnosisRepository.save(
        Diagnosis(
          encounterId = encounterId,
          patientId = encounter.patientId,
          code = dto.code,
          system = dto.system ?: "ICD-10",
          name = dto.name,
          type = dto.type ?: "working",
          notes = dto.notes,
        ),
      )
    return DiagnosisView.from(diagnosis)
  }

  @Transactional(readOnly = true)
  fun listDiagnoses(encounterId: UUID): List<DiagnosisView> = diagnosisRepository.findByEncounterId(encounterId).map { DiagnosisView.from(it) }

  @Transactional
  fun removeDiagnosis(
    encounterId: UUID,
    diagnosisId: UUID,
  ) {
    val diagnosis =
      diagnosisRepository
        .findById(diagnosisId)
        .orElseThrow { IllegalArgumentException("DIAGNOSIS_NOT_FOUND") }
    if (diagnosis.encounterId != encounterId) {
      throw IllegalArgumentException("DIAGNOSIS_NOT_FOUND")
    }
    diagnosisRepository.delete(diagnosis)
  }

  @Transactional
  fun placeOrder(
    encounterId: UUID,
    dto: OrderRequestDto,
  ): OrderView {
    val encounter = findEncounter(encounterId)
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
    return OrderView.from(order)
  }

  @Transactional
  fun cancelOrder(
    orderId: UUID,
    reason: String? = null,
  ): OrderView {
    val order = orderEntryRepository.findById(orderId).orElseThrow { IllegalArgumentException("ORDER_NOT_FOUND") }
    order.status = "cancelled"
    orderEntryRepository.save(order)
    return OrderView.from(order)
  }

  @Transactional(readOnly = true)
  fun listOrders(encounterId: UUID): List<OrderView> = orderEntryRepository.findByEncounterId(encounterId).map { OrderView.from(it) }

  @Transactional
  fun prescribe(
    encounterId: UUID,
    dto: PrescriptionRequestDto,
  ): PrescriptionView {
    val encounter = findEncounter(encounterId)
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
        ),
      )
    return PrescriptionView.from(prescription)
  }

  @Transactional(readOnly = true)
  fun listPrescriptions(encounterId: UUID): List<PrescriptionView> = prescriptionRepository.findByEncounterId(encounterId).map { PrescriptionView.from(it) }

  @Transactional(readOnly = true)
  fun getEncounterDetail(encounterId: UUID): EncounterDetailView {
    val encounter = findEncounter(encounterId)
    val latestVital = vitalSignRepository.findTopByPatientIdOrderByRecordedAtDesc(encounter.patientId)
    val notes =
      clinicalNoteRepository
        .findByEncounterId(encounterId)
        .sortedByDescending { it.createdAt ?: Instant.EPOCH }
        .map { ClinicalNoteView.from(it) }
    val diagnoses = diagnosisRepository.findByEncounterId(encounterId).map { DiagnosisView.from(it) }
    val orders = orderEntryRepository.findByEncounterId(encounterId).map { OrderView.from(it) }
    val prescriptions = prescriptionRepository.findByEncounterId(encounterId).map { PrescriptionView.from(it) }
    return EncounterDetailView(
      encounter = EncounterView.from(encounter),
      latestVitals = latestVital?.let { VitalSignView.from(it) },
      notes = notes,
      diagnoses = diagnoses,
      orders = orders,
      prescriptions = prescriptions,
    )
  }

  private fun findEncounter(id: UUID): Encounter = encounterRepository.findById(id).orElseThrow { IllegalArgumentException("ENCOUNTER_NOT_FOUND") }
}
