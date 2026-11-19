package com.squamed.core.laboratory.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.squamed.core.laboratory.domain.CriticalResult
import com.squamed.core.laboratory.domain.CriticalResultAck
import com.squamed.core.laboratory.domain.LabOrder
import com.squamed.core.laboratory.domain.LabReferenceRange
import com.squamed.core.laboratory.domain.LabReport
import com.squamed.core.laboratory.domain.LabResultAmendment
import com.squamed.core.laboratory.domain.LabTestRequest
import com.squamed.core.laboratory.domain.QcRun
import com.squamed.core.laboratory.domain.SpecimenCustody
import java.time.Instant
import java.util.UUID

data class LabTestInput(
  val testCode: String,
  val testName: String,
)

data class CreateLabOrderDto(
  val patientId: UUID,
  val encounterId: UUID? = null,
  val tests: List<LabTestInput>,
  val priority: String? = null,
  val clinicalNote: String? = null,
  val orderedBy: UUID? = null,
  val hospitalId: UUID? = null,
)

data class LabTestResultInput(
  val testRequestId: UUID,
  val resultValue: String? = null,
  val unit: String? = null,
  val referenceRange: String? = null,
  val isAbnormal: Boolean? = null,
)

data class EnterResultsDto(
  val results: List<LabTestResultInput>,
  val enteredVia: String? = null,
)

data class CollectRequestDto(
  val collectedBy: UUID? = null,
)

data class RejectRequestDto(
  val reason: String,
  val recollect: Boolean = false,
)

data class VerifyLabOrderDto(
  val overrideNote: String? = null,
)

data class AckCriticalResultDto(
  val note: String? = null,
)

data class CreateReferenceRangeDto(
  val testCode: String,
  /** M | F | ANY */
  val sex: String,
  val ageMinYears: Int = 0,
  val ageMaxYears: Int = 200,
  val rangeText: String,
  val hospitalId: UUID? = null,
)

data class ReferenceRangeView(
  val id: UUID?,
  val hospitalId: UUID,
  val testCode: String,
  val sex: String,
  val ageMinYears: Int,
  val ageMaxYears: Int,
  val rangeText: String,
  val isActive: Boolean,
) {
  companion object {
    fun from(entity: LabReferenceRange) =
      ReferenceRangeView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        testCode = entity.testCode,
        sex = entity.sex,
        ageMinYears = entity.ageMinYears,
        ageMaxYears = entity.ageMaxYears,
        rangeText = entity.rangeText,
        isActive = entity.isActive,
      )
  }
}

data class AmendResultDto(
  val newValue: String,
  val reason: String,
)

data class DeliverReportDto(
  /** COUNTER | APP */
  val via: String,
  val deliveredTo: UUID? = null,
)

data class LabReportView(
  val id: UUID?,
  val hospitalId: UUID,
  val labOrderId: UUID,
  val reportVersion: Int,
  val storageKey: String,
  val publishedBy: UUID?,
  val publishedAt: Instant?,
  val deliveredVia: String?,
  val deliveredTo: UUID?,
  val deliveredAt: Instant?,
) {
  companion object {
    fun from(entity: LabReport) =
      LabReportView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        labOrderId = entity.labOrderId,
        reportVersion = entity.reportVersion,
        storageKey = entity.storageKey,
        publishedBy = entity.publishedBy,
        publishedAt = entity.publishedAt,
        deliveredVia = entity.deliveredVia,
        deliveredTo = entity.deliveredTo,
        deliveredAt = entity.deliveredAt,
      )
  }
}

/** Latest published report download (02.04) — orderNumber drives the Content-Disposition filename. */
class LabReportDownload(
  val orderNumber: String,
  val reportVersion: Int,
  val bytes: ByteArray,
)

data class LabResultAmendmentView(
  val id: UUID?,
  val labOrderId: UUID,
  val labTestRequestId: UUID,
  val oldValue: String?,
  val newValue: String,
  val reason: String,
  val amendedBy: UUID?,
  val amendedAt: Instant?,
) {
  companion object {
    fun from(entity: LabResultAmendment) =
      LabResultAmendmentView(
        id = entity.id,
        labOrderId = entity.labOrderId,
        labTestRequestId = entity.labTestRequestId,
        oldValue = entity.oldValue,
        newValue = entity.newValue,
        reason = entity.reason,
        amendedBy = entity.amendedBy,
        amendedAt = entity.amendedAt,
      )
  }
}

data class TatRowView(
  val testCode: String,
  val count: Int,
  val avgMinutes: Double,
  val minMinutes: Long,
  val maxMinutes: Long,
)

data class CreateQcRunDto(
  val testCode: String,
  val level: String,
  val observedValue: Double,
  val targetMean: Double,
  val targetSd: Double,
  val instrument: String? = null,
  val note: String? = null,
)

data class LabOrderView(
  val id: UUID?,
  val hospitalId: UUID?,
  val encounterId: UUID?,
  val patientId: UUID,
  val orderNumber: String,
  val priority: String,
  val status: String,
  val resultStatus: String,
  val clinicalNote: String?,
  val orderedBy: UUID?,
  val orderedAt: Instant?,
  val specimenTakenAt: Instant?,
  val collectedBy: UUID?,
  val receivedAt: Instant?,
  val startedAt: Instant?,
  val completedAt: Instant?,
  val rejectedReason: String?,
  val barcodeId: String?,
  val accessionId: String?,
  val recollectOf: UUID?,
  val verifiedAt: Instant?,
  val verifiedBy: UUID?,
  val releasedAt: Instant?,
  val releasedBy: UUID?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: LabOrder) =
      LabOrderView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        encounterId = entity.encounterId,
        patientId = entity.patientId,
        orderNumber = entity.orderNumber,
        priority = entity.priority,
        status = entity.status,
        resultStatus = entity.resultStatus,
        clinicalNote = entity.clinicalNote,
        orderedBy = entity.orderedBy,
        orderedAt = entity.orderedAt,
        specimenTakenAt = entity.specimenTakenAt,
        collectedBy = entity.collectedBy,
        receivedAt = entity.receivedAt,
        startedAt = entity.startedAt,
        completedAt = entity.completedAt,
        rejectedReason = entity.rejectedReason,
        barcodeId = entity.barcodeId,
        accessionId = entity.accessionId,
        recollectOf = entity.recollectOf,
        verifiedAt = entity.verifiedAt,
        verifiedBy = entity.verifiedBy,
        releasedAt = entity.releasedAt,
        releasedBy = entity.releasedBy,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class LabTestRequestView(
  val id: UUID?,
  val labOrderId: UUID,
  val testCode: String,
  val testName: String,
  val status: String,
  val resultValue: String?,
  val unit: String?,
  val referenceRange: String?,
  val isAbnormal: Boolean,
  val notes: String?,
  val resultEnteredBy: UUID?,
  val resultEnteredAt: Instant?,
  val priorValue: String?,
  val deltaPercent: Double?,
  val isDeltaBlocked: Boolean,
  val enteredVia: String,
) {
  companion object {
    fun from(entity: LabTestRequest) =
      LabTestRequestView(
        id = entity.id,
        labOrderId = entity.labOrderId,
        testCode = entity.testCode,
        testName = entity.testName,
        status = entity.status,
        resultValue = entity.resultValue,
        unit = entity.unit,
        referenceRange = entity.referenceRange,
        isAbnormal = entity.isAbnormal,
        notes = entity.notes,
        resultEnteredBy = entity.resultEnteredBy,
        resultEnteredAt = entity.resultEnteredAt,
        priorValue = entity.priorValue,
        deltaPercent = entity.deltaPercent,
        isDeltaBlocked = entity.isDeltaBlocked,
        enteredVia = entity.enteredVia,
      )
  }
}

data class SpecimenCustodyView(
  val event: String,
  val actorId: UUID?,
  val note: String?,
  val occurredAt: Instant?,
) {
  companion object {
    fun from(entity: SpecimenCustody) =
      SpecimenCustodyView(
        event = entity.event,
        actorId = entity.actorId,
        note = entity.note,
        occurredAt = entity.occurredAt,
      )
  }
}

data class LabOrderDetailView(
  val order: LabOrderView,
  val tests: List<LabTestRequestView> = emptyList(),
  val custody: List<SpecimenCustodyView> = emptyList(),
)

data class CriticalResultView(
  val id: UUID?,
  val hospitalId: UUID,
  val labOrderId: UUID,
  val labTestRequestId: UUID,
  val patientId: UUID,
  val testCode: String,
  val testName: String,
  val resultValue: String?,
  val criticality: String,
  val status: String,
  val slaMinutes: Int,
  val minutesOpen: Long,
  val escalationLevel: Int,
  val escalationRole: String,
  val ackedBy: UUID?,
  val ackedAt: Instant?,
  val createdAt: Instant?,
) {
  companion object {
    fun from(
      entity: CriticalResult,
      now: Instant = Instant.now(),
    ): CriticalResultView {
      val minutesOpen = entity.createdAt?.until(now, java.time.temporal.ChronoUnit.MINUTES) ?: 0
      val level =
        if (minutesOpen >= entity.slaMinutes * 2) {
          2
        } else if (minutesOpen >= entity.slaMinutes) {
          1
        } else {
          0
        }
      return CriticalResultView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        labOrderId = entity.labOrderId,
        labTestRequestId = entity.labTestRequestId,
        patientId = entity.patientId,
        testCode = entity.testCode,
        testName = entity.testName,
        resultValue = entity.resultValue,
        criticality = entity.criticality,
        status = entity.status,
        slaMinutes = entity.slaMinutes,
        minutesOpen = minutesOpen,
        escalationLevel = level,
        escalationRole =
          when (level) {
            2 -> "cmo"
            1 -> "dept_head"
            else -> "none"
          },
        ackedBy = entity.ackedBy,
        ackedAt = entity.ackedAt,
        createdAt = entity.createdAt,
      )
    }
  }
}

data class CriticalResultAckView(
  val id: UUID?,
  val criticalResultId: UUID,
  val actorId: UUID?,
  val note: String?,
  val occurredAt: Instant?,
) {
  companion object {
    fun from(entity: CriticalResultAck) =
      CriticalResultAckView(
        id = entity.id,
        criticalResultId = entity.criticalResultId,
        actorId = entity.actorId,
        note = entity.note,
        occurredAt = entity.occurredAt,
      )
  }
}

data class QcRunView(
  val id: UUID?,
  val hospitalId: UUID,
  val instrument: String?,
  val testCode: String,
  val level: String,
  val observedValue: Double,
  val targetMean: Double,
  val targetSd: Double,
  @get:JsonProperty("zValue")
  val zValue: Double,
  val westgardRules: String?,
  val status: String,
  val note: String?,
  val runBy: UUID?,
  val runAt: Instant?,
) {
  companion object {
    fun from(entity: QcRun): QcRunView =
      QcRunView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        instrument = entity.instrument,
        testCode = entity.testCode,
        level = entity.level,
        observedValue = entity.observedValue,
        targetMean = entity.targetMean,
        targetSd = entity.targetSd,
        zValue = if (entity.targetSd > 0.0) (entity.observedValue - entity.targetMean) / entity.targetSd else 0.0,
        westgardRules = entity.westgardRules,
        status = entity.status,
        note = entity.note,
        runBy = entity.runBy,
        runAt = entity.runAt,
      )
  }
}
