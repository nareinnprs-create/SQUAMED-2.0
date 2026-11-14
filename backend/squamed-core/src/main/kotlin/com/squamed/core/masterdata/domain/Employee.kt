package com.squamed.core.masterdata.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "employee", schema = "master_data")
class Employee(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 50)
  var code: String,
  @Column(name = "first_name", nullable = false, length = 255)
  var firstName: String,
  @Column(name = "last_name", nullable = false, length = 255)
  var lastName: String,
  @Column(name = "gender", length = 30)
  var gender: String? = null,
  @Column(name = "date_of_birth")
  var dateOfBirth: LocalDate? = null,
  @Column(name = "phone", length = 50)
  var phone: String? = null,
  @Column(name = "email", length = 255)
  var email: String? = null,
  @Column(name = "designation", length = 100)
  var designation: String? = null,
  @Column(name = "department", length = 100)
  var department: String? = null,
  @Column(name = "qualification", length = 255)
  var qualification: String? = null,
  @Column(name = "license_number", length = 100)
  var licenseNumber: String? = null,
  @Column(name = "license_expiry")
  var licenseExpiry: LocalDate? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
