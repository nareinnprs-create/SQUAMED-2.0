package com.squamed.core.events

/**
 * Event catalog (chunk-00 EVENTS v1): the mapping table between the chunk's PascalCase event
 * names and the lowercase dotted wire names used on `squamed.domain-events`. PLANNED entries land
 * with their owning section (00.11 task.escalated, 00.12 consent.*).
 */
object EventCatalog {
  enum class Status { EMITTED, PLANNED }

  data class Entry(
    val name: String,
    val wireName: String,
    val status: Status,
    val description: String,
  )

  val entries: List<Entry> =
    listOf(
      Entry("PatientRegistered", "patient.created", Status.EMITTED, "New patient record registered."),
      Entry("PatientUpdated", "patient.updated", Status.EMITTED, "Patient demographics updated."),
      Entry("PatientMerged", "patient.merged", Status.EMITTED, "Two patient records merged (EMPI)."),
      Entry("PatientUnmerged", "patient.unmerged", Status.EMITTED, "A merge was reversed."),
      Entry("TaskCreated", "task.created", Status.EMITTED, "Task created in the task engine."),
      Entry("TaskAssigned", "task.assigned", Status.EMITTED, "Task assigned to a staff user."),
      Entry("TaskCompleted", "task.completed", Status.EMITTED, "Task reached a completed status."),
      Entry("TaskEscalated", "task.escalated", Status.EMITTED, "SLA breach or manual escalation (00.11 task engine)."),
      Entry("ConsentCaptured", "consent.captured", Status.EMITTED, "Consent captured (00.12 consent engine)."),
      Entry("ConsentWithdrawn", "consent.withdrawn", Status.EMITTED, "Consent withdrawn (00.12 consent engine)."),
      Entry("ConfigChanged", "config.changed", Status.EMITTED, "Hospital config document changed."),
      Entry("AuditChainBroken", "audit.chain_broken", Status.EMITTED, "Audit hash-chain breach detected (00.10 verify)."),
      Entry("NotificationDispatched", "notification.dispatched", Status.EMITTED, "Notification handed to its channel bridge."),
      Entry("AppointmentCreated", "appointment.created", Status.EMITTED, "Appointment booked into a session slot (01.01 scheduling engine)."),
      Entry("AppointmentCancelled", "appointment.cancelled", Status.EMITTED, "Appointment cancelled; its session slot is released (01.01)."),
      Entry("AppointmentRescheduled", "appointment.rescheduled", Status.EMITTED, "Appointment moved to a different session slot (01.01)."),
      Entry(
        "AppointmentStatusChanged",
        "appointment.status_changed",
        Status.EMITTED,
        "Appointment lifecycle status changed (pre-catalog ad-hoc emission, cataloged 01.01).",
      ),
      Entry("QueueCalled", "queue.called", Status.EMITTED, "Token called to a counter; displays and announcements consume it (01.02)."),
      Entry(
        "AnnouncementEnqueued",
        "announcement.enqueued",
        Status.EMITTED,
        "TTS announcement queued for displays: texts per language, repeat count, zone and volume (01.07).",
      ),
      Entry("QueueTransferred", "queue.transferred", Status.EMITTED, "Token moved to another queue with a fresh token number (01.02)."),
      Entry("QueueBumped", "queue.bumped", Status.EMITTED, "Entry bumped to emergency priority; broadcast to displays over WS (01.02)."),
      Entry(
        "CriticalQueueEvent",
        "queue.urgent_arrival",
        Status.EMITTED,
        "Walk-in tagged red at triage-lite; the urgent arrival escalates the live queue (01.09).",
      ),
      Entry(
        "EncounterOrderPlaced",
        "encounter.order.placed",
        Status.EMITTED,
        "OPD order placed from a consultation; the task engine consumes it in chunk 2 (01.04).",
      ),
      Entry(
        "ChargeablePerformed",
        "billing.chargeable_performed",
        Status.EMITTED,
        "A billable service was performed (lab order released 02.08, imaging study completed 02.05); chunk-3 charge capture consumes it.",
      ),
      Entry(
        "StudyCompleted",
        "radiology.study_completed",
        Status.EMITTED,
        "Imaging study acquired and completed in PACS with its Orthanc refs (02.05).",
      ),
      Entry(
        "RadiologyReportSigned",
        "radiology.report_signed",
        Status.EMITTED,
        "Radiologist signed a structured imaging report (02.05).",
      ),
      Entry("AdmissionCreated", "admission.created", Status.EMITTED, "An inpatient admission record was created (04.01)."),
      Entry("PatientAdmitted", "admission.admitted", Status.EMITTED, "Patient admitted with a bed assigned (04.01)."),
      Entry("BedAssigned", "admission.bed_assigned", Status.EMITTED, "Bed assigned at admission or transfer (04.01)."),
      Entry("PatientTransferred", "admission.transferred", Status.EMITTED, "Inpatient transferred to another bed/ward with a trace (04.01)."),
      Entry("DischargePlanned", "admission.discharge_planned", Status.EMITTED, "Expected discharge date set (04.01)."),
      Entry("PatientDischarged", "admission.discharged", Status.EMITTED, "Admission discharged after the checklist (04.01)."),
      Entry("DischargeSummarySigned", "admission.summary_signed", Status.EMITTED, "Coded discharge summary signed (04.01)."),
      Entry("HousekeepingTaskCreated", "housekeeping.task_created", Status.EMITTED, "Bed released for terminal cleaning (04.01)."),
      Entry("DoseScheduled", "nursing.dose_scheduled", Status.EMITTED, "eMAR dose scheduled from an active prescription (04.02)."),
      Entry("DoseAdministered", "nursing.dose_administered", Status.EMITTED, "eMAR dose administered after barcode verification (04.02)."),
      Entry("DoseOmitted", "nursing.dose_omitted", Status.EMITTED, "eMAR dose omitted with a code + reason, clinician notified (04.02)."),
      Entry("CarePlanUpdated", "nursing.careplan_updated", Status.EMITTED, "Care plan created/applied or its status changed (04.03)."),
      Entry("ThresholdAlertRaised", "icu.critical_alert", Status.EMITTED, "ICU threshold/critical alert raised for routing (04.05)."),
      Entry("CrossmatchRequested", "bloodbank.crossmatch_requested", Status.EMITTED, "Blood unit crossmatch requested for a patient (04.07)."),
      Entry("CrossmatchIssued", "bloodbank.crossmatch_issued", Status.EMITTED, "Compatible unit issued after the bedside two-identifier check (04.07)."),
      Entry("TransfusionReactionReported", "bloodbank.transfusion_reaction", Status.EMITTED, "Transfusion reaction reported with severity (04.07)."),
      Entry("AiActionProposed", "ai.action_proposed", Status.EMITTED, "Command-bar action drafted for human approval (05.03)."),
      Entry("AiActionApproved", "ai.action_approved", Status.EMITTED, "Drafted action approved and executed through the platform API (05.03)."),
      Entry("AiActionRejected", "ai.action_rejected", Status.EMITTED, "Drafted action rejected by a human reviewer (05.03)."),
      Entry("AiActionExpired", "ai.action_expired", Status.EMITTED, "Drafted action expired before a decision (05.03)."),
      Entry("AiIncidentReported", "ai.incident_reported", Status.EMITTED, "AI incident reported to the governance register (05.07)."),
      Entry("AttentionItemRaised", "ai.attention_item_raised", Status.EMITTED, "Intelligence-rail attention item raised (05.04)."),
      Entry("AttentionItemCleared", "ai.attention_item_cleared", Status.EMITTED, "Attention item cleared or dismissed (05.04)."),
      Entry(
        "RecordSharedConsented",
        "record.shared_consented",
        Status.EMITTED,
        "Patient record shared across group hospitals with a consent artifact (06.01).",
      ),
      Entry("ReferralCreated", "group.referral_created", Status.EMITTED, "Cross-hospital referral created inside a group (06.01)."),
      Entry("ReferralAccepted", "group.referral_accepted", Status.EMITTED, "Cross-hospital referral accepted by the receiving hospital (06.01)."),
      Entry("RosterPublished", "hr.roster_published", Status.EMITTED, "Workforce roster published for a week (06.04)."),
      Entry("CredentialExpiring", "hr.credential_expiring", Status.EMITTED, "Credential inside the renewal window with a renewal task (06.04)."),
      Entry("POIssued", "ops.po_issued", Status.EMITTED, "Purchase order issued from an approved requisition (06.05)."),
      Entry("GoodsReceived", "ops.goods_received", Status.EMITTED, "Goods receipt booked and stock incremented (06.05)."),
      Entry("SterilizationCycleCompleted", "ops.sterilization_completed", Status.EMITTED, "CSSD sterilization cycle completed with its result (06.05)."),
      Entry("WorkOrderCreated", "ops.work_order_created", Status.EMITTED, "Facilities work order opened for an asset (06.05)."),
      Entry("WorkOrderCompleted", "ops.work_order_completed", Status.EMITTED, "Facilities work order completed with downtime (06.05)."),
      Entry("EDEncounterClosed", "ops.ed_encounter_closed", Status.EMITTED, "ED encounter closed with a disposition (06.05)."),
      Entry("ErasureCompleted", "governance.erasure_completed", Status.EMITTED, "Non-clinical data erased under a retention policy (06.08)."),
      Entry("TraumaTeamActivated", "ops.trauma_team_activated", Status.EMITTED, "ED trauma team activated for an encounter (07.05)."),
      Entry("MassCasualtyDeclared", "ops.mass_casualty_declared", Status.EMITTED, "Mass-casualty mode declared by the ED (07.05)."),
    )

  fun all(): List<Entry> = entries

  /** Per-type topic routing (`squamed.$wireName` for task and notification prefixes), null = domain topic only. */
  fun perTypeTopic(wireName: String): String? =
    when {
      wireName.startsWith("task.") || wireName.startsWith("notification.") -> "squamed.$wireName"
      else -> null
    }
}
