package com.squamed.core.bloodbank.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "blood_donor", schema = "bloodbank")
class BloodDonor(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "donor_number", length = 30) var donorNumber: String? = null,
  @Column(name = "full_name", nullable = false, length = 255) var fullName: String,
  @Column(name = "blood_group", nullable = false, length = 5) var bloodGroup: String,
  @Column(name = "gender", length = 20) var gender: String? = null,
  @Column(name = "date_of_birth") var dateOfBirth: LocalDate? = null,
  @Column(name = "phone", length = 30) var phone: String? = null,
  @Column(name = "last_donation_at") var lastDonationAt: Instant? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "active",
  // 04.07 donor deferral.
  @Column(name = "deferral_reason", length = 400) var deferralReason: String? = null,
  @Column(name = "deferred_until") var deferredUntil: Instant? = null,
  @Column(name = "notes") var notes: String? = null,
) : BaseEntity()

@Entity
@Table(name = "blood_unit", schema = "bloodbank")
class BloodUnit(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "unit_number", nullable = false, length = 30) var unitNumber: String,
  @Column(name = "blood_group", nullable = false, length = 5) var bloodGroup: String,
  @Column(name = "component", nullable = false, length = 30) var component: String = "whole_blood",
  @Column(name = "collection_date") var collectionDate: LocalDate? = null,
  @Column(name = "expiry_date", nullable = false) var expiryDate: LocalDate,
  @Column(name = "volume_ml") var volumeMl: Int? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "available",
  @Column(name = "donor_id") var donorId: UUID? = null,
  @Column(name = "storage_location", length = 50) var storageLocation: String? = null,
  @Column(name = "received_at", nullable = false) var receivedAt: Instant = Instant.now(),
  // 04.07 screening hand-off (ttI / NAT).
  @Column(name = "screening_status", nullable = false, length = 10) var screeningStatus: String = "PENDING",
  @Column(name = "tti_result", length = 20) var ttiResult: String? = null,
  @Column(name = "nat_result", length = 20) var natResult: String? = null,
) : BaseEntity()

@Entity
@Table(name = "blood_grouping", schema = "bloodbank")
class BloodGrouping(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "unit_id", nullable = false) var unitId: UUID,
  @Column(name = "abo_result", nullable = false, length = 10) var aboResult: String,
  @Column(name = "rh_result", nullable = false, length = 10) var rhResult: String,
  @Column(name = "confirmed", nullable = false) var confirmed: Boolean = false,
  @Column(name = "confirmed_by") var confirmedBy: UUID? = null,
  @Column(name = "confirmed_at") var confirmedAt: Instant? = null,
  @Column(name = "grouped_by") var groupedBy: UUID? = null,
  @Column(name = "grouped_at", nullable = false) var groupedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "crossmatch", schema = "bloodbank")
class Crossmatch(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "unit_id", nullable = false) var unitId: UUID,
  @Column(name = "technique", nullable = false, length = 30) var technique: String,
  @Column(name = "compatibility", nullable = false, length = 20) var compatibility: String = "equivocal",
  @Column(name = "result_note") var resultNote: String? = null,
  @Column(name = "requested_by") var requestedBy: UUID? = null,
  @Column(name = "performed_by") var performedBy: UUID? = null,
  @Column(name = "performed_at", nullable = false) var performedAt: Instant = Instant.now(),
) : BaseEntity()

@Entity
@Table(name = "blood_reservation", schema = "bloodbank")
class BloodReservation(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "unit_id", nullable = false) var unitId: UUID,
  @Column(name = "reserved_for") var reservedFor: UUID? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "reserved",
  @Column(name = "reserved_at", nullable = false) var reservedAt: Instant = Instant.now(),
  @Column(name = "reserved_by") var reservedBy: UUID? = null,
  @Column(name = "released_at") var releasedAt: Instant? = null,
  @Column(name = "released_by") var releasedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "blood_issue", schema = "bloodbank")
class BloodIssue(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "unit_id", nullable = false) var unitId: UUID,
  @Column(name = "issued_for") var issuedFor: UUID? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "issued",
  @Column(name = "requested_by") var requestedBy: UUID? = null,
  @Column(name = "issued_by") var issuedBy: UUID? = null,
  @Column(name = "issued_at", nullable = false) var issuedAt: Instant = Instant.now(),
  // 04.07 bedside identity check recorded on issue.
  @Column(name = "patient_barcode_verified", nullable = false) var patientBarcodeVerified: Boolean = false,
  @Column(name = "second_identifier_verified", nullable = false) var secondIdentifierVerified: Boolean = false,
) : BaseEntity()

@Entity
@Table(name = "transfusion_record", schema = "bloodbank")
class TransfusionRecord(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "unit_id", nullable = false) var unitId: UUID,
  @Column(name = "transfusion_started_at", nullable = false) var startedAt: Instant = Instant.now(),
  @Column(name = "transfusion_ended_at") var endedAt: Instant? = null,
  @Column(name = "volume_ml") var volumeMl: Int? = null,
  @Column(name = "start_rate_ml_per_hour") var startRateMlPerHour: Int? = null,
  @Column(name = "observations") var observations: String? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "in_progress",
  @Column(name = "performed_by") var performedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "transfusion_reaction", schema = "bloodbank")
class TransfusionReaction(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "transfusion_record_id", nullable = false) var transfusionRecordId: UUID,
  @Column(name = "reaction_type", nullable = false, length = 40) var reactionType: String,
  @Column(name = "severity", nullable = false, length = 20) var severity: String,
  @Column(name = "onset_at", nullable = false) var onsetAt: Instant = Instant.now(),
  @Column(name = "symptoms") var symptoms: String? = null,
  @Column(name = "actions") var actions: String? = null,
  @Column(name = "reported_by") var reportedBy: UUID? = null,
) : BaseEntity()
