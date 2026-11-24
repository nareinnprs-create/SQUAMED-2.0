package com.squamed.core.portal.service

import com.squamed.core.billing.api.PaymentCreateDto
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.billing.service.BillingService
import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.ipd.repository.DischargeSummaryRepository
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.laboratory.repository.LabReportRepository
import com.squamed.core.notification.repository.NotificationRepository
import com.squamed.core.nursing.repository.NursingCarePlanRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.portal.api.PortalAccountLinkDto
import com.squamed.core.portal.api.PortalAppointmentView
import com.squamed.core.portal.api.PortalBillView
import com.squamed.core.portal.api.PortalCarePlanView
import com.squamed.core.portal.api.PortalDischargeView
import com.squamed.core.portal.api.PortalMeView
import com.squamed.core.portal.api.PortalNotificationView
import com.squamed.core.portal.api.PortalPrescriptionView
import com.squamed.core.portal.api.PortalPushSubscriptionView
import com.squamed.core.portal.api.PortalReportView
import com.squamed.core.portal.api.PortalSlotView
import com.squamed.core.portal.domain.PortalAccount
import com.squamed.core.portal.domain.PushSubscription
import com.squamed.core.portal.repository.PortalAccountRepository
import com.squamed.core.portal.repository.PushSubscriptionRepository
import com.squamed.core.radiology.repository.RadOrderRepository
import com.squamed.core.radiology.repository.RadiologyReportRepository
import com.squamed.core.scheduling.repository.AppointmentRepository
import com.squamed.core.scheduling.repository.SessionSlotRepository
import com.squamed.core.scheduling.service.SchedulingService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

// 06.03: every portal read is scoped to the account's own patient id; nothing accepts a patient
// id from the caller.
@Service
class PortalService(
  private val accountRepository: PortalAccountRepository,
  private val pushRepository: PushSubscriptionRepository,
  private val patientRepository: PatientRecordRepository,
  private val appointmentRepository: AppointmentRepository,
  private val slotRepository: SessionSlotRepository,
  private val labOrderRepository: LabOrderRepository,
  private val labReportRepository: LabReportRepository,
  private val radOrderRepository: RadOrderRepository,
  private val radiologyReportRepository: RadiologyReportRepository,
  private val prescriptionRepository: PrescriptionRepository,
  private val invoiceRepository: InvoiceRepository,
  private val admissionRepository: AdmissionRepository,
  private val dischargeSummaryRepository: DischargeSummaryRepository,
  private val carePlanRepository: NursingCarePlanRepository,
  private val notificationRepository: NotificationRepository,
  private val billingService: BillingService,
  private val schedulingService: SchedulingService,
  @Value("\${squamed.portal.push.vapid-public:}") private val vapidPublicKey: String = "",
) {
  @Transactional
  fun linkAccount(
    dto: PortalAccountLinkDto,
    actorId: UUID?,
  ): PortalMeView {
    if (accountRepository.findByUserId(dto.userId) != null) throw IllegalStateException("PORTAL_ACCOUNT_EXISTS")
    val patient = patientRepository.findById(dto.patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    val hospitalId = patient.hospitalId ?: throw IllegalArgumentException("PATIENT_HOSPITAL_REQUIRED")
    val account =
      accountRepository.save(
        PortalAccount(
          userId = dto.userId,
          patientId = dto.patientId,
          hospitalId = hospitalId,
          abhaNumber = dto.abhaNumber,
          abhaAddress = dto.abhaAddress,
        ).also { it.createdBy = actorId },
      )
    return me(account)
  }

  @Transactional(readOnly = true)
  fun meFor(userId: UUID): PortalMeView = me(accountFor(userId))

  @Transactional(readOnly = true)
  fun appointments(userId: UUID): List<PortalAppointmentView> {
    val account = accountFor(userId)
    return appointmentRepository.findByPatientId(account.patientId).sortedByDescending { it.scheduledStart }.map {
      PortalAppointmentView(it.id, it.scheduledStart, it.status, it.appointmentType, it.doctorId)
    }
  }

  @Transactional(readOnly = true)
  fun slots(
    userId: UUID,
    doctorId: UUID,
    from: LocalDate?,
    to: LocalDate?,
  ): List<PortalSlotView> {
    val account = accountFor(userId)
    val start = from ?: LocalDate.now()
    val end = to ?: start.plusDays(13)
    return slotRepository
      .findByHospitalIdAndDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(account.hospitalId, doctorId, start, end)
      .filter { it.status == "open" }
      .map { PortalSlotView(it.id, it.doctorId, it.slotDate, it.startTime, it.status) }
  }

  @Transactional
  fun book(
    userId: UUID,
    principal: com.squamed.core.identity.security.SquamedPrincipal,
    slotId: UUID,
    appointmentType: String,
    reason: String?,
  ): PortalAppointmentView {
    val account = accountFor(userId)
    val appointment =
      schedulingService.book(
        principal,
        slotId,
        SchedulingService.BookingRequest(
          patientId = account.patientId,
          appointmentType = appointmentType,
          reason = reason,
          source = "portal",
        ),
        overrideNoShowLimit = false,
      )
    return PortalAppointmentView(appointment.id, appointment.scheduledStart, appointment.status, appointment.appointmentType, appointment.doctorId)
  }

  @Transactional(readOnly = true)
  fun reports(userId: UUID): List<PortalReportView> {
    val account = accountFor(userId)
    val lab =
      labOrderRepository.findByPatientIdOrderByOrderedAtDesc(account.patientId).map { order ->
        val report = order.id?.let { labReportRepository.findTopByLabOrderIdOrderByReportVersionDesc(it) }
        PortalReportView("LAB", order.id!!, order.orderNumber, order.status, report?.id, report?.reportVersion, if (report == null) null else "PUBLISHED")
      }
    val rad =
      radOrderRepository.findByPatientId(account.patientId).map { order ->
        val report = order.id?.let { radiologyReportRepository.findByOrderIdOrderByReportVersionDesc(it).firstOrNull() }
        PortalReportView("RADIOLOGY", order.id!!, order.accession, order.status, report?.id, report?.reportVersion, report?.status)
      }
    return lab + rad
  }

  @Transactional(readOnly = true)
  fun prescriptions(userId: UUID): List<PortalPrescriptionView> {
    val account = accountFor(userId)
    return prescriptionRepository.findByPatientId(account.patientId).sortedByDescending { it.createdAt }.map {
      PortalPrescriptionView(it.id, it.drugName, it.dose, it.frequency, it.route, it.status, it.createdAt)
    }
  }

  @Transactional(readOnly = true)
  fun bills(userId: UUID): List<PortalBillView> {
    val account = accountFor(userId)
    return invoiceRepository.findByPatientId(account.hospitalId, account.patientId).sortedByDescending { it.issuedAt }.map {
      PortalBillView(it.id, it.invoiceNumber, it.totalQar, it.amountDueQar, it.status, it.issuedAt)
    }
  }

  @Transactional
  fun pay(
    userId: UUID,
    invoiceId: UUID,
  ): PortalBillView {
    val account = accountFor(userId)
    val invoice =
      invoiceRepository.findById(invoiceId).orElse(null)
        ?: throw IllegalArgumentException("INVOICE_NOT_FOUND")
    if (invoice.patientId != account.patientId) throw AccessDeniedException("PORTAL_ACCESS_DENIED: invoice not yours")
    if (invoice.status == "paid" || invoice.amountDueQar.signum() <= 0) throw IllegalStateException("INVOICE_ALREADY_PAID")
    billingService.recordPayment(
      invoiceId,
      PaymentCreateDto(method = "online", amountQar = invoice.amountDueQar, reference = "portal"),
      null,
      account.hospitalId,
    )
    val updated = invoiceRepository.findById(invoiceId).orElseThrow { IllegalArgumentException("INVOICE_NOT_FOUND") }
    return PortalBillView(updated.id, updated.invoiceNumber, updated.totalQar, updated.amountDueQar, updated.status, updated.issuedAt)
  }

  @Transactional(readOnly = true)
  fun discharge(userId: UUID): List<PortalDischargeView> {
    val account = accountFor(userId)
    return admissionRepository
      .findByPatientId(account.patientId, account.hospitalId)
      .sortedByDescending { it.admittedAt }
      .mapNotNull { admission ->
        admission.id?.let { dischargeSummaryRepository.findByAdmissionId(it).orElse(null) }?.let { summary ->
          PortalDischargeView(
            admissionId = admission.id!!,
            status = summary.status,
            signedAt = summary.signedAt,
            summaryText = summary.summaryText,
            followUpPlan = summary.followUpPlan,
            medicationsAtDischarge = summary.medicationsAtDischarge,
            instructions = summary.instructions,
          )
        }
      }
  }

  @Transactional(readOnly = true)
  fun carePlans(userId: UUID): List<PortalCarePlanView> {
    val account = accountFor(userId)
    return carePlanRepository.findByPatientIdOrderByCreatedAtDesc(account.patientId, account.hospitalId).map {
      PortalCarePlanView(it.id, it.title, it.problem, it.goal, it.interventions, it.status)
    }
  }

  @Transactional(readOnly = true)
  fun notifications(userId: UUID): List<PortalNotificationView> =
    notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).map {
      PortalNotificationView(it.id, it.type, it.title, it.body, it.readAt, it.createdAt)
    }

  @Transactional
  fun readNotification(
    userId: UUID,
    notificationId: UUID,
  ): PortalNotificationView {
    val notification = notificationRepository.findById(notificationId).orElseThrow { IllegalArgumentException("NOTIFICATION_NOT_FOUND") }
    if (notification.recipientId != userId) throw AccessDeniedException("PORTAL_ACCESS_DENIED: notification not yours")
    if (notification.readAt == null) {
      notification.readAt = java.time.Instant.now()
      notificationRepository.save(notification)
    }
    return PortalNotificationView(notification.id, notification.type, notification.title, notification.body, notification.readAt, notification.createdAt)
  }

  @Transactional
  fun subscribePush(
    userId: UUID,
    endpoint: String,
    p256dh: String,
    auth: String,
  ): PortalPushSubscriptionView {
    if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) throw IllegalArgumentException("PUSH_SUBSCRIPTION_INVALID")
    val existing = pushRepository.findByEndpoint(endpoint)
    val subscription =
      existing?.also {
        if (it.userId != userId) throw AccessDeniedException("PORTAL_ACCESS_DENIED: endpoint owned by another user")
        it.p256dh = p256dh
        it.auth = auth
        it.updatedBy = userId
      } ?: PushSubscription(userId = userId, endpoint = endpoint, p256dh = p256dh, auth = auth).also { it.createdBy = userId }
    val saved = pushRepository.save(subscription)
    return PortalPushSubscriptionView(saved.id, saved.endpoint, saved.createdAt)
  }

  @Transactional(readOnly = true)
  fun pushSubscriptions(userId: UUID): List<PortalPushSubscriptionView> =
    pushRepository.findByUserIdOrderByCreatedAtDesc(userId).map { PortalPushSubscriptionView(it.id, it.endpoint, it.createdAt) }

  /**
   * Web Push delivery needs VAPID keys + an HTTPS origin. Until they are configured this endpoint
   * reports the deployment gap instead of pretending delivery is live.
   */
  @Transactional(readOnly = true)
  fun pushStatus(userId: UUID): Map<String, Any?> {
    accountFor(userId)
    val subscriptions = pushRepository.findByUserIdOrderByCreatedAtDesc(userId).size
    return mapOf("configured" to vapidPublicKey.isNotBlank(), "subscriptions" to subscriptions)
  }

  private fun accountFor(userId: UUID): PortalAccount {
    val account = accountRepository.findByUserId(userId) ?: throw AccessDeniedException("PORTAL_ACCOUNT_REQUIRED")
    if (account.status != "ACTIVE") throw AccessDeniedException("PORTAL_ACCOUNT_INACTIVE")
    return account
  }

  private fun me(account: PortalAccount): PortalMeView {
    val patient = patientRepository.findById(account.patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    return PortalMeView(
      accountId = account.id,
      patientId = account.patientId,
      hospitalId = account.hospitalId,
      mrn = patient.mrn,
      name = listOfNotNull(patient.firstName, patient.lastName).joinToString(" "),
      abhaNumber = account.abhaNumber,
      abhaAddress = account.abhaAddress,
    )
  }
}
