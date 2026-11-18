package com.squamed.core.patient.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.patient.api.AllergyDto
import com.squamed.core.patient.api.MedicalHistoryDto
import com.squamed.core.patient.api.MergeLogDto
import com.squamed.core.patient.api.MergeResponseDto
import com.squamed.core.patient.api.NoteDto
import com.squamed.core.patient.api.PatientCreateDto
import com.squamed.core.patient.api.PatientDetailDto
import com.squamed.core.patient.api.PatientDetailSubresources
import com.squamed.core.patient.api.PatientIdentifierDto
import com.squamed.core.patient.api.PatientRelationshipDto
import com.squamed.core.patient.api.PatientSummaryDto
import com.squamed.core.patient.api.PatientUpdateDto
import com.squamed.core.patient.domain.Address
import com.squamed.core.patient.domain.Allergy
import com.squamed.core.patient.domain.ContactPoint
import com.squamed.core.patient.domain.MedicalHistory
import com.squamed.core.patient.domain.PatientIdentifier
import com.squamed.core.patient.domain.PatientMergeLog
import com.squamed.core.patient.domain.PatientNote
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.domain.PatientRelationship
import com.squamed.core.patient.repository.AddressRepository
import com.squamed.core.patient.repository.AllergyRepository
import com.squamed.core.patient.repository.ContactPointRepository
import com.squamed.core.patient.repository.EpisodeOfCareRepository
import com.squamed.core.patient.repository.MedicalHistoryRepository
import com.squamed.core.patient.repository.PatientIdentifierRepository
import com.squamed.core.patient.repository.PatientMergeLogRepository
import com.squamed.core.patient.repository.PatientNoteRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.patient.repository.PatientRelationshipRepository
import com.squamed.core.search.SearchIndexer
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedHashMap
import java.util.UUID

@Service
class PatientService(
  private val patientRecordRepository: PatientRecordRepository,
  private val patientIdentifierRepository: PatientIdentifierRepository,
  private val contactPointRepository: ContactPointRepository,
  private val addressRepository: AddressRepository,
  private val allergyRepository: AllergyRepository,
  private val patientNoteRepository: PatientNoteRepository,
  private val medicalHistoryRepository: MedicalHistoryRepository,
  private val episodeOfCareRepository: EpisodeOfCareRepository,
  private val patientRelationshipRepository: PatientRelationshipRepository,
  private val patientMergeLogRepository: PatientMergeLogRepository,
  private val mrnGenerator: MrnGenerator,
  private val eventPublisher: DomainEventPublisher,
  private val searchIndexer: SearchIndexer,
  private val photoStorage: PatientPhotoStorage,
  private val objectMapper: ObjectMapper,
) {
  private val logger = LoggerFactory.getLogger(PatientService::class.java)

  companion object {
    /** Sentinel DOB for unknown patients (documented in V46 header) — replaced on identification. */
    val UNKNOWN_SENTINEL_DOB: LocalDate = LocalDate.of(1900, 1, 1)
  }

  @Transactional
  fun create(dto: PatientCreateDto): PatientDetailDto {
    val mrn = resolveMrn(dto)
    val unknown = dto.unknownPatient ?: false
    val patient =
      patientRecordRepository.save(
        PatientRecord(
          hospitalId = dto.hospitalId,
          mrn = mrn,
          externalId = dto.externalId,
          title = dto.title,
          firstName =
            when {
              dto.firstName != null -> dto.firstName
              unknown -> "Unknown"
              else -> throw IllegalArgumentException("PATIENT_FIRST_NAME_REQUIRED")
            },
          middleName = dto.middleName,
          lastName =
            when {
              dto.lastName != null -> dto.lastName
              unknown -> "Unknown"
              else -> throw IllegalArgumentException("PATIENT_LAST_NAME_REQUIRED")
            },
          preferredName = dto.preferredName,
          dateOfBirth = dto.dateOfBirth ?: if (unknown) UNKNOWN_SENTINEL_DOB else throw IllegalArgumentException("PATIENT_DATE_OF_BIRTH_REQUIRED"),
          gender = dto.gender ?: if (unknown) "unknown" else throw IllegalArgumentException("PATIENT_GENDER_REQUIRED"),
          bloodGroup = dto.bloodGroup,
          nationality = dto.nationality,
          religion = dto.religion,
          maritalStatus = dto.maritalStatus,
          occupation = dto.occupation,
          photoUrl = dto.photoUrl,
          phone = dto.phone,
          alternatePhone = dto.alternatePhone,
          email = dto.email,
          addressLine1 = dto.addressLine1,
          addressLine2 = dto.addressLine2,
          city = dto.city,
          country = dto.country,
          postalCode = dto.postalCode,
          emergencyContactName = dto.emergencyContactName,
          emergencyContactPhone = dto.emergencyContactPhone,
          emergencyContactRelation = dto.emergencyContactRelation,
          qid = dto.qid,
          passportNumber = dto.passportNumber,
          idType = dto.idType,
          idNumber = dto.idNumber,
          insuranceCompany = dto.insuranceCompany,
          insuranceMemberId = dto.insuranceMemberId,
          insurancePolicyNo = dto.insurancePolicyNo,
          insurancePlan = dto.insurancePlan,
          isDeceased = dto.isDeceased ?: false,
          deathDate = dto.deathDate,
          deceasedNote = dto.deceasedNote,
          languagePreference = dto.languagePreference ?: "en",
          communicationPreference = dto.communicationPreference ?: "phone",
          registrationChannel = dto.registrationChannel ?: "walk-in",
          recordsStatus = dto.recordsStatus ?: if (unknown) "pending" else "active",
          isUnknown = unknown,
        ),
      )
    val patientId = patient.id ?: throw IllegalStateException("PATIENT_ID_MISSING")
    searchIndexer.indexPatient(patient)

    for (input in dto.identifiers.orEmpty()) {
      val idType = input.idType ?: throw IllegalArgumentException("IDENTIFIER_TYPE_REQUIRED")
      val idValue = input.idValue ?: throw IllegalArgumentException("IDENTIFIER_VALUE_REQUIRED")
      validateIdentifier(idType, idValue.trim())
      try {
        patientIdentifierRepository.save(
          PatientIdentifier(
            patientId = patientId,
            idType = idType,
            idValue = idValue.trim(),
            issuingAuthority = input.issuingAuthority,
            primaryFlag = input.primaryFlag ?: false,
          ),
        )
      } catch (e: DataIntegrityViolationException) {
        throw IllegalStateException("DUPLICATE_IDENTIFIER")
      }
    }

    for (input in dto.contactPoints.orEmpty()) {
      val type = input.type ?: throw IllegalArgumentException("CONTACT_TYPE_REQUIRED")
      val value = input.value ?: throw IllegalArgumentException("CONTACT_VALUE_REQUIRED")
      contactPointRepository.save(
        ContactPoint(
          patientId = patientId,
          type = type,
          value = value.trim(),
          purpose = input.purpose,
          isPrimary = input.isPrimary ?: false,
        ),
      )
    }

    for (input in dto.addresses.orEmpty()) {
      addressRepository.save(
        Address(
          patientId = patientId,
          type = input.type ?: "home",
          line1 = input.line1,
          line2 = input.line2,
          city = input.city,
          stateProvince = input.stateProvince,
          country = input.country,
          postalCode = input.postalCode,
          isPrimary = input.isPrimary ?: true,
        ),
      )
    }

    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "patient.created",
        occurredAt = Instant.now(),
        source = "patient-service",
        entityType = "patient",
        entityId = patientId,
        hospitalId = patient.hospitalId,
        payload =
          mapOf(
            "firstName" to patient.firstName,
            "lastName" to patient.lastName,
            "mrn" to patient.mrn,
          ),
      ),
    )

    return getDetail(patientId)
  }

  @Transactional
  fun update(
    id: UUID,
    dto: PatientUpdateDto,
  ): PatientDetailDto {
    val patient = findPatient(id)
    patient.externalId = dto.externalId ?: patient.externalId
    patient.title = dto.title ?: patient.title
    patient.firstName = dto.firstName ?: patient.firstName
    patient.middleName = dto.middleName ?: patient.middleName
    patient.lastName = dto.lastName ?: patient.lastName
    patient.preferredName = dto.preferredName ?: patient.preferredName
    patient.dateOfBirth = dto.dateOfBirth ?: patient.dateOfBirth
    patient.gender = dto.gender ?: patient.gender
    patient.bloodGroup = dto.bloodGroup ?: patient.bloodGroup
    patient.nationality = dto.nationality ?: patient.nationality
    patient.religion = dto.religion ?: patient.religion
    patient.maritalStatus = dto.maritalStatus ?: patient.maritalStatus
    patient.occupation = dto.occupation ?: patient.occupation
    patient.photoUrl = dto.photoUrl ?: patient.photoUrl
    patient.phone = dto.phone ?: patient.phone
    patient.alternatePhone = dto.alternatePhone ?: patient.alternatePhone
    patient.email = dto.email ?: patient.email
    patient.addressLine1 = dto.addressLine1 ?: patient.addressLine1
    patient.addressLine2 = dto.addressLine2 ?: patient.addressLine2
    patient.city = dto.city ?: patient.city
    patient.country = dto.country ?: patient.country
    patient.postalCode = dto.postalCode ?: patient.postalCode
    patient.emergencyContactName = dto.emergencyContactName ?: patient.emergencyContactName
    patient.emergencyContactPhone = dto.emergencyContactPhone ?: patient.emergencyContactPhone
    patient.emergencyContactRelation = dto.emergencyContactRelation ?: patient.emergencyContactRelation
    patient.qid = dto.qid ?: patient.qid
    patient.passportNumber = dto.passportNumber ?: patient.passportNumber
    patient.idType = dto.idType ?: patient.idType
    patient.idNumber = dto.idNumber ?: patient.idNumber
    patient.insuranceCompany = dto.insuranceCompany ?: patient.insuranceCompany
    patient.insuranceMemberId = dto.insuranceMemberId ?: patient.insuranceMemberId
    patient.insurancePolicyNo = dto.insurancePolicyNo ?: patient.insurancePolicyNo
    patient.insurancePlan = dto.insurancePlan ?: patient.insurancePlan
    patient.isDeceased = dto.isDeceased ?: patient.isDeceased
    patient.deathDate = dto.deathDate ?: patient.deathDate
    patient.deceasedNote = dto.deceasedNote ?: patient.deceasedNote
    patient.languagePreference = dto.languagePreference ?: patient.languagePreference
    patient.communicationPreference = dto.communicationPreference ?: patient.communicationPreference
    patient.registrationChannel = dto.registrationChannel ?: patient.registrationChannel
    patient.recordsStatus = dto.recordsStatus ?: patient.recordsStatus
    // Identifying an unknown patient: real name or DOB closes the unknown state.
    if (dto.firstName != null || dto.dateOfBirth != null) {
      patient.isUnknown = false
      if (patient.recordsStatus == "pending") patient.recordsStatus = "active"
    }
    patientRecordRepository.save(patient)
    searchIndexer.indexPatient(patient)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "patient.updated",
        occurredAt = Instant.now(),
        source = "patient-service",
        entityType = "patient",
        entityId = patient.id,
        hospitalId = patient.hospitalId,
        payload =
          mapOf(
            "firstName" to patient.firstName,
            "lastName" to patient.lastName,
            "mrn" to patient.mrn,
          ),
      ),
    )
    return getDetail(id)
  }

  @Transactional(readOnly = true)
  fun getDetail(id: UUID): PatientDetailDto {
    val patient = findPatient(id)
    val sub =
      patientRecordRepository
        .findDetailSubresources(id)
        ?.let { objectMapper.readValue(it, PatientDetailSubresources::class.java) }
        ?: PatientDetailSubresources()
    return PatientDetailDto.from(patient, sub.identifiers, sub.contactPoints, sub.addresses, sub.allergies, sub.conditions, sub.notes, sub.relationships)
  }

  @Transactional(readOnly = true)
  fun search(
    query: String?,
    hospitalId: UUID?,
  ): List<PatientSummaryDto> {
    val q = query?.trim().orEmpty()
    val results =
      if (q.isEmpty()) {
        if (hospitalId != null) patientRecordRepository.findByHospitalId(hospitalId) else patientRecordRepository.findAll()
      } else {
        searchByQuery(q, hospitalId)
      }
    // Archived (merged) duplicate rows are hidden from search; they stay reachable by direct id.
    return results.filter { it.mergedInto == null }.map { PatientSummaryDto.from(it) }
  }

  @Transactional
  fun addAllergy(
    patientId: UUID,
    dto: AllergyDto,
  ): AllergyDto {
    findPatient(patientId)
    val allergen = dto.allergen ?: throw IllegalArgumentException("ALLERGEN_REQUIRED")
    val isNka = allergen.trim().equals(Allergy.NKA_ALLERGEN, ignoreCase = true)
    if (isNka) {
      // SAFETY L49: NKA is an attestation; it cannot coexist with a documented active allergy.
      val known =
        allergyRepository
          .findByPatientIdAndIsActiveTrue(patientId)
          .any { !it.allergen.trim().equals(Allergy.NKA_ALLERGEN, ignoreCase = true) }
      if (known) throw IllegalStateException("NKA_CONFLICTS_WITH_KNOWN_ALLERGIES")
    } else {
      // A newly documented real allergy invalidates any standing NKA attestation.
      allergyRepository
        .findByPatientIdAndIsActiveTrue(patientId)
        .filter { it.allergen.trim().equals(Allergy.NKA_ALLERGEN, ignoreCase = true) }
        .forEach { it.isActive = false }
    }
    val allergy =
      allergyRepository.save(
        Allergy(
          patientId = patientId,
          allergen = allergen,
          reaction = dto.reaction,
          severity = if (isNka) Allergy.SEVERITY_NONE else Allergy.normalizeSeverity(dto.severity),
          recordedDate = dto.recordedDate,
          isActive = dto.isActive ?: true,
          notes = dto.notes,
        ),
      )
    return AllergyDto.from(allergy)
  }

  @Transactional
  fun deactivateAllergy(
    patientId: UUID,
    allergyId: UUID,
  ): AllergyDto {
    val allergy = allergyRepository.findById(allergyId).orElseThrow { IllegalArgumentException("ALLERGY_NOT_FOUND") }
    if (allergy.patientId != patientId) {
      throw IllegalArgumentException("ALLERGY_NOT_FOUND")
    }
    allergy.isActive = false
    allergyRepository.save(allergy)
    return AllergyDto.from(allergy)
  }

  @Transactional(readOnly = true)
  fun listAllergies(patientId: UUID): List<AllergyDto> {
    findPatient(patientId)
    return allergyRepository.findByPatientId(patientId).map { AllergyDto.from(it) }
  }

  @Transactional
  fun addNote(
    patientId: UUID,
    dto: NoteDto,
    authorUserId: UUID?,
  ): NoteDto {
    findPatient(patientId)
    val note =
      patientNoteRepository.save(
        PatientNote(
          patientId = patientId,
          authorId = authorUserId,
          title = dto.title,
          body = dto.body,
          isFlagged = dto.isFlagged ?: false,
        ),
      )
    return NoteDto.from(note)
  }

  @Transactional(readOnly = true)
  fun listNotes(patientId: UUID): List<NoteDto> {
    findPatient(patientId)
    return patientNoteRepository
      .findByPatientId(patientId)
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { NoteDto.from(it) }
  }

  @Transactional
  fun addMedicalHistory(
    patientId: UUID,
    dto: MedicalHistoryDto,
  ): MedicalHistoryDto {
    findPatient(patientId)
    val history =
      medicalHistoryRepository.save(
        MedicalHistory(
          patientId = patientId,
          conditionName = dto.conditionName ?: throw IllegalArgumentException("CONDITION_NAME_REQUIRED"),
          diagnosisCode = dto.diagnosisCode,
          diagnosisSystem = dto.diagnosisSystem ?: "ICD-10",
          onsetDate = dto.onsetDate,
          resolvedDate = dto.resolvedDate,
          notes = dto.notes,
        ),
      )
    return MedicalHistoryDto.from(history)
  }

  @Transactional(readOnly = true)
  fun listMedicalHistory(patientId: UUID): List<MedicalHistoryDto> {
    findPatient(patientId)
    return medicalHistoryRepository
      .findByPatientId(patientId)
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { MedicalHistoryDto.from(it) }
  }

  @Transactional
  fun mergePatient(
    targetId: UUID,
    duplicateId: UUID,
    actorId: UUID,
  ): MergeResponseDto {
    if (targetId == duplicateId) {
      throw IllegalArgumentException("CANNOT_MERGE_PATIENT_WITH_ITSELF")
    }
    val target = findPatient(targetId)
    val duplicate = findPatient(duplicateId)
    if (target.hospitalId != null && duplicate.hospitalId != null && target.hospitalId != duplicate.hospitalId) {
      throw IllegalArgumentException("MERGE_CROSS_HOSPITAL")
    }
    if (target.mergedInto != null || duplicate.mergedInto != null) {
      throw IllegalArgumentException("PATIENT_ALREADY_MERGED")
    }

    val moved =
      mutableMapOf(
        "identifiers" to 0,
        "contacts" to 0,
        "addresses" to 0,
        "allergies" to 0,
        "notes" to 0,
        "history" to 0,
        "episodes" to 0,
        "relationships" to 0,
      )
    // V47 reversibility: every moved child row id is recorded in the merge-log snapshot
    // so unmerge can re-point each row back to the duplicate.
    val movedIds =
      linkedMapOf(
        "identifiers" to mutableListOf<String>(),
        "contacts" to mutableListOf(),
        "addresses" to mutableListOf(),
        "allergies" to mutableListOf(),
        "notes" to mutableListOf(),
        "history" to mutableListOf(),
        "episodes" to mutableListOf(),
        "relationshipsOut" to mutableListOf(),
        "relationshipsIn" to mutableListOf(),
      )
    val skipped = mutableListOf<String>()

    patientIdentifierRepository.findByPatientId(duplicateId).forEach { identifier ->
      if (patientIdentifierRepository.existsByIdTypeAndIdValue(identifier.idType, identifier.idValue)) {
        skipped += "IDENTIFIER:${identifier.idType}:${identifier.idValue}"
        logger.warn(
          "Skipping duplicate identifier {}:{} when merging patient {} into {}",
          identifier.idType,
          identifier.idValue,
          duplicateId,
          targetId,
        )
      } else {
        identifier.id?.let { movedIds.getValue("identifiers").add(it.toString()) }
        identifier.patientId = targetId
        patientIdentifierRepository.save(identifier)
        moved["identifiers"] = moved.getValue("identifiers") + 1
      }
    }

    contactPointRepository.findByPatientId(duplicateId).forEach { contact ->
      contact.id?.let { movedIds.getValue("contacts").add(it.toString()) }
      contact.patientId = targetId
      contactPointRepository.save(contact)
      moved["contacts"] = moved.getValue("contacts") + 1
    }

    addressRepository.findByPatientId(duplicateId).forEach { address ->
      address.id?.let { movedIds.getValue("addresses").add(it.toString()) }
      address.patientId = targetId
      addressRepository.save(address)
      moved["addresses"] = moved.getValue("addresses") + 1
    }

    allergyRepository.findByPatientId(duplicateId).forEach { allergy ->
      allergy.id?.let { movedIds.getValue("allergies").add(it.toString()) }
      allergy.patientId = targetId
      allergyRepository.save(allergy)
      moved["allergies"] = moved.getValue("allergies") + 1
    }

    patientNoteRepository.findByPatientId(duplicateId).forEach { note ->
      note.id?.let { movedIds.getValue("notes").add(it.toString()) }
      note.patientId = targetId
      patientNoteRepository.save(note)
      moved["notes"] = moved.getValue("notes") + 1
    }

    medicalHistoryRepository.findByPatientId(duplicateId).forEach { history ->
      history.id?.let { movedIds.getValue("history").add(it.toString()) }
      history.patientId = targetId
      medicalHistoryRepository.save(history)
      moved["history"] = moved.getValue("history") + 1
    }

    episodeOfCareRepository.findByPatientId(duplicateId).forEach { episode ->
      episode.id?.let { movedIds.getValue("episodes").add(it.toString()) }
      episode.patientId = targetId
      episodeOfCareRepository.save(episode)
      moved["episodes"] = moved.getValue("episodes") + 1
    }

    // Relationships move both ways (outgoing by patientId, incoming by relatedPatientId);
    // an identical relationship already on the survivor stays put and is skipped.
    patientRelationshipRepository.findByPatientId(duplicateId).forEach { relationship ->
      if (patientRelationshipRepository.existsByHospitalIdAndPatientIdAndRelatedPatientIdAndRelationshipType(
          relationship.hospitalId,
          targetId,
          relationship.relatedPatientId,
          relationship.relationshipType,
        )
      ) {
        skipped += "RELATIONSHIP:${relationship.relationshipType}:${relationship.relatedPatientId}"
      } else {
        relationship.id?.let { movedIds.getValue("relationshipsOut").add(it.toString()) }
        relationship.patientId = targetId
        patientRelationshipRepository.save(relationship)
        moved["relationships"] = moved.getValue("relationships") + 1
      }
    }
    patientRelationshipRepository.findByRelatedPatientId(duplicateId).forEach { relationship ->
      if (patientRelationshipRepository.existsByHospitalIdAndPatientIdAndRelatedPatientIdAndRelationshipType(
          relationship.hospitalId,
          relationship.patientId,
          targetId,
          relationship.relationshipType,
        )
      ) {
        skipped += "RELATIONSHIP:${relationship.relationshipType}:${relationship.patientId}"
      } else {
        relationship.id?.let { movedIds.getValue("relationshipsIn").add(it.toString()) }
        relationship.relatedPatientId = targetId
        patientRelationshipRepository.save(relationship)
        moved["relationships"] = moved.getValue("relationships") + 1
      }
    }

    // Archive the duplicate row (never delete) so the merge stays reversible (V47).
    duplicate.mergedInto = targetId
    duplicate.mergedAt = Instant.now()
    duplicate.recordsStatus = "merged"
    patientRecordRepository.save(duplicate)

    val hospitalId =
      target.hospitalId ?: duplicate.hospitalId ?: throw IllegalArgumentException("PATIENT_HOSPITAL_REQUIRED")
    val mergeVersion =
      1 +
        patientMergeLogRepository
          .findByHospitalIdAndTargetPatientIdAndDuplicatePatientId(hospitalId, targetId, duplicateId)
          .size +
        patientMergeLogRepository
          .findByHospitalIdAndTargetPatientIdAndDuplicatePatientId(hospitalId, duplicateId, targetId)
          .size
    val now = Instant.now()
    val log =
      patientMergeLogRepository.save(
        PatientMergeLog(
          hospitalId = hospitalId,
          targetPatientId = targetId,
          duplicatePatientId = duplicateId,
          mergeVersion = mergeVersion,
          status = PatientMergeLog.STATUS_MERGED,
          snapshot = objectMapper.writeValueAsString(movedIds),
          mergedBy = actorId,
          mergedAt = now,
        ),
      )

    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "patient.merged",
        occurredAt = now,
        source = "patient-service",
        entityType = "patient",
        entityId = targetId,
        hospitalId = hospitalId,
        payload =
          mapOf(
            "duplicatePatientId" to duplicateId.toString(),
            "mergeId" to log.id.toString(),
            "mergeVersion" to mergeVersion,
          ),
      ),
    )

    return MergeResponseDto(
      patient = getDetail(targetId),
      moved = moved,
      skipped = skipped,
      mergeId = log.id,
    )
  }

  /**
   * V47 unmerge: re-point every child row from the merge-log snapshot back to the duplicate
   * (rows later re-owned elsewhere are left alone), restore the archived duplicate row, mark
   * the log UNMERGED. Re-merging the same pair produces a new merge_version.
   */
  @Transactional
  fun unmergePatient(
    mergeId: UUID,
    actorId: UUID,
  ): MergeLogDto {
    val log =
      patientMergeLogRepository.findById(mergeId).orElseThrow { IllegalArgumentException("MERGE_NOT_FOUND") }
    if (log.status != PatientMergeLog.STATUS_MERGED) {
      throw IllegalArgumentException("MERGE_NOT_UNMERGEABLE")
    }
    val targetId = log.targetPatientId
    val duplicateId = log.duplicatePatientId
    val snapshot: Map<String, List<String>> =
      objectMapper.readValue(log.snapshot, object : TypeReference<Map<String, List<String>>>() {})

    fun ids(key: String): List<UUID> = snapshot[key].orEmpty().mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }

    val movedBack =
      linkedMapOf(
        "identifiers" to 0,
        "contacts" to 0,
        "addresses" to 0,
        "allergies" to 0,
        "notes" to 0,
        "history" to 0,
        "episodes" to 0,
        "relationships" to 0,
      )

    fun <T : Any> repoint(
      rowIds: List<UUID>,
      repository: CrudRepository<T, UUID>,
      owner: (T) -> UUID,
      reassign: (T, UUID) -> Unit,
      counter: String,
    ) {
      for (rowId in rowIds) {
        repository.findById(rowId).ifPresent { row ->
          if (owner(row) == targetId) {
            reassign(row, duplicateId)
            repository.save(row)
            movedBack[counter] = movedBack.getValue(counter) + 1
          }
        }
      }
    }

    repoint(ids("identifiers"), patientIdentifierRepository, { it.patientId }, { row, id -> row.patientId = id }, "identifiers")
    repoint(ids("contacts"), contactPointRepository, { it.patientId }, { row, id -> row.patientId = id }, "contacts")
    repoint(ids("addresses"), addressRepository, { it.patientId }, { row, id -> row.patientId = id }, "addresses")
    repoint(ids("allergies"), allergyRepository, { it.patientId }, { row, id -> row.patientId = id }, "allergies")
    repoint(ids("notes"), patientNoteRepository, { it.patientId }, { row, id -> row.patientId = id }, "notes")
    repoint(ids("history"), medicalHistoryRepository, { it.patientId }, { row, id -> row.patientId = id }, "history")
    repoint(ids("episodes"), episodeOfCareRepository, { it.patientId }, { row, id -> row.patientId = id }, "episodes")
    repoint(ids("relationshipsOut"), patientRelationshipRepository, { it.patientId }, { row, id -> row.patientId = id }, "relationships")
    repoint(ids("relationshipsIn"), patientRelationshipRepository, { it.relatedPatientId }, { row, id -> row.relatedPatientId = id }, "relationships")

    val duplicate = findPatient(duplicateId)
    duplicate.mergedInto = null
    duplicate.mergedAt = null
    duplicate.recordsStatus = "active"
    patientRecordRepository.save(duplicate)

    log.status = PatientMergeLog.STATUS_UNMERGED
    log.unmergedBy = actorId
    log.unmergedAt = Instant.now()
    patientMergeLogRepository.save(log)

    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "patient.unmerged",
        occurredAt = log.unmergedAt ?: Instant.now(),
        source = "patient-service",
        entityType = "patient",
        entityId = duplicateId,
        hospitalId = log.hospitalId,
        payload =
          mapOf(
            "targetPatientId" to targetId.toString(),
            "mergeId" to mergeId.toString(),
            "mergeVersion" to log.mergeVersion,
          ),
      ),
    )

    return MergeLogDto.from(log, snapshot)
  }

  @Transactional(readOnly = true)
  fun getHospitalId(id: UUID): UUID? = findPatient(id).hospitalId

  @Transactional(readOnly = true)
  fun getPhotoKey(id: UUID): String? = findPatient(id).photoUrl

  fun getPhoto(key: String): Pair<ByteArray, String> = photoStorage.download(key)

  private fun searchByQuery(
    q: String,
    hospitalId: UUID?,
  ): List<PatientRecord> {
    val matches = LinkedHashMap<UUID, PatientRecord>()

    fun addCandidate(records: List<PatientRecord>) {
      for (record in records) {
        record.id?.let { matches.putIfAbsent(it, record) }
      }
    }

    patientRecordRepository.findByMrnIgnoreCase(q)?.let { addCandidate(listOf(it)) }
    addCandidate(patientRecordRepository.findByPhone(q))
    addCandidate(patientRecordRepository.findByPhoneContaining(q))
    addCandidate(patientRecordRepository.findByQid(q))
    addCandidate(patientRecordRepository.findByEmailIgnoreCase(q))
    // Identifier search (ABHA / ABHA address / any stored id) resolves to owning patients.
    patientIdentifierRepository.findByIdValue(q).forEach { identifier ->
      patientRecordRepository.findById(identifier.patientId).ifPresent { addCandidate(listOf(it)) }
    }

    val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.size >= 2) {
      addCandidate(
        patientRecordRepository.findByLastNameContainingIgnoreCaseAndFirstNameContainingIgnoreCase(
          lastName = tokens.last(),
          firstName = tokens.dropLast(1).joinToString(" "),
        ),
      )
      addCandidate(
        patientRecordRepository.findByLastNameContainingIgnoreCaseAndFirstNameContainingIgnoreCase(
          lastName = tokens.first(),
          firstName = tokens.drop(1).joinToString(" "),
        ),
      )
    } else {
      addCandidate(patientRecordRepository.findByFirstNameContainingIgnoreCase(q))
      addCandidate(patientRecordRepository.findByLastNameContainingIgnoreCase(q))
    }

    val filtered =
      if (hospitalId != null) {
        matches.values.filter { it.hospitalId == hospitalId }
      } else {
        matches.values
      }
    return filtered.sortedBy { it.createdAt ?: Instant.EPOCH }
  }

  private fun resolveMrn(dto: PatientCreateDto): String {
    val provided = dto.mrn?.trim()
    if (!provided.isNullOrEmpty()) {
      val existing =
        if (dto.hospitalId != null) {
          patientRecordRepository.findByHospitalIdAndMrnIgnoreCase(dto.hospitalId, provided)
        } else {
          patientRecordRepository.findByMrnIgnoreCase(provided)
        }
      if (existing != null) {
        throw IllegalStateException("PATIENT_MRN_ALREADY_EXISTS")
      }
      return provided
    }
    return mrnGenerator.next()
  }

  private fun findPatient(id: UUID): PatientRecord = patientRecordRepository.findById(id).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }

  @Transactional
  fun addIdentifier(
    patientId: UUID,
    dto: PatientIdentifierDto,
  ): PatientIdentifierDto {
    findPatient(patientId)
    val idType = dto.idType ?: throw IllegalArgumentException("IDENTIFIER_TYPE_REQUIRED")
    val idValue0 = dto.idValue?.trim() ?: throw IllegalArgumentException("IDENTIFIER_VALUE_REQUIRED")
    validateIdentifier(idType, idValue0)
    // ABHA addresses are case-insensitive; store them lowercase so duplicate checks are exact.
    val idValue = if (idType.equals("ABHA_ADDRESS", ignoreCase = true)) idValue0.lowercase() else idValue0
    if (patientIdentifierRepository.existsByIdTypeAndIdValue(idType, idValue)) {
      throw IllegalStateException("DUPLICATE_IDENTIFIER")
    }
    return PatientIdentifierDto.from(
      patientIdentifierRepository.save(
        PatientIdentifier(
          patientId = patientId,
          idType = idType,
          idValue = idValue,
          issuingAuthority = dto.issuingAuthority,
          primaryFlag = dto.primaryFlag ?: false,
        ),
      ),
    )
  }

  @Transactional(readOnly = true)
  fun listIdentifiers(patientId: UUID): List<PatientIdentifierDto> {
    findPatient(patientId)
    return patientIdentifierRepository.findByPatientId(patientId).map { PatientIdentifierDto.from(it) }
  }

  @Transactional
  fun deleteIdentifier(
    patientId: UUID,
    identifierId: UUID,
  ) {
    val identifier = patientIdentifierRepository.findById(identifierId).orElseThrow { IllegalArgumentException("IDENTIFIER_NOT_FOUND") }
    if (identifier.patientId != patientId) throw IllegalArgumentException("IDENTIFIER_NOT_FOUND")
    patientIdentifierRepository.delete(identifier)
  }

  @Transactional
  fun addRelationship(
    patientId: UUID,
    dto: PatientRelationshipDto,
  ): PatientRelationshipDto {
    val patient = findPatient(patientId)
    val relatedId = dto.relatedPatientId ?: throw IllegalArgumentException("RELATED_PATIENT_REQUIRED")
    if (relatedId == patientId) throw IllegalArgumentException("CANNOT_LINK_PATIENT_TO_ITSELF")
    val related = findPatient(relatedId)
    if (patient.hospitalId != null && related.hospitalId != null && patient.hospitalId != related.hospitalId) {
      throw IllegalArgumentException("RELATED_PATIENT_OTHER_HOSPITAL")
    }
    val type = dto.relationshipType?.trim()?.lowercase() ?: throw IllegalArgumentException("RELATIONSHIP_TYPE_REQUIRED")
    val hospitalId = patient.hospitalId ?: throw IllegalStateException("PATIENT_HOSPITAL_MISSING")
    // Pre-save check: a UNIQUE-violation only surfaces at flush time, outside any in-transaction catch.
    if (patientRelationshipRepository.existsByHospitalIdAndPatientIdAndRelatedPatientIdAndRelationshipType(
        hospitalId,
        patientId,
        relatedId,
        type,
      )
    ) {
      throw IllegalStateException("DUPLICATE_RELATIONSHIP")
    }
    try {
      return PatientRelationshipDto.from(
        patientRelationshipRepository.save(
          PatientRelationship(
            hospitalId = hospitalId,
            patientId = patientId,
            relatedPatientId = relatedId,
            relationshipType = type,
            isPrimaryGuardian = dto.isPrimaryGuardian ?: false,
            notes = dto.notes,
          ),
        ),
      )
    } catch (e: DataIntegrityViolationException) {
      throw IllegalStateException("DUPLICATE_RELATIONSHIP")
    }
  }

  @Transactional(readOnly = true)
  fun listRelationships(patientId: UUID): List<PatientRelationshipDto> {
    findPatient(patientId)
    val direct = patientRelationshipRepository.findByPatientId(patientId).map { it to false }
    val reverse = patientRelationshipRepository.findByRelatedPatientId(patientId).map { it to true }
    return (direct + reverse).map { (rel, reversed) -> PatientRelationshipDto.from(rel, reversed) }
  }

  @Transactional
  fun deleteRelationship(
    patientId: UUID,
    relationshipId: UUID,
  ) {
    val rel = patientRelationshipRepository.findById(relationshipId).orElseThrow { IllegalArgumentException("RELATIONSHIP_NOT_FOUND") }
    if (rel.patientId != patientId && rel.relatedPatientId != patientId) throw IllegalArgumentException("RELATIONSHIP_NOT_FOUND")
    patientRelationshipRepository.delete(rel)
  }

  @Transactional
  fun setPhoto(
    patientId: UUID,
    file: MultipartFile,
  ): String {
    val patient = findPatient(patientId)
    val key = photoStorage.upload(patient.hospitalId ?: throw IllegalStateException("PATIENT_HOSPITAL_MISSING"), patientId, file)
    patient.photoUrl = key
    patientRecordRepository.save(patient)
    return key
  }

  /** ABHA per ABDM: numeric 14-digit (or 13-digit legacy without checksum row), ABHA address like `name@abdm`. */
  private fun validateIdentifier(
    idType: String,
    idValue: String,
  ) {
    when (idType.uppercase()) {
      "ABHA" -> {
        if (!Regex("^\\d{14}$").matches(idValue)) {
          throw IllegalArgumentException("ABHA_NUMBER_INVALID")
        }
      }

      "ABHA_ADDRESS" -> {
        if (!Regex("^[a-z0-9._-]{4,20}@[a-z]{2,}$").matches(idValue.lowercase())) {
          throw IllegalArgumentException("ABHA_ADDRESS_INVALID")
        }
      }
    }
  }

  @Transactional
  fun findOrCreateFromHl7(
    hospitalId: UUID,
    mrn: String,
    firstName: String?,
    middleName: String?,
    lastName: String?,
    dateOfBirth: LocalDate?,
    gender: String?,
    phone: String?,
  ): UUID {
    val existing = searchByQuery(mrn, null).firstOrNull()?.id
    if (existing != null) return existing
    val detail =
      create(
        PatientCreateDto(
          hospitalId = hospitalId,
          mrn = mrn,
          firstName = firstName,
          middleName = middleName,
          lastName = lastName,
          dateOfBirth = dateOfBirth,
          gender = gender,
          phone = phone,
          recordsStatus = if (firstName.isNullOrBlank() && lastName.isNullOrBlank()) "pending" else "active",
        ),
      )
    return detail.id ?: throw IllegalStateException("PATIENT_ID_MISSING")
  }
}
