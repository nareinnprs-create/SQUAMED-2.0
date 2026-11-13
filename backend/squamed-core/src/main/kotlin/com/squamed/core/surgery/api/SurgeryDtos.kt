package com.squamed.core.surgery.api

import com.squamed.core.surgery.domain.ImplantRecord
import com.squamed.core.surgery.domain.InstrumentTracking
import com.squamed.core.surgery.domain.IntraOpRecord
import com.squamed.core.surgery.domain.OtAllocation
import com.squamed.core.surgery.domain.OtRoom
import com.squamed.core.surgery.domain.PostOpRecord
import com.squamed.core.surgery.domain.PreOpChecklist
import com.squamed.core.surgery.domain.SurgerySchedule
import com.squamed.core.surgery.domain.SurgicalTeam
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SurgeryScheduleCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val admissionId: UUID? = null,
  val bookingNumber: String? = null,
  val procedureName: String? = null,
  val procedureCode: String? = null,
  val specialty: String? = null,
  val surgeonId: UUID? = null,
  val scheduledStartAt: Instant? = null,
  val scheduledEndAt: Instant? = null,
  val notes: String? = null,
)

data class SurgeryStatusDto(
  val status: String? = null,
)

data class OtRoomCreateDto(
  val hospitalId: UUID? = null,
  val name: String? = null,
  val code: String? = null,
)

data class OtAllocationCreateDto(
  val otRoomId: UUID? = null,
  val startTime: Instant? = null,
  val endTime: Instant? = null,
)

data class SurgicalTeamCreateDto(
  val staffName: String? = null,
  val role: String? = null,
)

data class InstrumentCreateDto(
  val instrumentName: String? = null,
  val setName: String? = null,
)

data class InstrumentStatusDto(
  val status: String? = null,
)

data class ImplantCreateDto(
  val implantName: String? = null,
  val catalogNumber: String? = null,
  val lotNumber: String? = null,
  val expiryDate: LocalDate? = null,
  val quantity: Int? = null,
  val notes: String? = null,
)

data class ChecklistCreateDto(
  val checklistItem: String? = null,
  val category: String? = null,
)

data class IntraOpCreateDto(
  val eventName: String? = null,
  val notes: String? = null,
)

data class PostOpCreateDto(
  val patientCondition: String? = null,
  val destination: String? = null,
  val painScore: Int? = null,
  val drainOutputMl: Int? = null,
  val complications: String? = null,
)

data class OtRoomView(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String,
  val code: String,
  val status: String,
) {
  companion object {
    fun from(entity: OtRoom) = OtRoomView(entity.id, entity.hospitalId, entity.name, entity.code, entity.status)
  }
}

data class SurgeryScheduleView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val admissionId: UUID?,
  val bookingNumber: String?,
  val procedureName: String,
  val procedureCode: String?,
  val specialty: String?,
  val surgeonId: UUID?,
  val status: String,
  val scheduledStartAt: Instant?,
  val scheduledEndAt: Instant?,
  val actualStartAt: Instant?,
  val actualEndAt: Instant?,
  val notes: String?,
  val createdAt: Instant?,
  val updatedAt: Instant?,
) {
  companion object {
    fun from(entity: SurgerySchedule) =
      SurgeryScheduleView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        admissionId = entity.admissionId,
        bookingNumber = entity.bookingNumber,
        procedureName = entity.procedureName,
        procedureCode = entity.procedureCode,
        specialty = entity.specialty,
        surgeonId = entity.surgeonId,
        status = entity.status,
        scheduledStartAt = entity.scheduledStartAt,
        scheduledEndAt = entity.scheduledEndAt,
        actualStartAt = entity.actualStartAt,
        actualEndAt = entity.actualEndAt,
        notes = entity.notes,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
      )
  }
}

data class OtAllocationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val otRoomId: UUID?,
  val surgeryScheduleId: UUID?,
  val startTime: Instant?,
  val endTime: Instant?,
  val status: String,
) {
  companion object {
    fun from(entity: OtAllocation) =
      OtAllocationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        otRoomId = entity.otRoomId,
        surgeryScheduleId = entity.surgeryScheduleId,
        startTime = entity.startTime,
        endTime = entity.endTime,
        status = entity.status,
      )
  }
}

data class SurgicalTeamView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val staffName: String,
  val role: String,
  val joinedAt: Instant?,
) {
  companion object {
    fun from(entity: SurgicalTeam) =
      SurgicalTeamView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        staffName = entity.staffName,
        role = entity.role,
        joinedAt = entity.joinedAt,
      )
  }
}

data class InstrumentView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val instrumentName: String,
  val setName: String?,
  val status: String,
  val countedBy: UUID?,
) {
  companion object {
    fun from(entity: InstrumentTracking) =
      InstrumentView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        instrumentName = entity.instrumentName,
        setName = entity.setName,
        status = entity.status,
        countedBy = entity.countedBy,
      )
  }
}

data class ImplantView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val implantName: String,
  val catalogNumber: String?,
  val lotNumber: String?,
  val expiryDate: LocalDate?,
  val quantity: Int,
  val implantedBy: UUID?,
  val notes: String?,
) {
  companion object {
    fun from(entity: ImplantRecord) =
      ImplantView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        implantName = entity.implantName,
        catalogNumber = entity.catalogNumber,
        lotNumber = entity.lotNumber,
        expiryDate = entity.expiryDate,
        quantity = entity.quantity,
        implantedBy = entity.implantedBy,
        notes = entity.notes,
      )
  }
}

data class PreOpChecklistView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val checklistItem: String,
  val category: String?,
  val checked: Boolean,
  val checkedBy: UUID?,
  val checkedAt: Instant?,
) {
  companion object {
    fun from(entity: PreOpChecklist) =
      PreOpChecklistView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        checklistItem = entity.checklistItem,
        category = entity.category,
        checked = entity.checked,
        checkedBy = entity.checkedBy,
        checkedAt = entity.checkedAt,
      )
  }
}

data class IntraOpView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val eventName: String,
  val notes: String?,
  val recordedBy: UUID?,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: IntraOpRecord) =
      IntraOpView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        eventName = entity.eventName,
        notes = entity.notes,
        recordedBy = entity.recordedBy,
        recordedAt = entity.recordedAt,
      )
  }
}

data class PostOpView(
  val id: UUID?,
  val hospitalId: UUID?,
  val surgeryScheduleId: UUID?,
  val patientCondition: String?,
  val destination: String?,
  val painScore: Int?,
  val drainOutputMl: Int?,
  val complications: String?,
  val recordedBy: UUID?,
  val recordedAt: Instant?,
) {
  companion object {
    fun from(entity: PostOpRecord) =
      PostOpView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        surgeryScheduleId = entity.surgeryScheduleId,
        patientCondition = entity.patientCondition,
        destination = entity.destination,
        painScore = entity.painScore,
        drainOutputMl = entity.drainOutputMl,
        complications = entity.complications,
        recordedBy = entity.recordedBy,
        recordedAt = entity.recordedAt,
      )
  }
}
