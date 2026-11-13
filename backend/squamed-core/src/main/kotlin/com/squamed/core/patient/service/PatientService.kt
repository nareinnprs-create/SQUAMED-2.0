package com.squamed.core.patient.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.patient.api.AddressDto
import com.squamed.core.patient.api.AllergyDto
import com.squamed.core.patient.api.ContactPointDto
import com.squamed.core.patient.api.MedicalHistoryDto
import com.squamed.core.patient.api.MergeResponseDto
import com.squamed.core.patient.api.NoteDto
import com.squamed.core.patient.api.PatientCreateDto
import com.squamed.core.patient.api.PatientDetailDto
import com.squamed.core.patient.api.PatientIdentifierDto
import com.squamed.core.patient.api.PatientSummaryDto
import com.squamed.core.patient.api.PatientUpdateDto
import com.squamed.core.patient.domain.Address
import com.squamed.core.patient.domain.Allergy
import com.squamed.core.patient.domain.ContactPoint
import com.squamed.core.patient.domain.MedicalHistory
import com.squamed.core.patient.domain.PatientIdentifier
import com.squamed.core.patient.domain.PatientNote
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.AddressRepository
import com.squamed.core.patient.repository.AllergyRepository
import com.squamed.core.patient.repository.ContactPointRepository
import com.squamed.core.patient.repository.EpisodeOfCareRepository
import com.squamed.core.patient.repository.MedicalHistoryRepository
import com.squamed.core.patient.repository.PatientIdentifierRepository
import com.squamed.core.patient.repository.PatientNoteRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.search.SearchIndexer
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
  private val mrnGenerator: MrnGenerator,
  private val eventPublisher: DomainEventPublisher,
  private val searchIndexer: SearchIndexer,
) {
  private val logger = LoggerFactory.getLogger(PatientService::class.java)

  @Transactional
  fun create(dto: PatientCreateDto): PatientDetailDto {
    val mrn = resolveMrn(dto)
    val patient =
      patientRecordRepository.save(
        PatientRecord(
          hospitalId = dto.hospitalId,
          mrn = mrn,
          externalId = dto.externalId,
          title = dto.title,
          firstName = dto.firstName ?: throw IllegalArgumentException("PATIENT_FIRST_NAME_REQUIRED"),
          middleName = dto.middleName,
          lastName = dto.lastName ?: throw IllegalArgumentException("PATIENT_LAST_NAME_REQUIRED"),
          preferredName = dto.preferredName,
          dateOfBirth = dto.dateOfBirth ?: throw IllegalArgumentException("PATIENT_DATE_OF_BIRTH_REQUIRED"),
          gender = dto.gender ?: throw IllegalArgumentException("PATIENT_GENDER_REQUIRED"),
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
          recordsStatus = dto.recordsStatus ?: "active",
        ),
      )
    val patientId = patient.id ?: throw IllegalStateException("PATIENT_ID_MISSING")
    searchIndexer.indexPatient(patient)

    for (input in dto.identifiers.orEmpty()) {
      val idType = input.idType ?: throw IllegalArgumentException("IDENTIFIER_TYPE_REQUIRED")
      val idValue = input.idValue ?: throw IllegalArgumentException("IDENTIFIER_VALUE_REQUIRED")
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
    patientRecordRepository.save(patient)
    searchIndexer.indexPatient(patient)
    return getDetail(id)
  }

  @Transactional(readOnly = true)
  fun getDetail(id: UUID): PatientDetailDto {
    val patient = findPatient(id)
    val identifiers = patientIdentifierRepository.findByPatientId(id).map { PatientIdentifierDto.from(it) }
    val contactPoints = contactPointRepository.findByPatientId(id).map { ContactPointDto.from(it) }
    val addresses = addressRepository.findByPatientId(id).map { AddressDto.from(it) }
    val allergies = allergyRepository.findByPatientId(id).map { AllergyDto.from(it) }
    val conditions = medicalHistoryRepository.findByPatientId(id).map { MedicalHistoryDto.from(it) }
    val notes = patientNoteRepository.findByPatientId(id).map { NoteDto.from(it) }
    return PatientDetailDto.from(patient, identifiers, contactPoints, addresses, allergies, conditions, notes)
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
    return results.map { PatientSummaryDto.from(it) }
  }

  @Transactional
  fun addAllergy(
    patientId: UUID,
    dto: AllergyDto,
  ): AllergyDto {
    findPatient(patientId)
    val allergy =
      allergyRepository.save(
        Allergy(
          patientId = patientId,
          allergen = dto.allergen ?: throw IllegalArgumentException("ALLERGEN_REQUIRED"),
          reaction = dto.reaction,
          severity = dto.severity ?: "mild",
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
  ): MergeResponseDto {
    if (targetId == duplicateId) {
      throw IllegalArgumentException("CANNOT_MERGE_PATIENT_WITH_ITSELF")
    }
    findPatient(targetId)
    findPatient(duplicateId)

    val moved =
      mutableMapOf(
        "identifiers" to 0,
        "contacts" to 0,
        "addresses" to 0,
        "allergies" to 0,
        "notes" to 0,
        "history" to 0,
        "episodes" to 0,
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
        identifier.patientId = targetId
        patientIdentifierRepository.save(identifier)
        moved["identifiers"] = moved.getValue("identifiers") + 1
      }
    }

    contactPointRepository.findByPatientId(duplicateId).forEach { contact ->
      contact.patientId = targetId
      contactPointRepository.save(contact)
      moved["contacts"] = moved.getValue("contacts") + 1
    }

    addressRepository.findByPatientId(duplicateId).forEach { address ->
      address.patientId = targetId
      addressRepository.save(address)
      moved["addresses"] = moved.getValue("addresses") + 1
    }

    allergyRepository.findByPatientId(duplicateId).forEach { allergy ->
      allergy.patientId = targetId
      allergyRepository.save(allergy)
      moved["allergies"] = moved.getValue("allergies") + 1
    }

    patientNoteRepository.findByPatientId(duplicateId).forEach { note ->
      note.patientId = targetId
      patientNoteRepository.save(note)
      moved["notes"] = moved.getValue("notes") + 1
    }

    medicalHistoryRepository.findByPatientId(duplicateId).forEach { history ->
      history.patientId = targetId
      medicalHistoryRepository.save(history)
      moved["history"] = moved.getValue("history") + 1
    }

    episodeOfCareRepository.findByPatientId(duplicateId).forEach { episode ->
      episode.patientId = targetId
      episodeOfCareRepository.save(episode)
      moved["episodes"] = moved.getValue("episodes") + 1
    }

    patientRecordRepository.deleteById(duplicateId)
    return MergeResponseDto(
      patient = getDetail(targetId),
      moved = moved,
      skipped = skipped,
    )
  }

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
  fun findOrCreateFromHl7(
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
