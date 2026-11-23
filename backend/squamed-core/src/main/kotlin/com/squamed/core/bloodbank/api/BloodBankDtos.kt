package com.squamed.core.bloodbank.api

import com.squamed.core.bloodbank.domain.BloodDonor
import com.squamed.core.bloodbank.domain.BloodGrouping
import com.squamed.core.bloodbank.domain.BloodIssue
import com.squamed.core.bloodbank.domain.BloodReservation
import com.squamed.core.bloodbank.domain.BloodUnit
import com.squamed.core.bloodbank.domain.Crossmatch
import com.squamed.core.bloodbank.domain.TransfusionReaction
import com.squamed.core.bloodbank.domain.TransfusionRecord
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DonorCreateDto(
  val hospitalId: UUID? = null,
  val donorNumber: String? = null,
  val fullName: String? = null,
  val bloodGroup: String? = null,
  val gender: String? = null,
  val dateOfBirth: LocalDate? = null,
  val phone: String? = null,
  val notes: String? = null,
)

data class DonorStatusDto(
  val status: String? = null,
)

data class UnitCreateDto(
  val hospitalId: UUID? = null,
  val unitNumber: String? = null,
  val bloodGroup: String? = null,
  val component: String? = null,
  val collectionDate: LocalDate? = null,
  val expiryDate: LocalDate? = null,
  val volumeMl: Int? = null,
  val donorId: UUID? = null,
  val storageLocation: String? = null,
)

data class UnitStatusDto(
  val status: String? = null,
)

data class GroupingCreateDto(
  val hospitalId: UUID? = null,
  val unitId: UUID? = null,
  val aboResult: String? = null,
  val rhResult: String? = null,
)

data class CrossmatchCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val unitId: UUID? = null,
  val technique: String? = null,
)

data class CrossmatchResultDto(
  val compatibility: String? = null,
  val resultNote: String? = null,
)

data class ReservationCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val unitId: UUID? = null,
  val reservedFor: UUID? = null,
)

data class IssueCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val unitId: UUID? = null,
  val issuedFor: UUID? = null,
  // 04.07 bedside two-identifier check.
  val patientBarcode: String? = null,
  val confirmPatientName: String? = null,
)

data class TransfusionCreateDto(
  val hospitalId: UUID? = null,
  val patientId: UUID? = null,
  val unitId: UUID? = null,
  val volumeMl: Int? = null,
  val startRateMlPerHour: Int? = null,
  val observations: String? = null,
)

data class TransfusionStatusDto(
  val status: String? = null,
)

data class ReactionCreateDto(
  val reactionType: String? = null,
  val severity: String? = null,
  val symptoms: String? = null,
  val actions: String? = null,
)

data class DonorView(
  val id: UUID?,
  val hospitalId: UUID?,
  val donorNumber: String?,
  val fullName: String,
  val bloodGroup: String,
  val gender: String?,
  val dateOfBirth: LocalDate?,
  val phone: String?,
  val lastDonationAt: Instant?,
  val status: String,
  val notes: String?,
  val deferralReason: String?,
  val deferredUntil: Instant?,
) {
  companion object {
    fun from(entity: BloodDonor) =
      DonorView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        donorNumber = entity.donorNumber,
        fullName = entity.fullName,
        bloodGroup = entity.bloodGroup,
        gender = entity.gender,
        dateOfBirth = entity.dateOfBirth,
        phone = entity.phone,
        lastDonationAt = entity.lastDonationAt,
        status = entity.status,
        notes = entity.notes,
        deferralReason = entity.deferralReason,
        deferredUntil = entity.deferredUntil,
      )
  }
}

data class UnitView(
  val id: UUID?,
  val hospitalId: UUID?,
  val unitNumber: String,
  val bloodGroup: String,
  val component: String,
  val collectionDate: LocalDate?,
  val expiryDate: LocalDate?,
  val volumeMl: Int?,
  val status: String,
  val donorId: UUID?,
  val storageLocation: String?,
  val receivedAt: Instant?,
  val screeningStatus: String,
) {
  companion object {
    fun from(entity: BloodUnit) =
      UnitView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        unitNumber = entity.unitNumber,
        bloodGroup = entity.bloodGroup,
        component = entity.component,
        collectionDate = entity.collectionDate,
        expiryDate = entity.expiryDate,
        volumeMl = entity.volumeMl,
        status = entity.status,
        donorId = entity.donorId,
        storageLocation = entity.storageLocation,
        receivedAt = entity.receivedAt,
        screeningStatus = entity.screeningStatus,
      )
  }
}

data class GroupingView(
  val id: UUID?,
  val hospitalId: UUID?,
  val unitId: UUID?,
  val aboResult: String,
  val rhResult: String,
  val confirmed: Boolean,
  val confirmedBy: UUID?,
  val confirmedAt: Instant?,
  val groupedBy: UUID?,
  val groupedAt: Instant?,
) {
  companion object {
    fun from(entity: BloodGrouping) =
      GroupingView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        unitId = entity.unitId,
        aboResult = entity.aboResult,
        rhResult = entity.rhResult,
        confirmed = entity.confirmed,
        confirmedBy = entity.confirmedBy,
        confirmedAt = entity.confirmedAt,
        groupedBy = entity.groupedBy,
        groupedAt = entity.groupedAt,
      )
  }
}

data class CrossmatchView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val unitId: UUID?,
  val technique: String,
  val compatibility: String,
  val resultNote: String?,
  val requestedBy: UUID?,
  val performedBy: UUID?,
  val performedAt: Instant?,
) {
  companion object {
    fun from(entity: Crossmatch) =
      CrossmatchView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        unitId = entity.unitId,
        technique = entity.technique,
        compatibility = entity.compatibility,
        resultNote = entity.resultNote,
        requestedBy = entity.requestedBy,
        performedBy = entity.performedBy,
        performedAt = entity.performedAt,
      )
  }
}

data class ReservationView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val unitId: UUID?,
  val reservedFor: UUID?,
  val status: String,
  val reservedAt: Instant?,
  val reservedBy: UUID?,
  val releasedAt: Instant?,
  val releasedBy: UUID?,
) {
  companion object {
    fun from(entity: BloodReservation) =
      ReservationView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        unitId = entity.unitId,
        reservedFor = entity.reservedFor,
        status = entity.status,
        reservedAt = entity.reservedAt,
        reservedBy = entity.reservedBy,
        releasedAt = entity.releasedAt,
        releasedBy = entity.releasedBy,
      )
  }
}

data class IssueView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val unitId: UUID?,
  val issuedFor: UUID?,
  val status: String,
  val requestedBy: UUID?,
  val issuedBy: UUID?,
  val issuedAt: Instant?,
) {
  companion object {
    fun from(entity: BloodIssue) =
      IssueView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        unitId = entity.unitId,
        issuedFor = entity.issuedFor,
        status = entity.status,
        requestedBy = entity.requestedBy,
        issuedBy = entity.issuedBy,
        issuedAt = entity.issuedAt,
      )
  }
}

data class TransfusionView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val unitId: UUID?,
  val startedAt: Instant?,
  val endedAt: Instant?,
  val volumeMl: Int?,
  val startRateMlPerHour: Int?,
  val observations: String?,
  val status: String,
  val performedBy: UUID?,
) {
  companion object {
    fun from(entity: TransfusionRecord) =
      TransfusionView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        unitId = entity.unitId,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
        volumeMl = entity.volumeMl,
        startRateMlPerHour = entity.startRateMlPerHour,
        observations = entity.observations,
        status = entity.status,
        performedBy = entity.performedBy,
      )
  }
}

data class ReactionView(
  val id: UUID?,
  val hospitalId: UUID?,
  val patientId: UUID?,
  val transfusionRecordId: UUID?,
  val reactionType: String,
  val severity: String,
  val onsetAt: Instant?,
  val symptoms: String?,
  val actions: String?,
  val reportedBy: UUID?,
) {
  companion object {
    fun from(entity: TransfusionReaction) =
      ReactionView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        patientId = entity.patientId,
        transfusionRecordId = entity.transfusionRecordId,
        reactionType = entity.reactionType,
        severity = entity.severity,
        onsetAt = entity.onsetAt,
        symptoms = entity.symptoms,
        actions = entity.actions,
        reportedBy = entity.reportedBy,
      )
  }
}

// 04.07 donor deferral, unit screening, traceability.
data class DonorDeferralDto(
  val reason: String,
  val until: Instant? = null,
)

data class UnitScreeningDto(
  val ttiResult: String,
  val natResult: String? = null,
)

data class TraceabilityView(
  val unitId: UUID,
  val unitNumber: String,
  val bloodGroup: String,
  val component: String,
  val status: String,
  val screeningStatus: String,
  val donorId: UUID?,
  val donorName: String?,
  val grouping: Map<String, Any?>?,
  val crossmatches: List<Map<String, Any?>>,
  val reservations: List<Map<String, Any?>>,
  val issue: Map<String, Any?>?,
  val transfusions: List<Map<String, Any?>>,
  val reactions: List<Map<String, Any?>>,
)
