package com.squamed.core.ipd.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admission", schema = "ipd")
class Admission(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "admission_type", nullable = false, length = 30)
  var admissionType: String = "elective",
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "admitted",
  @Column(name = "bed_id")
  var bedId: UUID? = null,
  @Column(name = "ward_id")
  var wardId: UUID? = null,
  @Column(name = "room_id")
  var roomId: UUID? = null,
  @Column(name = "attending_doctor_id")
  var attendingDoctorId: UUID? = null,
  @Column(name = "admission_diagnosis")
  var admissionDiagnosis: String? = null,
  @Column(name = "source", nullable = false, length = 30)
  var source: String = "reception",
  @Column(name = "admitted_at")
  var admittedAt: Instant? = null,
  @Column(name = "admitted_by")
  var admittedBy: UUID? = null,
  @Column(name = "discharged_at")
  var dischargedAt: Instant? = null,
  @Column(name = "discharge_type", length = 30)
  var dischargeType: String? = null,
  @Column(name = "discharge_notes")
  var dischargeNotes: String? = null,
  // 04.01 expected-discharge planning + IPC isolation flag for allocation.
  @Column(name = "expected_discharge_at")
  var expectedDischargeAt: Instant? = null,
  @Column(name = "isolation_type", nullable = false, length = 10)
  var isolationType: String = "NONE",
) : BaseEntity()

@Entity
@Table(name = "admission_transfer", schema = "ipd")
class AdmissionTransfer(
  @Column(name = "admission_id", nullable = false)
  var admissionId: UUID,
  @Column(name = "from_bed_id")
  var fromBedId: UUID? = null,
  @Column(name = "to_bed_id")
  var toBedId: UUID? = null,
  @Column(name = "reason")
  var reason: String? = null,
  @Column(name = "transferred_at", nullable = false)
  var transferredAt: Instant = Instant.now(),
  @Column(name = "transferred_by")
  var transferredBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "discharge_summary", schema = "ipd")
class DischargeSummary(
  @Column(name = "admission_id", nullable = false)
  var admissionId: UUID,
  @Column(name = "summary_text")
  var summaryText: String? = null,
  @Column(name = "follow_up_plan")
  var followUpPlan: String? = null,
  @Column(name = "medications_at_discharge")
  var medicationsAtDischarge: String? = null,
  @Column(name = "instructions")
  var instructions: String? = null,
  @Column(name = "prepared_by")
  var preparedBy: UUID? = null,
  @Column(name = "prepared_at", nullable = false)
  var preparedAt: Instant = Instant.now(),
  // 04.01: the summary must be signed before discharge; edits after signing are rejected.
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "DRAFT",
  @Column(name = "icd_codes", length = 300)
  var icdCodes: String? = null,
  @Column(name = "signed_by")
  var signedBy: UUID? = null,
  @Column(name = "signed_at")
  var signedAt: Instant? = null,
) : BaseEntity()

// 04.01 discharge checklist: pharmacy returns + final bill + summary signature gate the discharge.
@Entity
@Table(name = "discharge_checklist", schema = "ipd")
class DischargeChecklist(
  @Column(name = "admission_id", nullable = false)
  var admissionId: UUID,
  @Column(name = "pharmacy_return_done", nullable = false)
  var pharmacyReturnDone: Boolean = false,
  @Column(name = "pharmacy_return_at")
  var pharmacyReturnAt: Instant? = null,
  @Column(name = "final_bill_verified", nullable = false)
  var finalBillVerified: Boolean = false,
  @Column(name = "completed_by")
  var completedBy: UUID? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "attendant", schema = "ipd")
class Attendant(
  @Column(name = "admission_id", nullable = false)
  var admissionId: UUID,
  @Column(name = "name", nullable = false, length = 150)
  var name: String,
  @Column(name = "relationship", length = 50)
  var relationship: String? = null,
  @Column(name = "phone", length = 30)
  var phone: String? = null,
  @Column(name = "notes")
  var notes: String? = null,
) : BaseEntity()
