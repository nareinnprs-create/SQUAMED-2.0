package com.squamed.core.portal.api

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class PortalAccountLinkDto(
  val patientId: UUID,
  val userId: UUID,
  val abhaNumber: String? = null,
  val abhaAddress: String? = null,
)

data class PortalMeView(
  val accountId: UUID?,
  val patientId: UUID,
  val hospitalId: UUID,
  val mrn: String,
  val name: String,
  val abhaNumber: String?,
  val abhaAddress: String?,
)

data class PortalAppointmentView(
  val id: UUID?,
  val scheduledStart: Instant?,
  val status: String,
  val appointmentType: String?,
  val doctorId: UUID?,
)

data class PortalSlotView(
  val id: UUID?,
  val doctorId: UUID,
  val slotDate: LocalDate,
  val startTime: LocalTime,
  val status: String,
)

data class PortalBookDto(
  val slotId: UUID,
  val appointmentType: String,
  val reason: String? = null,
)

data class PortalReportView(
  val kind: String,
  val orderId: UUID,
  val orderNumber: String,
  val orderStatus: String,
  val reportId: UUID?,
  val reportVersion: Int?,
  val reportStatus: String?,
)

data class PortalPrescriptionView(
  val id: UUID?,
  val drugName: String,
  val dose: String?,
  val frequency: String?,
  val route: String?,
  val status: String,
  val createdAt: Instant?,
)

data class PortalBillView(
  val id: UUID?,
  val invoiceNumber: String,
  val totalQar: BigDecimal,
  val amountDueQar: BigDecimal,
  val status: String,
  val issuedAt: Instant?,
)

data class PortalDischargeView(
  val admissionId: UUID,
  val status: String,
  val signedAt: Instant?,
  val summaryText: String?,
  val followUpPlan: String?,
  val medicationsAtDischarge: String?,
  val instructions: String?,
)

data class PortalCarePlanView(
  val id: UUID?,
  val title: String,
  val problem: String?,
  val goal: String?,
  val interventions: String?,
  val status: String,
)

data class PortalNotificationView(
  val id: UUID?,
  val type: String,
  val title: String,
  val body: String?,
  val readAt: Instant?,
  val createdAt: Instant?,
)

data class PortalPushSubscribeDto(
  val endpoint: String,
  val p256dh: String,
  val auth: String,
)

data class PortalPushSubscriptionView(
  val id: UUID?,
  val endpoint: String,
  val createdAt: Instant?,
)
