package com.squamed.core.bloodbank.service

import com.squamed.core.bloodbank.api.CrossmatchCreateDto
import com.squamed.core.bloodbank.api.CrossmatchResultDto
import com.squamed.core.bloodbank.api.CrossmatchView
import com.squamed.core.bloodbank.api.DonorCreateDto
import com.squamed.core.bloodbank.api.DonorStatusDto
import com.squamed.core.bloodbank.api.DonorView
import com.squamed.core.bloodbank.api.GroupingCreateDto
import com.squamed.core.bloodbank.api.GroupingView
import com.squamed.core.bloodbank.api.IssueCreateDto
import com.squamed.core.bloodbank.api.IssueView
import com.squamed.core.bloodbank.api.ReactionCreateDto
import com.squamed.core.bloodbank.api.ReactionView
import com.squamed.core.bloodbank.api.ReservationCreateDto
import com.squamed.core.bloodbank.api.ReservationView
import com.squamed.core.bloodbank.api.TransfusionCreateDto
import com.squamed.core.bloodbank.api.TransfusionStatusDto
import com.squamed.core.bloodbank.api.TransfusionView
import com.squamed.core.bloodbank.api.UnitCreateDto
import com.squamed.core.bloodbank.api.UnitStatusDto
import com.squamed.core.bloodbank.api.UnitView
import com.squamed.core.bloodbank.domain.BloodDonor
import com.squamed.core.bloodbank.domain.BloodGrouping
import com.squamed.core.bloodbank.domain.BloodIssue
import com.squamed.core.bloodbank.domain.BloodReservation
import com.squamed.core.bloodbank.domain.BloodUnit
import com.squamed.core.bloodbank.domain.Crossmatch
import com.squamed.core.bloodbank.domain.TransfusionReaction
import com.squamed.core.bloodbank.domain.TransfusionRecord
import com.squamed.core.bloodbank.repository.BloodDonorRepository
import com.squamed.core.bloodbank.repository.BloodGroupingRepository
import com.squamed.core.bloodbank.repository.BloodIssueRepository
import com.squamed.core.bloodbank.repository.BloodReservationRepository
import com.squamed.core.bloodbank.repository.BloodUnitRepository
import com.squamed.core.bloodbank.repository.CrossmatchRepository
import com.squamed.core.bloodbank.repository.TransfusionReactionRepository
import com.squamed.core.bloodbank.repository.TransfusionRecordRepository
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class BloodBankService(
  private val donorRepository: BloodDonorRepository,
  private val unitRepository: BloodUnitRepository,
  private val groupingRepository: BloodGroupingRepository,
  private val crossmatchRepository: CrossmatchRepository,
  private val reservationRepository: BloodReservationRepository,
  private val issueRepository: BloodIssueRepository,
  private val transfusionRepository: TransfusionRecordRepository,
  private val reactionRepository: TransfusionReactionRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun createDonor(
    dto: DonorCreateDto,
    actorId: UUID,
  ): DonorView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val fullName = dto.fullName ?: throw IllegalArgumentException("BLOODBANK_DONOR_NAME_REQUIRED")
    val bloodGroup = dto.bloodGroup ?: throw IllegalArgumentException("BLOODBANK_BLOOD_GROUP_REQUIRED")
    if (bloodGroup !in BLOOD_GROUPS) throw IllegalArgumentException("BLOODBANK_INVALID_BLOOD_GROUP")
    val donor =
      donorRepository.save(
        BloodDonor(
          hospitalId = hospitalId,
          donorNumber = dto.donorNumber,
          fullName = fullName,
          bloodGroup = bloodGroup,
          gender = dto.gender,
          dateOfBirth = dto.dateOfBirth,
          phone = dto.phone,
          notes = dto.notes,
        ).also { it.createdBy = actorId },
      )
    return DonorView.from(donor)
  }

  @Transactional
  fun updateDonorStatus(
    id: UUID,
    dto: DonorStatusDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): DonorView {
    val donor = findDonor(id, hospitalId)
    val status = dto.status ?: throw IllegalArgumentException("BLOODBANK_DONOR_STATUS_REQUIRED")
    if (status !in DONOR_STATUSES) throw IllegalArgumentException("BLOODBANK_DONOR_INVALID_STATUS")
    donor.status = status
    donor.updatedBy = actorId
    return DonorView.from(donorRepository.save(donor))
  }

  @Transactional(readOnly = true)
  fun listDonors(
    status: String?,
    bloodGroup: String?,
    hospitalId: UUID?,
  ): List<DonorView> {
    val rows =
      when {
        status != null -> donorRepository.findByStatus(hospitalId, status)
        bloodGroup != null -> donorRepository.findByBloodGroup(hospitalId, bloodGroup)
        else -> donorRepository.findAllForHospital(hospitalId)
      }
    return rows.map { DonorView.from(it) }
  }

  @Transactional
  fun createUnit(
    dto: UnitCreateDto,
    actorId: UUID,
  ): UnitView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val unitNumber = dto.unitNumber ?: throw IllegalArgumentException("BLOODBANK_UNIT_NUMBER_REQUIRED")
    val bloodGroup = dto.bloodGroup ?: throw IllegalArgumentException("BLOODBANK_BLOOD_GROUP_REQUIRED")
    if (bloodGroup !in BLOOD_GROUPS) throw IllegalArgumentException("BLOODBANK_INVALID_BLOOD_GROUP")
    val component = dto.component ?: "whole_blood"
    if (component !in COMPONENTS) throw IllegalArgumentException("BLOODBANK_INVALID_COMPONENT")
    val expiry = dto.expiryDate ?: throw IllegalArgumentException("BLOODBANK_EXPIRY_REQUIRED")
    if (!expiry.isAfter(LocalDate.now())) throw IllegalArgumentException("BLOODBANK_EXPIRY_IN_PAST")
    if (dto.donorId != null) findDonor(dto.donorId, hospitalId)
    val unit =
      unitRepository.save(
        BloodUnit(
          hospitalId = hospitalId,
          unitNumber = unitNumber,
          bloodGroup = bloodGroup,
          component = component,
          collectionDate = dto.collectionDate,
          expiryDate = expiry,
          volumeMl = dto.volumeMl,
          donorId = dto.donorId,
          storageLocation = dto.storageLocation,
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "bloodbank.unit_received",
        occurredAt = Instant.now(),
        source = "bloodbank-service",
        entityType = "blood_unit",
        entityId = unit.id,
        hospitalId = hospitalId,
        payload = mapOf("bloodGroup" to bloodGroup, "component" to component),
      ),
    )
    return UnitView.from(unit)
  }

  @Transactional
  fun updateUnitStatus(
    id: UUID,
    dto: UnitStatusDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): UnitView {
    val unit = findUnit(id, hospitalId)
    val status = dto.status ?: throw IllegalArgumentException("BLOODBANK_UNIT_STATUS_REQUIRED")
    if (status !in UNIT_STATUSES) throw IllegalArgumentException("BLOODBANK_UNIT_INVALID_STATUS")
    if (unit.status == "issued") throw IllegalArgumentException("BLOODBANK_UNIT_ISSUED_LOCKED")
    if (status == "issued") throw IllegalArgumentException("BLOODBANK_UNIT_ISSUED_VIA_ISSUE")
    unit.status = status
    unit.updatedBy = actorId
    return UnitView.from(unitRepository.save(unit))
  }

  @Transactional(readOnly = true)
  fun listUnits(
    status: String?,
    bloodGroup: String?,
    component: String?,
    hospitalId: UUID?,
  ): List<UnitView> {
    val rows =
      when {
        status != null -> unitRepository.findByStatus(hospitalId, status)
        bloodGroup != null -> unitRepository.findByBloodGroup(hospitalId, bloodGroup)
        component != null -> unitRepository.findByComponent(hospitalId, component)
        else -> unitRepository.findAllForHospital(hospitalId)
      }
    return rows.map { UnitView.from(it) }
  }

  @Transactional
  fun addGrouping(
    dto: GroupingCreateDto,
    actorId: UUID,
  ): GroupingView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val unitId = dto.unitId ?: throw IllegalArgumentException("BLOODBANK_UNIT_REQUIRED")
    findUnit(unitId, hospitalId)
    if (groupingRepository.findByUnitId(unitId) != null) throw IllegalArgumentException("BLOODBANK_ALREADY_GROUPED")
    val aboResult = dto.aboResult ?: throw IllegalArgumentException("BLOODBANK_ABO_REQUIRED")
    val rhResult = dto.rhResult ?: throw IllegalArgumentException("BLOODBANK_RH_REQUIRED")
    if (aboResult !in ABO_RESULTS) throw IllegalArgumentException("BLOODBANK_INVALID_ABO")
    if (rhResult !in RH_RESULTS) throw IllegalArgumentException("BLOODBANK_INVALID_RH")
    val grouping =
      groupingRepository.save(
        BloodGrouping(
          hospitalId = hospitalId,
          unitId = unitId,
          aboResult = aboResult,
          rhResult = rhResult,
          groupedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return GroupingView.from(grouping)
  }

  @Transactional
  fun confirmGrouping(
    id: UUID,
    actorId: UUID,
    hospitalId: UUID?,
  ): GroupingView {
    val grouping = findGrouping(id, hospitalId)
    if (grouping.confirmed) throw IllegalArgumentException("BLOODBANK_GROUPING_CONFIRMED")
    grouping.confirmed = true
    grouping.confirmedBy = actorId
    grouping.confirmedAt = Instant.now()
    grouping.updatedBy = actorId
    return GroupingView.from(groupingRepository.save(grouping))
  }

  @Transactional(readOnly = true)
  fun getGrouping(
    unitId: UUID,
    hospitalId: UUID?,
  ): GroupingView {
    val grouping = groupingRepository.findByUnitId(unitId) ?: throw IllegalArgumentException("BLOODBANK_GROUPING_NOT_FOUND")
    requireSameHospital(hospitalId, grouping.hospitalId) // 00.04 tenant sweep
    return GroupingView.from(grouping)
  }

  @Transactional
  fun createCrossmatch(
    dto: CrossmatchCreateDto,
    actorId: UUID,
  ): CrossmatchView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val patientId = dto.patientId ?: throw IllegalArgumentException("BLOODBANK_PATIENT_REQUIRED")
    val unitId = dto.unitId ?: throw IllegalArgumentException("BLOODBANK_UNIT_REQUIRED")
    findUnit(unitId, hospitalId)
    val technique = dto.technique ?: throw IllegalArgumentException("BLOODBANK_TECHNIQUE_REQUIRED")
    if (technique !in TECHNIQUES) throw IllegalArgumentException("BLOODBANK_INVALID_TECHNIQUE")
    val crossmatch =
      crossmatchRepository.save(
        Crossmatch(
          hospitalId = hospitalId,
          patientId = patientId,
          unitId = unitId,
          technique = technique,
          requestedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    return CrossmatchView.from(crossmatch)
  }

  @Transactional
  fun updateCrossmatchResult(
    id: UUID,
    dto: CrossmatchResultDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): CrossmatchView {
    val crossmatch = findCrossmatch(id, hospitalId)
    val compatibility = dto.compatibility ?: throw IllegalArgumentException("BLOODBANK_COMPATIBILITY_REQUIRED")
    if (compatibility !in COMPATIBILITIES) throw IllegalArgumentException("BLOODBANK_INVALID_COMPATIBILITY")
    crossmatch.compatibility = compatibility
    crossmatch.resultNote = dto.resultNote
    crossmatch.performedBy = actorId
    crossmatch.performedAt = Instant.now()
    crossmatch.updatedBy = actorId
    return CrossmatchView.from(crossmatchRepository.save(crossmatch))
  }

  @Transactional(readOnly = true)
  fun listCrossmatches(
    patientId: UUID?,
    unitId: UUID?,
    hospitalId: UUID?,
  ): List<CrossmatchView> {
    val rows =
      when {
        patientId != null -> crossmatchRepository.findByPatientId(hospitalId, patientId)
        unitId != null -> crossmatchRepository.findByUnitId(hospitalId, unitId)
        else -> crossmatchRepository.findAllForHospital(hospitalId)
      }
    return rows.map { CrossmatchView.from(it) }
  }

  @Transactional
  fun reserve(
    dto: ReservationCreateDto,
    actorId: UUID,
  ): ReservationView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val patientId = dto.patientId ?: throw IllegalArgumentException("BLOODBANK_PATIENT_REQUIRED")
    val unitId = dto.unitId ?: throw IllegalArgumentException("BLOODBANK_UNIT_REQUIRED")
    val unit = findUnit(unitId, hospitalId)
    if (unit.status != "available") throw IllegalArgumentException("BLOODBANK_UNIT_NOT_AVAILABLE")
    unit.status = "reserved"
    unit.updatedBy = actorId
    unitRepository.save(unit)
    return ReservationView.from(
      reservationRepository.save(
        BloodReservation(
          hospitalId = hospitalId,
          patientId = patientId,
          unitId = unitId,
          reservedFor = dto.reservedFor,
          reservedBy = actorId,
        ).also { it.createdBy = actorId },
      ),
    )
  }

  @Transactional
  fun releaseReservation(
    unitId: UUID,
    actorId: UUID,
    hospitalId: UUID?,
  ): ReservationView {
    val reservation =
      reservationRepository
        .findByUnitId(unitId)
        .filter { it.status == "reserved" }
        .maxByOrNull { it.reservedAt }
        ?: throw IllegalArgumentException("BLOODBANK_NO_ACTIVE_RESERVATION")
    requireSameHospital(hospitalId, reservation.hospitalId) // 00.04 tenant sweep
    val unit = findUnit(unitId, hospitalId)
    unit.status = "available"
    unit.updatedBy = actorId
    unitRepository.save(unit)
    reservation.status = "released"
    reservation.releasedAt = Instant.now()
    reservation.releasedBy = actorId
    reservation.updatedBy = actorId
    return ReservationView.from(reservationRepository.save(reservation))
  }

  @Transactional
  fun issue(
    dto: IssueCreateDto,
    actorId: UUID,
  ): IssueView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val patientId = dto.patientId ?: throw IllegalArgumentException("BLOODBANK_PATIENT_REQUIRED")
    val unitId = dto.unitId ?: throw IllegalArgumentException("BLOODBANK_UNIT_REQUIRED")
    val unit = findUnit(unitId, hospitalId)
    if (unit.status !in setOf("available", "reserved")) throw IllegalArgumentException("BLOODBANK_UNIT_NOT_ISSUABLE")
    unit.status = "issued"
    unit.updatedBy = actorId
    unitRepository.save(unit)
    val issueRecord =
      issueRepository.save(
        BloodIssue(
          hospitalId = hospitalId,
          patientId = patientId,
          unitId = unitId,
          issuedFor = dto.issuedFor,
          issuedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "bloodbank.unit_issued",
        occurredAt = Instant.now(),
        source = "bloodbank-service",
        entityType = "blood_issue",
        entityId = issueRecord.id,
        hospitalId = hospitalId,
        payload = mapOf("patientId" to patientId, "unitId" to unitId),
      ),
    )
    return IssueView.from(issueRecord)
  }

  @Transactional(readOnly = true)
  fun listIssues(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<IssueView> {
    val rows =
      if (patientId != null) {
        issueRepository.findByPatientId(hospitalId, patientId)
      } else {
        issueRepository.findAllForHospital(hospitalId)
      }
    return rows.map { IssueView.from(it) }
  }

  @Transactional
  fun startTransfusion(
    dto: TransfusionCreateDto,
    actorId: UUID,
  ): TransfusionView {
    val hospitalId = dto.hospitalId ?: throw IllegalArgumentException("BLOODBANK_HOSPITAL_REQUIRED")
    val patientId = dto.patientId ?: throw IllegalArgumentException("BLOODBANK_PATIENT_REQUIRED")
    val unitId = dto.unitId ?: throw IllegalArgumentException("BLOODBANK_UNIT_REQUIRED")
    val unit = findUnit(unitId, hospitalId)
    if (unit.status != "issued") throw IllegalArgumentException("BLOODBANK_UNIT_NOT_ISSUED")
    val transfusion =
      transfusionRepository.save(
        TransfusionRecord(
          hospitalId = hospitalId,
          patientId = patientId,
          unitId = unitId,
          volumeMl = dto.volumeMl,
          startRateMlPerHour = dto.startRateMlPerHour,
          observations = dto.observations,
          performedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "bloodbank.transfusion_started",
        occurredAt = Instant.now(),
        source = "bloodbank-service",
        entityType = "transfusion_record",
        entityId = transfusion.id,
        hospitalId = hospitalId,
        payload = mapOf("patientId" to patientId, "unitId" to unitId),
      ),
    )
    return TransfusionView.from(transfusion)
  }

  @Transactional
  fun updateTransfusionStatus(
    id: UUID,
    dto: TransfusionStatusDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): TransfusionView {
    val transfusion = findTransfusion(id, hospitalId)
    val status = dto.status ?: throw IllegalArgumentException("BLOODBANK_TRANSFUSION_STATUS_REQUIRED")
    if (status !in TRANSFUSION_STATUSES) throw IllegalArgumentException("BLOODBANK_TRANSFUSION_INVALID_STATUS")
    if (transfusion.status != "in_progress") throw IllegalArgumentException("BLOODBANK_TRANSFUSION_FINISHED")
    transfusion.status = status
    transfusion.endedAt = Instant.now()
    transfusion.updatedBy = actorId
    val saved = transfusionRepository.save(transfusion)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "bloodbank.transfusion_${status.lowercase().replace("_", "-")}",
        occurredAt = Instant.now(),
        source = "bloodbank-service",
        entityType = "transfusion_record",
        entityId = saved.id,
        hospitalId = saved.hospitalId,
        payload = mapOf("patientId" to saved.patientId),
      ),
    )
    return TransfusionView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listTransfusions(
    patientId: UUID?,
    hospitalId: UUID?,
  ): List<TransfusionView> {
    val rows =
      if (patientId != null) {
        transfusionRepository.findByPatientId(hospitalId, patientId)
      } else {
        transfusionRepository.findAllForHospital(hospitalId)
      }
    return rows.map { TransfusionView.from(it) }
  }

  @Transactional
  fun reportReaction(
    transfusionId: UUID,
    dto: ReactionCreateDto,
    actorId: UUID,
    hospitalId: UUID?,
  ): ReactionView {
    val transfusion = findTransfusion(transfusionId, hospitalId)
    val reactionType = dto.reactionType ?: throw IllegalArgumentException("BLOODBANK_REACTION_TYPE_REQUIRED")
    if (reactionType !in REACTION_TYPES) throw IllegalArgumentException("BLOODBANK_INVALID_REACTION_TYPE")
    val severity = dto.severity ?: throw IllegalArgumentException("BLOODBANK_REACTION_SEVERITY_REQUIRED")
    if (severity !in SEVERITIES) throw IllegalArgumentException("BLOODBANK_INVALID_SEVERITY")
    val reaction =
      reactionRepository.save(
        TransfusionReaction(
          hospitalId = transfusion.hospitalId,
          patientId = transfusion.patientId,
          transfusionRecordId = transfusionId,
          reactionType = reactionType,
          severity = severity,
          symptoms = dto.symptoms,
          actions = dto.actions,
          reportedBy = actorId,
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "bloodbank.transfusion_reaction",
        occurredAt = Instant.now(),
        source = "bloodbank-service",
        entityType = "transfusion_reaction",
        entityId = reaction.id,
        hospitalId = transfusion.hospitalId,
        payload = mapOf("patientId" to transfusion.patientId, "reactionType" to reactionType, "severity" to severity),
      ),
    )
    return ReactionView.from(reaction)
  }

  @Transactional(readOnly = true)
  fun listReactions(
    transfusionId: UUID,
    hospitalId: UUID?,
  ): List<ReactionView> {
    findTransfusion(transfusionId, hospitalId)
    return reactionRepository.findByTransfusionRecordId(transfusionId).map { ReactionView.from(it) }
  }

  private fun findDonor(
    id: UUID,
    hospitalId: UUID?,
  ): BloodDonor {
    val donor = donorRepository.findById(id).orElseThrow { IllegalArgumentException("BLOODBANK_DONOR_NOT_FOUND") }
    requireSameHospital(hospitalId, donor.hospitalId) // 00.04 tenant sweep
    return donor
  }

  private fun findUnit(
    id: UUID,
    hospitalId: UUID?,
  ): BloodUnit {
    val unit = unitRepository.findById(id).orElseThrow { IllegalArgumentException("BLOODBANK_UNIT_NOT_FOUND") }
    requireSameHospital(hospitalId, unit.hospitalId) // 00.04 tenant sweep
    return unit
  }

  private fun findGrouping(
    id: UUID,
    hospitalId: UUID?,
  ): BloodGrouping {
    val grouping = groupingRepository.findById(id).orElseThrow { IllegalArgumentException("BLOODBANK_GROUPING_NOT_FOUND") }
    requireSameHospital(hospitalId, grouping.hospitalId) // 00.04 tenant sweep
    return grouping
  }

  private fun findCrossmatch(
    id: UUID,
    hospitalId: UUID?,
  ): Crossmatch {
    val crossmatch = crossmatchRepository.findById(id).orElseThrow { IllegalArgumentException("BLOODBANK_CROSSMATCH_NOT_FOUND") }
    requireSameHospital(hospitalId, crossmatch.hospitalId) // 00.04 tenant sweep
    return crossmatch
  }

  private fun findTransfusion(
    id: UUID,
    hospitalId: UUID?,
  ): TransfusionRecord {
    val transfusion = transfusionRepository.findById(id).orElseThrow { IllegalArgumentException("BLOODBANK_TRANSFUSION_NOT_FOUND") }
    requireSameHospital(hospitalId, transfusion.hospitalId) // 00.04 tenant sweep
    return transfusion
  }

  companion object {
    val BLOOD_GROUPS = setOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    val COMPONENTS = setOf("whole_blood", "prbc", "ffp", "platelets", "cryoprecipitate")
    val UNIT_STATUSES = setOf("available", "reserved", "expired", "discarded")
    val DONOR_STATUSES = setOf("active", "deferred", "retired")
    val ABO_RESULTS = setOf("A", "B", "AB", "O")
    val RH_RESULTS = setOf("positive", "negative")
    val TECHNIQUES = setOf("saline", "antiglobulin", "gel")
    val COMPATIBILITIES = setOf("compatible", "incompatible", "equivocal")
    val TRANSFUSION_STATUSES = setOf("completed", "stopped")
    val REACTION_TYPES = setOf("febrile", "allergic", "hemolytic", "tac", "taco", "other")
    val SEVERITIES = setOf("mild", "moderate", "severe")
  }
}
