package com.squamed.core.patient360.api

import com.squamed.core.billing.api.ChargeView
import com.squamed.core.billing.api.InvoiceView
import com.squamed.core.consent.api.ConsentView
import com.squamed.core.encounter.api.DiagnosisView
import com.squamed.core.encounter.api.OrderView
import com.squamed.core.encounter.api.PrescriptionView
import com.squamed.core.encounter.api.VitalSignView
import com.squamed.core.encounter.domain.Encounter
import com.squamed.core.laboratory.api.LabOrderView
import com.squamed.core.patient.api.AllergyDto
import com.squamed.core.patient.api.MedicalHistoryDto
import com.squamed.core.patient.api.NoteDto
import com.squamed.core.patient.api.PatientDetailDto
import com.squamed.core.patient.api.PatientDocumentDto
import com.squamed.core.pharmacy.api.DispensationView
import com.squamed.core.receptionqueue.api.VisitDto
import com.squamed.core.scheduling.api.AppointmentView
import com.squamed.core.task.api.TaskView
import java.time.Instant
import java.util.UUID

data class Encounter360View(
  val encounterId: UUID?,
  val date: Instant?,
  val type: String,
  val status: String,
  val reason: String?,
  val diagnoses: List<DiagnosisView> = emptyList(),
  val prescriptions: List<PrescriptionView> = emptyList(),
  val orders: List<OrderView> = emptyList(),
  val noteCount: Int = 0,
) {
  companion object {
    fun from(
      entity: Encounter,
      diagnoses: List<DiagnosisView>,
      prescriptions: List<PrescriptionView>,
      orders: List<OrderView>,
      noteCount: Int,
    ) = Encounter360View(
      encounterId = entity.id,
      date = entity.startedAt ?: entity.createdAt,
      type = entity.encounterType,
      status = entity.status,
      reason = entity.reason,
      diagnoses = diagnoses,
      prescriptions = prescriptions,
      orders = orders,
      noteCount = noteCount,
    )
  }
}

data class TaskSummaryView(
  val open: Int,
  val completed: Int,
)

data class Patient360View(
  val demographics: PatientDetailDto,
  val latestVitals: VitalSignView? = null,
  val recentVitals: List<VitalSignView> = emptyList(),
  val encounters: List<Encounter360View> = emptyList(),
  val totalEncounters: Int = 0,
  val allergies: List<AllergyDto> = emptyList(),
  val notes: List<NoteDto> = emptyList(),
  val medicalHistory: List<MedicalHistoryDto> = emptyList(),
  val appointments: List<AppointmentView> = emptyList(),
  val visits: List<VisitDto> = emptyList(),
  val labOrders: List<LabOrderView> = emptyList(),
  val totalLabOrders: Int = 0,
  val medications: List<DispensationView> = emptyList(),
  val charges: List<ChargeView> = emptyList(),
  val invoices: List<InvoiceView> = emptyList(),
  val totalInvoices: Int = 0,
  val tasks: List<TaskView> = emptyList(),
  val taskSummary: TaskSummaryView = TaskSummaryView(open = 0, completed = 0),
  val consents: List<ConsentView> = emptyList(),
  val documents: List<PatientDocumentDto> = emptyList(),
)
