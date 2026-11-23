package com.squamed.core.hr.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "roster", schema = "hr")
class Roster(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "ward_id")
  var wardId: UUID? = null,
  @Column(name = "week_start", nullable = false)
  var weekStart: LocalDate,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "DRAFT",
  @Column(name = "published_by")
  var publishedBy: UUID? = null,
  @Column(name = "published_at")
  var publishedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "shift", schema = "hr")
class Shift(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "roster_id", nullable = false)
  var rosterId: UUID,
  @Column(name = "ward_id")
  var wardId: UUID? = null,
  @Column(name = "employee_id", nullable = false)
  var employeeId: UUID,
  @Column(name = "shift_date", nullable = false)
  var shiftDate: LocalDate,
  @Column(name = "shift_code", nullable = false, length = 20)
  var shiftCode: String,
  @Column(name = "role", nullable = false, length = 40)
  var role: String,
) : BaseEntity()

@Entity
@Table(name = "leave_request", schema = "hr")
class LeaveRequest(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "employee_id", nullable = false)
  var employeeId: UUID,
  @Column(name = "leave_type", nullable = false, length = 30)
  var leaveType: String,
  @Column(name = "from_date", nullable = false)
  var fromDate: LocalDate,
  @Column(name = "to_date", nullable = false)
  var toDate: LocalDate,
  @Column(name = "reason", length = 400)
  var reason: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PENDING",
  @Column(name = "decided_by")
  var decidedBy: UUID? = null,
  @Column(name = "decided_at")
  var decidedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "attendance", schema = "hr")
class Attendance(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "employee_id", nullable = false)
  var employeeId: UUID,
  @Column(name = "work_date", nullable = false)
  var workDate: LocalDate,
  @Column(name = "check_in_at")
  var checkInAt: Instant? = null,
  @Column(name = "check_out_at")
  var checkOutAt: Instant? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PRESENT",
) : BaseEntity()

@Entity
@Table(name = "credential", schema = "hr")
class Credential(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "employee_id", nullable = false)
  var employeeId: UUID,
  @Column(name = "credential_type", nullable = false, length = 60)
  var credentialType: String,
  @Column(name = "identifier", length = 120)
  var identifier: String? = null,
  @Column(name = "expires_on", nullable = false)
  var expiresOn: LocalDate,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "ACTIVE",
  @Column(name = "renewal_task_id")
  var renewalTaskId: UUID? = null,
) : BaseEntity()
