package com.squamed.core.receptionqueue.api

import com.squamed.core.receptionqueue.domain.QueueEntry
import com.squamed.core.receptionqueue.domain.ServicePoint
import com.squamed.core.receptionqueue.domain.VisitRegistration
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

data class QueueStatsView(
  val queueType: String,
  val waiting: Int,
  val inService: Int,
  val completed: Int,
  val avgWaitMinutes: Long,
)

data class ServicePointCreateDto(
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
