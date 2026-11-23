package com.squamed.core.hr.api

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RosterCreateDto(
  val wardId: UUID? = null,
  val weekStart: LocalDate,
)

data class ShiftCreateDto(
  val employeeId: UUID,
  val shiftDate: LocalDate,
  val shiftCode: String,
  val role: String,
  val wardId: UUID? = null,
)

data class ShiftView(
  val id: UUID?,
  val employeeId: UUID,
  val shiftDate: LocalDate,
  val shiftCode: String,
  val role: String,
  val wardId: UUID?,
) {
  companion object {
    fun from(entity: com.squamed.core.hr.domain.Shift) = ShiftView(entity.id, entity.employeeId, entity.shiftDate, entity.shiftCode, entity.role, entity.wardId)
  }
}

data class RosterView(
  val id: UUID?,
  val hospitalId: UUID,
  val wardId: UUID?,
  val weekStart: LocalDate,
  val status: String,
  val shifts: List<ShiftView>,
) {
  companion object {
    fun from(
      entity: com.squamed.core.hr.domain.Roster,
      shifts: List<ShiftView>,
    ) = RosterView(entity.id, entity.hospitalId, entity.wardId, entity.weekStart, entity.status, shifts)
  }
}

data class LeaveCreateDto(
  val employeeId: UUID,
  val leaveType: String,
  val fromDate: LocalDate,
  val toDate: LocalDate,
  val reason: String? = null,
)

data class LeaveDecisionDto(
  val status: String,
)

data class LeaveView(
  val id: UUID?,
  val employeeId: UUID,
  val leaveType: String,
  val fromDate: LocalDate,
  val toDate: LocalDate,
  val reason: String?,
  val status: String,
  val decidedAt: Instant?,
) {
  companion object {
    fun from(entity: com.squamed.core.hr.domain.LeaveRequest) =
      LeaveView(entity.id, entity.employeeId, entity.leaveType, entity.fromDate, entity.toDate, entity.reason, entity.status, entity.decidedAt)
  }
}

data class AttendanceDto(
  val employeeId: UUID,
  val workDate: LocalDate,
  val checkInAt: Instant? = null,
  val checkOutAt: Instant? = null,
  val status: String? = null,
)

data class AttendanceView(
  val id: UUID?,
  val employeeId: UUID,
  val workDate: LocalDate,
  val checkInAt: Instant?,
  val checkOutAt: Instant?,
  val status: String,
) {
  companion object {
    fun from(entity: com.squamed.core.hr.domain.Attendance) =
      AttendanceView(entity.id, entity.employeeId, entity.workDate, entity.checkInAt, entity.checkOutAt, entity.status)
  }
}

data class CredentialCreateDto(
  val employeeId: UUID,
  val credentialType: String,
  val identifier: String? = null,
  val expiresOn: LocalDate,
)

data class CredentialView(
  val id: UUID?,
  val employeeId: UUID,
  val credentialType: String,
  val identifier: String?,
  val expiresOn: LocalDate,
  val status: String,
  val renewalTaskId: UUID?,
) {
  companion object {
    fun from(entity: com.squamed.core.hr.domain.Credential) =
      CredentialView(entity.id, entity.employeeId, entity.credentialType, entity.identifier, entity.expiresOn, entity.status, entity.renewalTaskId)
  }
}

data class RatioRowView(
  val wardId: UUID?,
  val wardName: String?,
  val patients: Int,
  val nurses: Int,
  val patientsPerNurse: Double?,
)

data class PayrollRowView(
  val employeeId: UUID,
  val employeeCode: String,
  val name: String,
  val shifts: Int,
  val hours: Int,
  val leaveDays: Long,
)

data class PayrollExportView(
  val from: LocalDate,
  val to: LocalDate,
  val rows: List<PayrollRowView>,
)
