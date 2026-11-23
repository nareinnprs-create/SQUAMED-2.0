package com.squamed.core.hr.repository

import com.squamed.core.hr.domain.Attendance
import com.squamed.core.hr.domain.Credential
import com.squamed.core.hr.domain.LeaveRequest
import com.squamed.core.hr.domain.Roster
import com.squamed.core.hr.domain.Shift
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface RosterRepository : JpaRepository<Roster, UUID> {
  fun findByHospitalIdAndWardIdAndWeekStart(
    hospitalId: UUID,
    wardId: UUID?,
    weekStart: LocalDate,
  ): Roster?

  fun findByHospitalIdOrderByWeekStartDesc(hospitalId: UUID): List<Roster>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Roster?
}

interface ShiftRepository : JpaRepository<Shift, UUID> {
  fun findByRosterIdOrderByShiftDateAscShiftCodeAsc(rosterId: UUID): List<Shift>

  fun findByHospitalIdAndShiftDate(
    hospitalId: UUID,
    shiftDate: LocalDate,
  ): List<Shift>

  fun findByHospitalIdAndShiftDateAndRole(
    hospitalId: UUID,
    shiftDate: LocalDate,
    role: String,
  ): List<Shift>
}

interface LeaveRequestRepository : JpaRepository<LeaveRequest, UUID> {
  fun findByHospitalIdOrderByFromDateDesc(hospitalId: UUID): List<LeaveRequest>

  fun findByHospitalIdAndStatusOrderByFromDateDesc(
    hospitalId: UUID,
    status: String,
  ): List<LeaveRequest>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): LeaveRequest?
}

interface AttendanceRepository : JpaRepository<Attendance, UUID> {
  fun findByHospitalIdAndEmployeeIdAndWorkDate(
    hospitalId: UUID,
    employeeId: UUID,
    workDate: LocalDate,
  ): Attendance?

  fun findByHospitalIdAndWorkDate(
    hospitalId: UUID,
    workDate: LocalDate,
  ): List<Attendance>
}

interface CredentialRepository : JpaRepository<Credential, UUID> {
  fun findByHospitalIdOrderByExpiresOnAsc(hospitalId: UUID): List<Credential>

  fun findByHospitalIdAndStatusAndExpiresOnLessThanEqual(
    hospitalId: UUID,
    status: String,
    expiresOn: LocalDate,
  ): List<Credential>
}
