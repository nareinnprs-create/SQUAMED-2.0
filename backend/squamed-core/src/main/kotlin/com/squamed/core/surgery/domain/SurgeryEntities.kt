package com.squamed.core.surgery.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "ot_room", schema = "surgery")
class OtRoom(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "name", nullable = false, length = 100) var name: String,
  @Column(name = "code", nullable = false, length = 30) var code: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "operational",
) : BaseEntity()

@Entity
@Table(name = "surgery_schedule", schema = "surgery")
class SurgerySchedule(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "admission_id") var admissionId: UUID? = null,
  @Column(name = "booking_number", length = 30) var bookingNumber: String? = null,
  @Column(name = "procedure_name", nullable = false, length = 255) var procedureName: String,
  @Column(name = "procedure_code", length = 50) var procedureCode: String? = null,
  @Column(name = "specialty", length = 50) var specialty: String? = null,
  @Column(name = "surgeon_id") var surgeonId: UUID? = null,
  // 04.06 consent gate before the WHO sign-in/time-out.
  @Column(name = "consent_id") var consentId: UUID? = null,
  @Column(name = "status", nullable = false, length = 30) var status: String = "scheduled",
  @Column(name = "scheduled_start_at", nullable = false) var scheduledStartAt: Instant,
  @Column(name = "scheduled_end_at") var scheduledEndAt: Instant? = null,
  @Column(name = "actual_start_at") var actualStartAt: Instant? = null,
  @Column(name = "actual_end_at") var actualEndAt: Instant? = null,
  @Column(name = "notes") var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "ot_allocation", schema = "surgery")
class OtAllocation(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "ot_room_id", nullable = false) var otRoomId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "start_time", nullable = false) var startTime: Instant,
  @Column(name = "end_time") var endTime: Instant? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "allocated",
) : BaseEntity()

@Entity
@Table(name = "surgical_team", schema = "surgery")
class SurgicalTeam(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "staff_name", nullable = false, length = 255) var staffName: String,
  @Column(name = "role", nullable = false, length = 30) var role: String,
  @Column(name = "joined_at", nullable = false) var joinedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "instrument_tracking", schema = "surgery")
class InstrumentTracking(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "instrument_name", nullable = false, length = 255) var instrumentName: String,
  @Column(name = "set_name", length = 100) var setName: String? = null,
  @Column(name = "status", nullable = false, length = 30) var status: String = "booked",
  @Column(name = "counted_by") var countedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "implant_record", schema = "surgery")
class ImplantRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "implant_name", nullable = false, length = 255) var implantName: String,
  @Column(name = "catalog_number", length = 50) var catalogNumber: String? = null,
  @Column(name = "lot_number", length = 50) var lotNumber: String? = null,
  @Column(name = "expiry_date") var expiryDate: LocalDate? = null,
  @Column(name = "quantity", nullable = false) var quantity: Int = 1,
  @Column(name = "implanted_by") var implantedBy: UUID? = null,
  @Column(name = "notes") var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "pre_op_checklist", schema = "surgery")
class PreOpChecklist(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "checklist_item", nullable = false, length = 255) var checklistItem: String,
  // 04.06 WHO stage: SIGN_IN | TIME_OUT | SIGN_OUT.
  @Column(name = "stage", length = 10) var stage: String? = null,
  @Column(name = "category", length = 30) var category: String? = null,
  @Column(name = "checked", nullable = false) var checked: Boolean = false,
  @Column(name = "checked_by") var checkedBy: UUID? = null,
  @Column(name = "checked_at") var checkedAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "intra_op_record", schema = "surgery")
class IntraOpRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "event_name", nullable = false, length = 100) var eventName: String,
  @Column(name = "notes") var notes: String? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "post_op_record", schema = "surgery")
class PostOpRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "patient_condition") var patientCondition: String? = null,
  @Column(name = "destination", length = 30) var destination: String? = null,
  @Column(name = "pain_score") var painScore: Int? = null,
  @Column(name = "drain_output_ml") var drainOutputMl: Int? = null,
  @Column(name = "complications") var complications: String? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()

// 04.06 intra-op consumable -> charge event.
@Entity
@Table(name = "surgical_consumable", schema = "surgery")
class SurgicalConsumable(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "surgery_schedule_id", nullable = false) var surgeryScheduleId: UUID,
  @Column(name = "name", nullable = false, length = 255) var name: String,
  @Column(name = "quantity", nullable = false) var quantity: Int = 1,
  @Column(name = "unit_price_qar", precision = 12, scale = 2) var unitPriceQar: java.math.BigDecimal? = null,
  @Column(name = "charge_event_id") var chargeEventId: UUID? = null,
  @Column(name = "recorded_by") var recordedBy: UUID? = null,
  @Column(name = "recorded_at", nullable = false) var recordedAt: Instant = Instant.now(),
) : BaseEntity()
