package com.squamed.core.patient.api

import com.squamed.core.patient.domain.Address
import com.squamed.core.patient.domain.Allergy
import com.squamed.core.patient.domain.ContactPoint
import com.squamed.core.patient.domain.EmpiReview
import com.squamed.core.patient.domain.MedicalHistory
import com.squamed.core.patient.domain.PatientIdentifier
import com.squamed.core.patient.domain.PatientMergeLog
import com.squamed.core.patient.domain.PatientNote
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.domain.PatientRelationship
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PatientCreateDto(
  val hospitalId: UUID? = null,
  val mrn: String? = null,
  val unknownPatient: Boolean? = null,
  val externalId: String? = null,
  val title: String? = null,
  val firstName: String? = null,
  val middleName: String? = null,
  val lastName: String? = null,
  val preferredName: String? = null,
  val dateOfBirth: LocalDate? = null,
  val gender: String? = null,
  val bloodGroup: String? = null,
  val nationality: String? = null,
  val religion: String? = null,
  val maritalStatus: String? = null,
  val occupation: String? = null,
  val photoUrl: String? = null,
  val phone: String? = null,
  val alternatePhone: String? = null,
  val email: String? = null,
  val addressLine1: String? = null,
  val addressLine2: String? = null,
  val city: String? = null,
  val country: String? = null,
  val postalCode: String? = null,
  val emergencyContactName: String? = null,
  val emergencyContactPhone: String? = null,
  val emergencyContactRelation: String? = null,
  val qid: String? = null,
  val passportNumber: String? = null,
  val idType: String? = null,
  val idNumber: String? = null,
  val insuranceCompany: String? = null,
  val insuranceMemberId: String? = null,
  val insurancePolicyNo: String? = null,
  val insurancePlan: String? = null,
  val isDeceased: Boolean? = null,
  val deathDate: LocalDate? = null,
  val deceasedNote: String? = null,
  val languagePreference: String? = null,
  val communicationPreference: String? = null,
  val registrationChannel: String? = null,
  val recordsStatus: String? = null,
  val identifiers: List<PatientIdentifierDto> = emptyList(),
  val contactPoints: List<ContactPointDto> = emptyList(),
  val addresses: List<AddressDto> = emptyList(),
)

data class PatientUpdateDto(
  val externalId: String? = null,
  val title: String? = null,
  val firstName: String? = null,
  val middleName: String? = null,
  val lastName: String? = null,
  val preferredName: String? = null,
  val dateOfBirth: LocalDate? = null,
  val gender: String? = null,
  val bloodGroup: String? = null,
  val nationality: String? = null,
  val religion: String? = null,
  val maritalStatus: String? = null,
  val occupation: String? = null,
  val photoUrl: String? = null,
  val phone: String? = null,
  val alternatePhone: String? = null,
  val email: String? = null,
  val addressLine1: String? = null,
  val addressLine2: String? = null,
  val city: String? = null,
  val country: String? = null,
  val postalCode: String? = null,
  val emergencyContactName: String? = null,
  val emergencyContactPhone: String? = null,
  val emergencyContactRelation: String? = null,
  val qid: String? = null,
  val passportNumber: String? = null,
  val idType: String? = null,
  val idNumber: String? = null,
  val insuranceCompany: String? = null,
  val insuranceMemberId: String? = null,
  val insurancePolicyNo: String? = null,
  val insurancePlan: String? = null,
  val isDeceased: Boolean? = null,
  val deathDate: LocalDate? = null,
  val deceasedNote: String? = null,
  val languagePreference: String? = null,
  val communicationPreference: String? = null,
  val registrationChannel: String? = null,
  val recordsStatus: String? = null,
)

data class PatientSummaryDto(
  val id: UUID?,
  val mrn: String?,
  val hospitalId: UUID?,
  val firstName: String?,
  val middleName: String?,
  val lastName: String?,
  val preferredName: String?,
  val dateOfBirth: LocalDate?,
  val gender: String?,
  val phone: String?,
  val email: String?,
  val qid: String?,
  val recordsStatus: String?,
  val isUnknown: Boolean = false,
) {
  companion object {
    fun from(entity: PatientRecord) =
      PatientSummaryDto(
        id = entity.id,
        mrn = entity.mrn,
        hospitalId = entity.hospitalId,
        firstName = entity.firstName,
        middleName = entity.middleName,
        lastName = entity.lastName,
        preferredName = entity.preferredName,
        dateOfBirth = entity.dateOfBirth,
        gender = entity.gender,
        phone = entity.phone,
        email = entity.email,
        qid = entity.qid,
        recordsStatus = entity.recordsStatus,
        isUnknown = entity.isUnknown,
      )
  }
}

data class PatientDetailDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val mrn: String?,
  val externalId: String?,
  val title: String?,
  val firstName: String?,
  val middleName: String?,
  val lastName: String?,
  val preferredName: String?,
  val dateOfBirth: LocalDate?,
  val gender: String?,
  val bloodGroup: String?,
  val nationality: String?,
  val religion: String?,
  val maritalStatus: String?,
  val occupation: String?,
  val photoUrl: String?,
  val phone: String?,
  val alternatePhone: String?,
  val email: String?,
  val addressLine1: String?,
  val addressLine2: String?,
  val city: String?,
  val country: String?,
  val postalCode: String?,
  val emergencyContactName: String?,
  val emergencyContactPhone: String?,
  val emergencyContactRelation: String?,
  val qid: String?,
  val passportNumber: String?,
  val idType: String?,
  val idNumber: String?,
  val insuranceCompany: String?,
  val insuranceMemberId: String?,
  val insurancePolicyNo: String?,
  val insurancePlan: String?,
  val isDeceased: Boolean?,
  val deathDate: LocalDate?,
  val deceasedNote: String?,
  val languagePreference: String?,
  val communicationPreference: String?,
  val registrationChannel: String?,
  val recordsStatus: String?,
  val isUnknown: Boolean? = null,
  val identifiers: List<PatientIdentifierDto> = emptyList(),
  val contactPoints: List<ContactPointDto> = emptyList(),
  val addresses: List<AddressDto> = emptyList(),
  val allergies: List<AllergyDto> = emptyList(),
  val conditions: List<MedicalHistoryDto> = emptyList(),
  val notes: List<NoteDto> = emptyList(),
  val relationships: List<PatientRelationshipDto> = emptyList(),
) {
  companion object {
    fun from(
      entity: PatientRecord,
      identifiers: List<PatientIdentifierDto>,
      contactPoints: List<ContactPointDto>,
      addresses: List<AddressDto>,
      allergies: List<AllergyDto>,
      conditions: List<MedicalHistoryDto>,
      notes: List<NoteDto>,
      relationships: List<PatientRelationshipDto> = emptyList(),
    ) = PatientDetailDto(
      id = entity.id,
      hospitalId = entity.hospitalId,
      mrn = entity.mrn,
      externalId = entity.externalId,
      title = entity.title,
      firstName = entity.firstName,
      middleName = entity.middleName,
      lastName = entity.lastName,
      preferredName = entity.preferredName,
      dateOfBirth = entity.dateOfBirth,
      gender = entity.gender,
      bloodGroup = entity.bloodGroup,
      nationality = entity.nationality,
      religion = entity.religion,
      maritalStatus = entity.maritalStatus,
      occupation = entity.occupation,
      photoUrl = entity.photoUrl,
      phone = entity.phone,
      alternatePhone = entity.alternatePhone,
      email = entity.email,
      addressLine1 = entity.addressLine1,
      addressLine2 = entity.addressLine2,
      city = entity.city,
      country = entity.country,
      postalCode = entity.postalCode,
      emergencyContactName = entity.emergencyContactName,
      emergencyContactPhone = entity.emergencyContactPhone,
      emergencyContactRelation = entity.emergencyContactRelation,
      qid = entity.qid,
      passportNumber = entity.passportNumber,
      idType = entity.idType,
      idNumber = entity.idNumber,
      insuranceCompany = entity.insuranceCompany,
      insuranceMemberId = entity.insuranceMemberId,
      insurancePolicyNo = entity.insurancePolicyNo,
      insurancePlan = entity.insurancePlan,
      isDeceased = entity.isDeceased,
      deathDate = entity.deathDate,
      deceasedNote = entity.deceasedNote,
      languagePreference = entity.languagePreference,
      communicationPreference = entity.communicationPreference,
      registrationChannel = entity.registrationChannel,
      recordsStatus = entity.recordsStatus,
      isUnknown = entity.isUnknown,
      identifiers = identifiers,
      contactPoints = contactPoints,
      addresses = addresses,
      allergies = allergies,
      conditions = conditions,
      notes = notes,
      relationships = relationships,
    )
  }
}

data class PhotoDto(
  val photoKey: String? = null,
)

data class PatientIdentifierDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val idType: String? = null,
  val idValue: String? = null,
  val issuingAuthority: String? = null,
  val primaryFlag: Boolean? = null,
) {
  companion object {
    fun from(entity: PatientIdentifier) =
      PatientIdentifierDto(
        id = entity.id,
        patientId = entity.patientId,
        idType = entity.idType,
        idValue = entity.idValue,
        issuingAuthority = entity.issuingAuthority,
        primaryFlag = entity.primaryFlag,
      )
  }
}

data class PatientRelationshipDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val relatedPatientId: UUID? = null,
  val relationshipType: String? = null,
  val isPrimaryGuardian: Boolean? = null,
  val notes: String? = null,
  /** true when the row was matched in reverse (this patient is the related party). */
  val reverse: Boolean = false,
) {
  companion object {
    fun from(
      entity: PatientRelationship,
      reverse: Boolean = false,
    ) = PatientRelationshipDto(
      id = entity.id,
      patientId = entity.patientId,
      relatedPatientId = entity.relatedPatientId,
      relationshipType = entity.relationshipType,
      isPrimaryGuardian = entity.isPrimaryGuardian,
      notes = entity.notes,
      reverse = reverse,
    )
  }
}

data class ContactPointDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val type: String? = null,
  val value: String? = null,
  val purpose: String? = null,
  val isPrimary: Boolean? = null,
) {
  companion object {
    fun from(entity: ContactPoint) =
      ContactPointDto(
        id = entity.id,
        patientId = entity.patientId,
        type = entity.type,
        value = entity.value,
        purpose = entity.purpose,
        isPrimary = entity.isPrimary,
      )
  }
}

data class AddressDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val type: String? = null,
  val line1: String? = null,
  val line2: String? = null,
  val city: String? = null,
  val stateProvince: String? = null,
  val country: String? = null,
  val postalCode: String? = null,
  val isPrimary: Boolean? = null,
) {
  companion object {
    fun from(entity: Address) =
      AddressDto(
        id = entity.id,
        patientId = entity.patientId,
        type = entity.type,
        line1 = entity.line1,
        line2 = entity.line2,
        city = entity.city,
        stateProvince = entity.stateProvince,
        country = entity.country,
        postalCode = entity.postalCode,
        isPrimary = entity.isPrimary,
      )
  }
}

data class AllergyDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val allergen: String? = null,
  val reaction: String? = null,
  val severity: String? = null,
  val recordedDate: LocalDate? = null,
  val isActive: Boolean? = null,
  val notes: String? = null,
) {
  companion object {
    fun from(entity: Allergy) =
      AllergyDto(
        id = entity.id,
        patientId = entity.patientId,
        allergen = entity.allergen,
        reaction = entity.reaction,
        severity = entity.severity,
        recordedDate = entity.recordedDate,
        isActive = entity.isActive,
        notes = entity.notes,
      )
  }
}

data class NoteDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val authorId: UUID? = null,
  val title: String? = null,
  val body: String? = null,
  val isFlagged: Boolean? = null,
  val createdAt: Instant? = null,
) {
  companion object {
    fun from(entity: PatientNote) =
      NoteDto(
        id = entity.id,
        patientId = entity.patientId,
        authorId = entity.authorId,
        title = entity.title,
        body = entity.body,
        isFlagged = entity.isFlagged,
        createdAt = entity.createdAt,
      )
  }
}

data class MedicalHistoryDto(
  val id: UUID? = null,
  val patientId: UUID? = null,
  val conditionName: String? = null,
  val diagnosisCode: String? = null,
  val diagnosisSystem: String? = null,
  val onsetDate: LocalDate? = null,
  val resolvedDate: LocalDate? = null,
  val notes: String? = null,
) {
  companion object {
    fun from(entity: MedicalHistory) =
      MedicalHistoryDto(
        id = entity.id,
        patientId = entity.patientId,
        conditionName = entity.conditionName,
        diagnosisCode = entity.diagnosisCode,
        diagnosisSystem = entity.diagnosisSystem,
        onsetDate = entity.onsetDate,
        resolvedDate = entity.resolvedDate,
        notes = entity.notes,
      )
  }
}

data class MatchCandidateDto(
  val patientId: UUID,
  val mrn: String?,
  val name: String?,
  val dob: LocalDate?,
  val gender: String?,
  val score: Int,
  val matchedOn: List<String> = emptyList(),
)

data class MergeResponseDto(
  val patient: PatientDetailDto,
  val moved: Map<String, Int> = emptyMap(),
  val skipped: List<String> = emptyList(),
  /** patient_merge_log row executing the merge (null only in legacy callers). */
  val mergeId: UUID? = null,
)

data class MergePatientsDto(
  val targetId: UUID,
  val duplicateId: UUID,
)

data class EmpiReviewDto(
  val id: UUID?,
  val hospitalId: UUID,
  val targetPatientId: UUID,
  val duplicatePatientId: UUID,
  val score: Int,
  val matchedOn: List<String>,
  val status: String,
  val submittedBy: UUID,
  val decidedBy: UUID? = null,
  val decidedAt: Instant? = null,
  val mergeId: UUID? = null,
  val createdAt: Instant? = null,
) {
  companion object {
    fun from(entity: EmpiReview) =
      EmpiReviewDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        targetPatientId = entity.targetPatientId,
        duplicatePatientId = entity.duplicatePatientId,
        score = entity.score,
        matchedOn = entity.matchedOn.split(",").filter { it.isNotBlank() },
        status = entity.status,
        submittedBy = entity.submittedBy,
        decidedBy = entity.decidedBy,
        decidedAt = entity.decidedAt,
        mergeId = entity.mergeId,
        createdAt = entity.createdAt,
      )
  }
}

data class MergeLogDto(
  val id: UUID?,
  val hospitalId: UUID,
  val targetPatientId: UUID,
  val duplicatePatientId: UUID,
  val mergeVersion: Int,
  val status: String,
  val mergedBy: UUID,
  val mergedAt: Instant? = null,
  val unmergedBy: UUID? = null,
  val unmergedAt: Instant? = null,
  val moved: Map<String, Int> = emptyMap(),
) {
  companion object {
    fun from(
      entity: PatientMergeLog,
      snapshot: Map<String, List<String>>? = null,
    ) = MergeLogDto(
      id = entity.id,
      hospitalId = entity.hospitalId,
      targetPatientId = entity.targetPatientId,
      duplicatePatientId = entity.duplicatePatientId,
      mergeVersion = entity.mergeVersion,
      status = entity.status,
      mergedBy = entity.mergedBy,
      mergedAt = entity.mergedAt,
      unmergedBy = entity.unmergedBy,
      unmergedAt = entity.unmergedAt,
      moved =
        snapshot?.let { snap ->
          mapOf(
            "identifiers" to snap["identifiers"].orEmpty().size,
            "contacts" to snap["contacts"].orEmpty().size,
            "addresses" to snap["addresses"].orEmpty().size,
            "allergies" to snap["allergies"].orEmpty().size,
            "notes" to snap["notes"].orEmpty().size,
            "history" to snap["history"].orEmpty().size,
            "episodes" to snap["episodes"].orEmpty().size,
            "relationships" to snap["relationshipsOut"].orEmpty().size + snap["relationshipsIn"].orEmpty().size,
          )
        } ?: emptyMap(),
    )
  }
}

data class PatientCreateResponse(
  val patient: PatientDetailDto,
  val matches: List<MatchCandidateDto> = emptyList(),
)
