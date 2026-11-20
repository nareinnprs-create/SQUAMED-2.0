package com.squamed.core.patient360.service

import com.squamed.core.billing.api.ChargeView
import com.squamed.core.billing.api.InvoiceView
import com.squamed.core.billing.repository.ChargeRepository
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.consent.api.ConsentView
import com.squamed.core.consent.repository.ConsentChannelOptInRepository
import com.squamed.core.consent.repository.ConsentRepository
import com.squamed.core.encounter.api.DiagnosisView
import com.squamed.core.encounter.api.OrderView
import com.squamed.core.encounter.api.PrescriptionView
import com.squamed.core.encounter.api.VitalSignView
import com.squamed.core.encounter.repository.ClinicalNoteRepository
import com.squamed.core.encounter.repository.DiagnosisRepository
import com.squamed.core.encounter.repository.EncounterRepository
import com.squamed.core.encounter.repository.OrderEntryRepository
import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.encounter.repository.VitalSignRepository
import com.squamed.core.laboratory.api.LabOrderView
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.patient.api.PatientDocumentDto
import com.squamed.core.patient.repository.PatientDocumentRepository
import com.squamed.core.patient.service.PatientService
import com.squamed.core.patient360.api.Encounter360View
import com.squamed.core.patient360.api.Patient360View
import com.squamed.core.patient360.api.TaskSummaryView
import com.squamed.core.pharmacy.api.DispensationView
import com.squamed.core.pharmacy.repository.DispensationRepository
import com.squamed.core.receptionqueue.api.VisitDto
import com.squamed.core.receptionqueue.repository.VisitRegistrationRepository
import com.squamed.core.scheduling.api.AppointmentView
import com.squamed.core.scheduling.repository.AppointmentRepository
import com.squamed.core.task.api.TaskView
import com.squamed.core.task.repository.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class Patient360Service(
  private val patientService: PatientService,
  private val encounterRepository: EncounterRepository,
  private val vitalSignRepository: VitalSignRepository,
  private val diagnosisRepository: DiagnosisRepository,
  private val prescriptionRepository: PrescriptionRepository,
  private val orderEntryRepository: OrderEntryRepository,
  private val clinicalNoteRepository: ClinicalNoteRepository,
  private val labOrderRepository: LabOrderRepository,
  private val dispensationRepository: DispensationRepository,
  private val chargeRepository: ChargeRepository,
  private val invoiceRepository: InvoiceRepository,
  private val appointmentRepository: AppointmentRepository,
  private val visitRegistrationRepository: VisitRegistrationRepository,
  private val taskRepository: TaskRepository,
  private val consentRepository: ConsentRepository,
  private val consentOptInRepository: ConsentChannelOptInRepository,
  private val documentRepository: PatientDocumentRepository,
  private val radiologyService: com.squamed.core.radiology.service.RadiologyService, // 02.05 imaging attach
) {
  @Transactional(readOnly = true)
  fun getPatient360(
    patientId: UUID,
    hospitalId: UUID?,
  ): Patient360View {
    // 00.04 tenant sweep: the parent patient's hospital gates every aggregate built below
    // (no patient-package edit needed — PatientService already exposes getHospitalId).
    requireSameHospital(hospitalId, patientService.getHospitalId(patientId))
    val demographics = patientService.getDetail(patientId)

    val encounters =
      encounterRepository
        .findByPatientId(patientId)
        .sortedByDescending { it.startedAt ?: it.createdAt ?: Instant.EPOCH }

    val encounterViews =
      encounters
        .take(20)
        .map { encounter ->
          val encounterId = encounter.id ?: return@map null
          Encounter360View.from(
            entity = encounter,
            diagnoses = diagnosisRepository.findByEncounterId(encounterId).map { DiagnosisView.from(it) },
            prescriptions = prescriptionRepository.findByEncounterId(encounterId).map { PrescriptionView.from(it) },
            orders = orderEntryRepository.findByEncounterId(encounterId).map { OrderView.from(it) },
            noteCount = clinicalNoteRepository.findByEncounterId(encounterId).size,
          )
        }.filterNotNull()

    val labOrders = labOrderRepository.findByPatientIdOrderByOrderedAtDesc(patientId)

    val invoices =
      invoiceRepository
        .findByPatientId(null, patientId)
        .sortedByDescending { it.issuedAt }

    val tasks =
      (
        taskRepository.findByRelatedTypeAndRelatedId("PATIENT", patientId) +
          taskRepository.findByRelatedId(patientId)
      ).distinctBy { it.id }
        .sortedByDescending { it.createdAt ?: Instant.EPOCH }

    val consents =
      consentRepository.findByPatientId(patientId).map { consent ->
        ConsentView.from(
          consent,
          consentOptInRepository.findByConsentId(consent.id ?: throw IllegalStateException("CONSENT_ID_MISSING")),
          Instant.now(),
        )
      }

    return Patient360View(
      demographics = demographics,
      latestVitals = vitalSignRepository.findTopByPatientIdOrderByRecordedAtDesc(patientId)?.let { VitalSignView.from(it) },
      recentVitals = vitalSignRepository.findTop10ByPatientIdOrderByRecordedAtDesc(patientId).map { VitalSignView.from(it) },
      encounters = encounterViews,
      totalEncounters = encounters.size,
      allergies = patientService.listAllergies(patientId),
      notes = patientService.listNotes(patientId),
      medicalHistory = patientService.listMedicalHistory(patientId),
      appointments =
        appointmentRepository
          .findByPatientId(patientId)
          .sortedByDescending { it.scheduledStart }
          .take(20)
          .map { AppointmentView.from(it) },
      visits =
        visitRegistrationRepository
          .findByPatientId(patientId)
          .sortedByDescending { it.registrationStartedAt }
          .take(20)
          .map { VisitDto.from(it) },
      labOrders = labOrders.take(20).map { LabOrderView.from(it) },
      totalLabOrders = labOrders.size,
      medications =
        dispensationRepository
          .findByPatientId(null, patientId)
          .sortedByDescending { it.dispensedAt ?: it.createdAt ?: Instant.EPOCH }
          .take(20)
          .map { DispensationView.from(it) },
      charges =
        chargeRepository
          .findByPatientIdOrderByPostedAtDesc(null, patientId)
          .take(20)
          .map { ChargeView.from(it) },
      invoices = invoices.take(20).map { InvoiceView.from(it) },
      totalInvoices = invoices.size,
      tasks = tasks.map { TaskView.from(it) },
      taskSummary =
        TaskSummaryView(
          open = tasks.count { it.status != "completed" && it.status != "cancelled" },
          completed = tasks.count { it.status == "completed" },
        ),
      consents = consents,
      documents = documentRepository.findByPatientIdOrderByCreatedAtDesc(patientId).map { PatientDocumentDto.from(it) },
      imaging = radiologyService.imagingForPatient(patientId),
    )
  }
}
