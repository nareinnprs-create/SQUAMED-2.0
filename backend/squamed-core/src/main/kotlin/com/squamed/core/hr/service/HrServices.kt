package com.squamed.core.hr.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.repository.BedRepository
import com.squamed.core.hospitalconfig.repository.WardRepository
import com.squamed.core.hr.api.AttendanceDto
import com.squamed.core.hr.api.AttendanceView
import com.squamed.core.hr.api.CredentialCreateDto
import com.squamed.core.hr.api.CredentialView
import com.squamed.core.hr.api.LeaveCreateDto
import com.squamed.core.hr.api.LeaveView
import com.squamed.core.hr.api.PayrollExportView
import com.squamed.core.hr.api.PayrollRowView
import com.squamed.core.hr.api.RatioRowView
import com.squamed.core.hr.api.RosterCreateDto
import com.squamed.core.hr.api.RosterView
import com.squamed.core.hr.api.ShiftCreateDto
import com.squamed.core.hr.api.ShiftView
import com.squamed.core.hr.domain.Attendance
import com.squamed.core.hr.domain.Credential
import com.squamed.core.hr.domain.LeaveRequest
import com.squamed.core.hr.domain.Roster
import com.squamed.core.hr.domain.Shift
import com.squamed.core.hr.repository.AttendanceRepository
import com.squamed.core.hr.repository.CredentialRepository
import com.squamed.core.hr.repository.LeaveRequestRepository
import com.squamed.core.hr.repository.RosterRepository
import com.squamed.core.hr.repository.ShiftRepository
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.masterdata.repository.EmployeeRepository
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class HrRosterService(
  private val rosterRepository: RosterRepository,
  private val shiftRepository: ShiftRepository,
  private val employeeRepository: EmployeeRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createRoster(
    hospitalId: UUID,
    dto: RosterCreateDto,
    actorId: UUID?,
  ): RosterView {
    val weekStart = dto.weekStart.with(DayOfWeek.MONDAY)
    val existing = rosterRepository.findByHospitalIdAndWardIdAndWeekStart(hospitalId, dto.wardId, weekStart)
    if (existing != null) throw IllegalStateException("ROSTER_ALREADY_EXISTS")
    val roster =
      rosterRepository.save(
        Roster(hospitalId = hospitalId, wardId = dto.wardId, weekStart = weekStart).also { it.createdBy = actorId },
      )
    return view(roster)
  }

  @Transactional
  fun addShift(
    rosterId: UUID,
    hospitalId: UUID,
    dto: ShiftCreateDto,
    actorId: UUID?,
  ): ShiftView {
    val roster = rosterRepository.findByIdAndHospitalId(rosterId, hospitalId) ?: throw IllegalArgumentException("ROSTER_NOT_FOUND")
    if (roster.status != "DRAFT") throw IllegalStateException("ROSTER_NOT_DRAFT")
    val employee = employeeRepository.findById(dto.employeeId).orElseThrow { IllegalArgumentException("EMPLOYEE_NOT_FOUND") }
    if (employee.hospitalId != hospitalId) throw IllegalArgumentException("EMPLOYEE_HOSPITAL_MISMATCH")
    if (dto.shiftDate !in roster.weekStart..roster.weekStart.plusDays(6)) throw IllegalArgumentException("SHIFT_OUTSIDE_ROSTER_WEEK")
    if (dto.shiftCode !in setOf("EARLY", "LATE", "NIGHT")) throw IllegalArgumentException("SHIFT_CODE_INVALID")
    val duplicate =
      shiftRepository.findByRosterIdOrderByShiftDateAscShiftCodeAsc(rosterId).any {
        it.employeeId == dto.employeeId && it.shiftDate == dto.shiftDate && it.shiftCode == dto.shiftCode
      }
    if (duplicate) throw IllegalStateException("SHIFT_DUPLICATE")
    val saved =
      shiftRepository.save(
        Shift(
          hospitalId = hospitalId,
          rosterId = rosterId,
          wardId = dto.wardId ?: roster.wardId,
          employeeId = dto.employeeId,
          shiftDate = dto.shiftDate,
          shiftCode = dto.shiftCode,
          role = dto.role.uppercase(),
        ).also { it.createdBy = actorId },
      )
    return ShiftView.from(saved)
  }

  @Transactional
  fun publish(
    rosterId: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): RosterView {
    val roster = rosterRepository.findByIdAndHospitalId(rosterId, hospitalId) ?: throw IllegalArgumentException("ROSTER_NOT_FOUND")
    if (roster.status != "DRAFT") throw IllegalStateException("ROSTER_NOT_DRAFT")
    val shifts = shiftRepository.findByRosterIdOrderByShiftDateAscShiftCodeAsc(rosterId)
    if (shifts.isEmpty()) throw IllegalArgumentException("ROSTER_EMPTY")
    roster.status = "PUBLISHED"
    roster.publishedBy = actorId
    roster.publishedAt = Instant.now()
    roster.updatedBy = actorId
    val saved = rosterRepository.save(roster)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "hr.roster_published",
        occurredAt = Instant.now(),
        source = "hr-service",
        entityType = "roster",
        entityId = saved.id,
        hospitalId = hospitalId,
        payload = mapOf("weekStart" to saved.weekStart.toString(), "shifts" to shifts.size),
        actorType = "user",
        actorId = actorId,
      ),
    )
    return view(saved)
  }

  @Transactional(readOnly = true)
  fun list(hospitalId: UUID): List<RosterView> = rosterRepository.findByHospitalIdOrderByWeekStartDesc(hospitalId).map(::view)

  @Transactional(readOnly = true)
  fun get(
    rosterId: UUID,
    hospitalId: UUID,
  ): RosterView {
    val roster = rosterRepository.findByIdAndHospitalId(rosterId, hospitalId) ?: throw IllegalArgumentException("ROSTER_NOT_FOUND")
    return view(roster)
  }

  private fun view(roster: Roster): RosterView {
    val shifts =
      roster.id?.let { shiftRepository.findByRosterIdOrderByShiftDateAscShiftCodeAsc(it).map(ShiftView::from) } ?: emptyList()
    return RosterView.from(roster, shifts)
  }
}

@Service
class HrLeaveService(
  private val leaveRepository: LeaveRequestRepository,
  private val employeeRepository: EmployeeRepository,
) {
  @Transactional
  fun request(
    hospitalId: UUID,
    dto: LeaveCreateDto,
    actorId: UUID?,
  ): LeaveView {
    if (dto.toDate.isBefore(dto.fromDate)) throw IllegalArgumentException("LEAVE_DATES_INVALID")
    val employee = employeeRepository.findById(dto.employeeId).orElseThrow { IllegalArgumentException("EMPLOYEE_NOT_FOUND") }
    if (employee.hospitalId != hospitalId) throw IllegalArgumentException("EMPLOYEE_HOSPITAL_MISMATCH")
    if (dto.leaveType.isBlank()) throw IllegalArgumentException("LEAVE_TYPE_REQUIRED")
    val saved =
      leaveRepository.save(
        LeaveRequest(
          hospitalId = hospitalId,
          employeeId = dto.employeeId,
          leaveType = dto.leaveType.trim().uppercase(),
          fromDate = dto.fromDate,
          toDate = dto.toDate,
          reason = dto.reason,
        ).also { it.createdBy = actorId },
      )
    return LeaveView.from(saved)
  }

  @Transactional
  fun decide(
    id: UUID,
    hospitalId: UUID,
    status: String,
    actorId: UUID?,
  ): LeaveView {
    val leave = leaveRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("LEAVE_NOT_FOUND")
    if (leave.status != "PENDING") throw IllegalStateException("LEAVE_NOT_PENDING")
    val next = status.trim().uppercase()
    if (next !in setOf("APPROVED", "REJECTED")) throw IllegalArgumentException("LEAVE_STATUS_INVALID")
    leave.status = next
    leave.decidedBy = actorId
    leave.decidedAt = Instant.now()
    leave.updatedBy = actorId
    return LeaveView.from(leaveRepository.save(leave))
  }

  @Transactional(readOnly = true)
  fun list(
    hospitalId: UUID,
    status: String?,
  ): List<LeaveView> =
    (
      if (status.isNullOrBlank()) {
        leaveRepository.findByHospitalIdOrderByFromDateDesc(hospitalId)
      } else {
        leaveRepository.findByHospitalIdAndStatusOrderByFromDateDesc(hospitalId, status.uppercase())
      }
    ).map(LeaveView::from)
}

@Service
class HrAttendanceService(
  private val attendanceRepository: AttendanceRepository,
  private val employeeRepository: EmployeeRepository,
) {
  @Transactional
  fun record(
    hospitalId: UUID,
    dto: AttendanceDto,
    actorId: UUID?,
  ): AttendanceView {
    val employee = employeeRepository.findById(dto.employeeId).orElseThrow { IllegalArgumentException("EMPLOYEE_NOT_FOUND") }
    if (employee.hospitalId != hospitalId) throw IllegalArgumentException("EMPLOYEE_HOSPITAL_MISMATCH")
    val record =
      attendanceRepository.findByHospitalIdAndEmployeeIdAndWorkDate(hospitalId, dto.employeeId, dto.workDate)
        ?: Attendance(hospitalId = hospitalId, employeeId = dto.employeeId, workDate = dto.workDate).also { it.createdBy = actorId }
    dto.checkInAt?.let { record.checkInAt = it }
    dto.checkOutAt?.let { record.checkOutAt = it }
    dto.status?.let { record.status = it.trim().uppercase() }
    record.updatedBy = actorId
    return AttendanceView.from(attendanceRepository.save(record))
  }

  @Transactional(readOnly = true)
  fun list(
    hospitalId: UUID,
    workDate: LocalDate?,
  ): List<AttendanceView> =
    (
      workDate?.let { attendanceRepository.findByHospitalIdAndWorkDate(hospitalId, it) }
        ?: attendanceRepository.findByHospitalIdAndWorkDate(hospitalId, LocalDate.now())
    ).map(AttendanceView::from)
}

@Service
class HrCredentialService(
  private val credentialRepository: CredentialRepository,
  private val employeeRepository: EmployeeRepository,
  private val taskService: TaskService,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun create(
    hospitalId: UUID,
    dto: CredentialCreateDto,
    actorId: UUID?,
  ): CredentialView {
    val employee = employeeRepository.findById(dto.employeeId).orElseThrow { IllegalArgumentException("EMPLOYEE_NOT_FOUND") }
    if (employee.hospitalId != hospitalId) throw IllegalArgumentException("EMPLOYEE_HOSPITAL_MISMATCH")
    if (dto.credentialType.isBlank()) throw IllegalArgumentException("CREDENTIAL_TYPE_REQUIRED")
    val saved =
      credentialRepository.save(
        Credential(
          hospitalId = hospitalId,
          employeeId = dto.employeeId,
          credentialType = dto.credentialType.trim(),
          identifier = dto.identifier,
          expiresOn = dto.expiresOn,
        ).also { it.createdBy = actorId },
      )
    return CredentialView.from(saved)
  }

  @Transactional(readOnly = true)
  fun list(hospitalId: UUID): List<CredentialView> = credentialRepository.findByHospitalIdOrderByExpiresOnAsc(hospitalId).map(CredentialView::from)

  /**
   * 06.04: credentials inside the renewal window get one renewal task and one event; past-due
   * credentials flip to EXPIRED. Re-running the scan never duplicates the task.
   */
  @Transactional
  fun scan(
    hospitalId: UUID,
    actorId: UUID,
  ): List<CredentialView> {
    val today = LocalDate.now()
    val window = today.plusDays(30)
    val due = credentialRepository.findByHospitalIdAndStatusAndExpiresOnLessThanEqual(hospitalId, "ACTIVE", window)
    due.forEach { credential ->
      if (credential.expiresOn.isBefore(today)) {
        credential.status = "EXPIRED"
      }
      if (credential.renewalTaskId == null) {
        val employee = employeeRepository.findById(credential.employeeId).orElse(null)
        val task =
          taskService.createTask(
            TaskCreateDto(
              hospitalId = hospitalId,
              title = "Renew ${credential.credentialType} for ${employee?.firstName ?: "employee"} ${employee?.lastName ?: ""}".trim(),
              description = "Credential ${credential.identifier ?: credential.id} expires ${credential.expiresOn}",
              priority = "high",
              relatedType = "CREDENTIAL",
              relatedId = credential.id,
            ),
            actorId,
          )
        credential.renewalTaskId = task.id
        eventPublisher.publish(
          DomainEvent(
            eventId = UUID.randomUUID(),
            eventType = "hr.credential_expiring",
            occurredAt = Instant.now(),
            source = "hr-service",
            entityType = "credential",
            entityId = credential.id,
            hospitalId = hospitalId,
            payload = mapOf("employeeId" to credential.employeeId.toString(), "expiresOn" to credential.expiresOn.toString()),
            actorType = "user",
            actorId = actorId,
          ),
        )
      }
      credential.updatedBy = actorId
      credentialRepository.save(credential)
    }
    return due.map(CredentialView::from)
  }
}

@Service
class HrReportService(
  private val bedRepository: BedRepository,
  private val wardRepository: WardRepository,
  private val admissionRepository: AdmissionRepository,
  private val shiftRepository: ShiftRepository,
  private val leaveRepository: LeaveRequestRepository,
  private val employeeRepository: EmployeeRepository,
) {
  @Transactional(readOnly = true)
  fun nursePatientRatio(
    hospitalId: UUID,
    date: LocalDate?,
  ): List<RatioRowView> {
    val day = date ?: LocalDate.now()
    val wards = wardRepository.findByHospitalId(hospitalId)
    val beds = bedRepository.findByHospitalId(hospitalId).associateBy { it.id }
    val admitted = admissionRepository.findAllByHospital(hospitalId).filter { it.status == "admitted" && it.bedId != null }
    val patientsByWard =
      admitted.groupingBy { admission -> beds[admission.bedId]?.wardId }.eachCount()
    val nursesByWard =
      shiftRepository
        .findByHospitalIdAndShiftDateAndRole(hospitalId, day, "NURSE")
        .groupingBy { it.wardId }
        .eachCount()
    val rows =
      wards.map { ward ->
        val patients = patientsByWard[ward.id] ?: 0
        val nurses = nursesByWard[ward.id] ?: 0
        RatioRowView(ward.id, ward.name, patients, nurses, if (nurses == 0) null else patients.toDouble() / nurses)
      }
    val unassignedPatients = patientsByWard[null] ?: 0
    val unassignedNurses = nursesByWard[null] ?: 0
    return if (unassignedPatients + unassignedNurses > 0) {
      rows +
        RatioRowView(
          null,
          "unassigned",
          unassignedPatients,
          unassignedNurses,
          if (unassignedNurses ==
            0
          ) {
            null
          } else {
            unassignedPatients.toDouble() / unassignedNurses
          },
        )
    } else {
      rows
    }
  }

  /** Payroll stays external: this is the export the payroll system consumes. */
  @Transactional(readOnly = true)
  fun payrollExport(
    hospitalId: UUID,
    from: LocalDate,
    to: LocalDate,
  ): PayrollExportView {
    if (to.isBefore(from)) throw IllegalArgumentException("PAYROLL_RANGE_INVALID")
    val allShifts =
      generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .flatMap { shiftRepository.findByHospitalIdAndShiftDate(hospitalId, it) }
        .toList()
    val leaves = leaveRepository.findByHospitalIdAndStatusOrderByFromDateDesc(hospitalId, "APPROVED")
    val employees = employeeRepository.findAll().filter { it.hospitalId == hospitalId }
    val rows =
      employees
        .map { employee ->
          val own = allShifts.filter { it.employeeId == employee.id }
          val leaveDays =
            leaves
              .filter { it.employeeId == employee.id }
              .sumOf { leave ->
                val start = maxOf(leave.fromDate, from)
                val end = minOf(leave.toDate, to)
                if (end.isBefore(start)) 0L else ChronoUnit.DAYS.between(start, end) + 1
              }
          PayrollRowView(
            employeeId = employee.id ?: throw IllegalStateException("EMPLOYEE_ID_MISSING"),
            employeeCode = employee.code,
            name = "${employee.firstName} ${employee.lastName}".trim(),
            shifts = own.size,
            hours = own.size * 8,
            leaveDays = leaveDays,
          )
        }.filter { it.shifts > 0 || it.leaveDays > 0 }
    return PayrollExportView(from, to, rows)
  }
}
