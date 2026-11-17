package com.squamed.core.receptionqueue.api

import com.squamed.core.receptionqueue.domain.AnnouncementRule
import com.squamed.core.receptionqueue.domain.ConsultReceipt
import com.squamed.core.receptionqueue.domain.DisplayZone
import com.squamed.core.receptionqueue.domain.QueueEntry
import com.squamed.core.receptionqueue.domain.ServicePoint
import com.squamed.core.receptionqueue.domain.VisitPass
import com.squamed.core.receptionqueue.domain.VisitRegistration
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class VisitCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val appointmentId: UUID? = null,
  val visitType: String? = null,
  val departmentId: UUID? = null,
  val doctorId: UUID? = null,
  val specialtyId: UUID? = null,
  val priority: String? = null,
  val reason: String? = null,
  val source: String? = null,
  val registered: Boolean? = null,
  val addToQueue: Boolean? = null,
  val queueType: String? = null,
)

data class VisitCreateResult(
  val visit: VisitDto,
  val queueEntry: QueueEntryView? = null,
)

data class VisitCancelDto(
  val reason: String? = null,
)

data class VisitDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID,
  val appointmentId: UUID?,
  val visitNumber: String,
  val visitType: String,
  val departmentId: UUID?,
  val doctorId: UUID?,
  val specialtyId: UUID?,
  val reason: String?,
  val priority: String,
  val source: String,
  val status: String,
  val insuranceVerification: String?,
  val insuranceVerifiedAt: Instant?,
  val registrationStartedAt: Instant,
  val registeredAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(entity: VisitRegistration) =
      VisitDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        appointmentId = entity.appointmentId,
        visitNumber = entity.visitNumber,
        visitType = entity.visitType,
        departmentId = entity.departmentId,
        doctorId = entity.doctorId,
        specialtyId = entity.specialtyId,
        reason = entity.reason,
        priority = entity.priority,
        source = entity.source,
        status = entity.status,
        insuranceVerification = entity.insuranceVerification,
        insuranceVerifiedAt = entity.insuranceVerifiedAt,
        registrationStartedAt = entity.registrationStartedAt,
        registeredAt = entity.registeredAt,
        createdAt = entity.createdAt,
      )
  }
}

data class QueueCreateDto(
  val hospitalId: UUID? = null,
  val queueType: String? = null,
  val patientId: UUID? = null,
  val patientName: String? = null,
  val visitId: UUID? = null,
  val priority: String? = null,
  val servicePointId: String? = null,
  val departmentId: UUID? = null,
)

data class QueueTypeDto(
  val queueType: String? = null,
)

data class QueueStartDto(
  val servedBy: UUID? = null,
)

data class QueueTransferDto(
  val queueType: String? = null,
)

data class QueueEntryView(
  val id: UUID?,
  val queueType: String,
  val tokenNumber: String,
  val patientId: UUID,
  val patientName: String?,
  val priority: String,
  val status: String,
  val visitId: UUID?,
  val appointmentId: UUID?,
  val departmentId: UUID?,
  val servicePointId: String?,
  val servicePointName: String?,
  val servedBy: UUID?,
  val calledAt: Instant?,
  val startedAt: Instant?,
  val completedAt: Instant?,
  val skippedAt: Instant?,
  val noShowAt: Instant?,
  val estimatedWaitMinutes: Int?,
  val queueDate: LocalDate,
  val createdAt: Instant?,
  val qrPayload: String,
) {
  companion object {
    fun from(entity: QueueEntry) =
      QueueEntryView(
        id = entity.id,
        queueType = entity.queueType,
        tokenNumber = entity.tokenNumber,
        patientId = entity.patientId,
        patientName = entity.patientName,
        priority = entity.priority,
        status = entity.status,
        visitId = entity.visitId,
        appointmentId = entity.appointmentId,
        departmentId = entity.departmentId,
        servicePointId = entity.servicePointId,
        servicePointName = entity.servicePointName,
        servedBy = entity.servedBy,
        calledAt = entity.calledAt,
        startedAt = entity.startedAt,
        completedAt = entity.completedAt,
        skippedAt = entity.skippedAt,
        noShowAt = entity.noShowAt,
        estimatedWaitMinutes = entity.estimatedWaitMinutes,
        queueDate = entity.queueDate,
        createdAt = entity.createdAt,
        qrPayload = "SQM:${entity.hospitalId?.toString().orEmpty()}:${entity.queueType}:${entity.tokenNumber}:${entity.queueDate}",
      )
  }
}

data class BoardEntry(
  val queueType: String,
  val tokenNumber: String,
  val patientName: String?,
  val status: String,
  val priority: String,
  val estimatedWaitMinutes: Int?,
  val calledAt: Instant?,
) {
  companion object {
    fun from(entity: QueueEntry) =
      BoardEntry(
        queueType = entity.queueType,
        tokenNumber = entity.tokenNumber,
        patientName = entity.patientName,
        status = entity.status,
        priority = entity.priority,
        estimatedWaitMinutes = entity.estimatedWaitMinutes,
        calledAt = entity.calledAt,
      )
  }
}

// 01.06 TV display: per-token view for the waiting-room screen — deliberately carries NO patient identifiers.
data class DisplayTokenView(
  val tokenNumber: String,
  val queueType: String,
  val priority: String,
  val calledAt: Instant?,
) {
  companion object {
    fun from(entity: QueueEntry) =
      DisplayTokenView(
        tokenNumber = entity.tokenNumber,
        queueType = entity.queueType,
        priority = entity.priority,
        calledAt = entity.calledAt,
      )
  }
}

data class CounterBoardView(
  val servicePointId: String,
  val servicePointName: String,
  val departmentId: UUID?,
  val departmentName: String?,
  val current: DisplayTokenView?,
  val next: List<DisplayTokenView>,
)

data class DisplayBoardView(
  val generatedAt: Instant,
  val counters: List<CounterBoardView>,
)

data class QueueStatsView(
  val queueType: String,
  val waiting: Int,
  val inService: Int,
  val completed: Int,
  val avgWaitMinutes: Long,
)

data class ServicePointCreateDto(
  val hospitalId: UUID? = null,
  val name: String? = null,
  val code: String? = null,
  val queueType: String? = null,
  val counterNumber: String? = null,
  val status: String? = null,
)

data class ServicePointDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String,
  val code: String?,
  val queueType: String,
  val counterNumber: String?,
  val status: String,
) {
  companion object {
    fun from(entity: ServicePoint) =
      ServicePointDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        code = entity.code,
        queueType = entity.queueType,
        counterNumber = entity.counterNumber,
        status = entity.status,
      )
  }
}

/** 01.03 visitor/attendant pass issue request. */
data class VisitPassIssueDto(
  val visitorName: String? = null,
  val relation: String? = null,
  val phone: String? = null,
  val idReference: String? = null,
)

data class VisitPassDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val visitId: UUID,
  val patientId: UUID,
  val passNumber: String,
  val visitorName: String,
  val relation: String,
  val phone: String?,
  val idReference: String?,
  val status: String,
  val issuedAt: Instant,
  val returnedAt: Instant?,
) {
  companion object {
    fun from(entity: VisitPass) =
      VisitPassDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        visitId = entity.visitId,
        patientId = entity.patientId,
        passNumber = entity.passNumber,
        visitorName = entity.visitorName,
        relation = entity.relation,
        phone = entity.phone,
        idReference = entity.idReference,
        status = entity.status,
        issuedAt = entity.issuedAt,
        returnedAt = entity.returnedAt,
      )
  }
}

/** 01.03 consult-fee receipt issue request. */
data class ConsultReceiptIssueDto(
  val amountQar: BigDecimal? = null,
  val mode: String? = null,
)

data class ConsultReceiptDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val visitId: UUID,
  val patientId: UUID,
  val receiptNumber: String,
  val amountQar: BigDecimal,
  val mode: String,
  val status: String,
  val issuedAt: Instant,
  val voidedAt: Instant?,
) {
  companion object {
    fun from(entity: ConsultReceipt) =
      ConsultReceiptDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        visitId = entity.visitId,
        patientId = entity.patientId,
        receiptNumber = entity.receiptNumber,
        amountQar = entity.amountQar,
        mode = entity.mode,
        status = entity.status,
        issuedAt = entity.issuedAt,
        voidedAt = entity.voidedAt,
      )
  }
}

/** 01.07 voice announcements: zones, rules and manual enqueue DTOs. */
data class DisplayZoneCreateDto(
  val hospitalId: UUID? = null,
  val name: String? = null,
  val servicePointId: String? = null,
  val volume: Int? = null,
)

data class DisplayZoneDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String,
  val servicePointId: String?,
  val volume: Int,
  val enabled: Boolean,
) {
  companion object {
    fun from(entity: DisplayZone) =
      DisplayZoneDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        servicePointId = entity.servicePointId,
        volume = entity.volume,
        enabled = entity.enabled,
      )
  }
}

data class AnnouncementRuleCreateDto(
  val hospitalId: UUID? = null,
  val queueType: String? = null,
  val repeatCount: Int? = null,
  val languages: String? = null,
  val zoneId: UUID? = null,
)

data class AnnouncementRuleDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val queueType: String,
  val repeatCount: Int,
  val languages: String,
  val zoneId: UUID?,
  val enabled: Boolean,
) {
  companion object {
    fun from(entity: AnnouncementRule) =
      AnnouncementRuleDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        queueType = entity.queueType,
        repeatCount = entity.repeatCount,
        languages = entity.languages,
        zoneId = entity.zoneId,
        enabled = entity.enabled,
      )
  }
}

data class AnnouncementEnqueueDto(
  val hospitalId: UUID? = null,
  val texts: Map<String, String>? = null,
  val zoneId: UUID? = null,
  val repeatCount: Int? = null,
)

/** 01.07: what was just enqueued for the display page to speak. */
data class AnnouncementView(
  val tokenNumber: String?,
  val queueType: String?,
  val counter: String?,
  val priority: String?,
  val repeatCount: Int,
  val languages: List<String>,
  val zoneId: UUID?,
  val zoneName: String?,
  val volume: Int,
  val texts: Map<String, String>,
)
