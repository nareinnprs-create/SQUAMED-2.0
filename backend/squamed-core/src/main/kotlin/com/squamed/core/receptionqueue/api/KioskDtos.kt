package com.squamed.core.receptionqueue.api

import java.time.Instant
import java.util.UUID

/** 01.08 kiosk-lite: self-service check-in request. */
data class KioskCheckInRequest(
  /** abha | abha_address | mobile | qr */
  val identifierType: String,
  /** identifier value, or the full QR payload (`SQM:{hid}:{queueType}:{token}:{date}`) for qr mode. */
  val identifier: String,
  /** optional queue override; defaults to the appointment's department queue ("consult"). */
  val queueType: String? = null,
)

/** Token slip returned to the kiosk UI for on-screen display + QR re-print. */
data class KioskSlipDto(
  val patientId: UUID,
  val patientMrn: String?,
  val patientName: String?,
  val appointmentId: UUID?,
  val appointmentStart: Instant?,
  val departmentName: String?,
  val token: QueueEntryView,
  val printedAt: Instant,
)
