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
    )

  fun all(): List<Entry> = entries

  /** Per-type topic routing (`squamed.$wireName` for task and notification prefixes), null = domain topic only. */
  fun perTypeTopic(wireName: String): String? =
    when {
      wireName.startsWith("task.") || wireName.startsWith("notification.") -> "squamed.$wireName"
      else -> null
    }
}
