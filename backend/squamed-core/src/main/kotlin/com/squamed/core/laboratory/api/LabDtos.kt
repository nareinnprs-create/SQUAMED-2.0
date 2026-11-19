package com.squamed.core.laboratory.api

import com.squamed.core.laboratory.domain.LabOrder
import com.squamed.core.laboratory.domain.LabTestRequest
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
