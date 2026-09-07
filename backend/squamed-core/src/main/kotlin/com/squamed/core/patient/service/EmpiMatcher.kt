package com.squamed.core.patient.service

import com.squamed.core.patient.api.MatchCandidateDto
import com.squamed.core.patient.api.PatientCreateDto
import com.squamed.core.patient.domain.PatientIdentifier
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.PatientIdentifierRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.stereotype.Service
import java.util.LinkedHashMap
import java.util.UUID

@Service
class EmpiMatcher(
  private val patientRecordRepository: PatientRecordRepository,
  private val patientIdentifierRepository: PatientIdentifierRepository,
) {
  companion object {
    private const val POSSIBLE_MATCH_THRESHOLD = 60
  }

  fun findMatches(dto: PatientCreateDto): List<MatchCandidateDto> {
    val candidates = collectCandidates(dto)
    val identifierByPatientId =
      candidates
        .mapNotNull { it.id }
        .associateWith { patientIdentifierRepository.findByPatientId(it) }
    return candidates
      .mapNotNull { candidate ->
        val id = candidate.id ?: return@mapNotNull null
        scoreMatch(candidate, dto, identifierByPatientId[id].orEmpty())
      }.filter { it.score >= POSSIBLE_MATCH_THRESHOLD }
      .sortedWith(compareByDescending<MatchCandidateDto> { it.score }.thenBy { it.patientId.toString() })
  }

  private fun collectCandidates(dto: PatientCreateDto): List<PatientRecord> {
    val candidates = LinkedHashMap<UUID, PatientRecord>()

    fun add(records: List<PatientRecord>) {
      for (record in records) {
        record.id?.let { candidates.putIfAbsent(it, record) }
      }
    }

    val qid = dto.qid?.trim()
    if (!qid.isNullOrEmpty()) {
      add(patientRecordRepository.findByQid(qid))
    }
    val passport = dto.passportNumber?.trim()
    if (!passport.isNullOrEmpty()) {
      add(patientRecordRepository.findByPassportNumber(passport))
    }
    val idNumber = dto.idNumber?.trim()
    if (!idNumber.isNullOrEmpty()) {
      add(patientRecordRepository.findByIdNumber(idNumber))
    }
    val phone = dto.phone?.trim()
    if (!phone.isNullOrEmpty()) {
      add(patientRecordRepository.findByPhone(phone))
    }
    val email = dto.email?.trim()
    if (!email.isNullOrEmpty()) {
      add(patientRecordRepository.findByEmailIgnoreCase(email))
    }
    val firstName = dto.firstName?.trim()
    val lastName = dto.lastName?.trim()
    if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty()) {
      add(patientRecordRepository.findByLastNameContainingIgnoreCaseAndFirstNameContainingIgnoreCase(lastName, firstName))
    }
    for (inputIdentifier in dto.identifiers.orEmpty()) {
      val value = inputIdentifier.idValue?.trim()
      if (!value.isNullOrEmpty()) {
        val ids = patientIdentifierRepository.findByIdValue(value).map { it.patientId }
        if (ids.isNotEmpty()) {
          add(patientRecordRepository.findAllById(ids))
        }
      }
    }
    return candidates.values.toList()
  }

  private fun scoreMatch(
    candidate: PatientRecord,
    dto: PatientCreateDto,
    candidateIdentifiers: List<PatientIdentifier>,
  ): MatchCandidateDto? {
    data class Rule(
      val score: Int,
      val reason: String,
    )

    val rules = mutableListOf<Rule>()

    val dtoQid = dto.qid?.trim()
    val dtoPassport = dto.passportNumber?.trim()
    val dtoIdNumber = dto.idNumber?.trim()
    val dtoPhone = dto.phone?.trim()
    val dtoEmail = dto.email?.trim()

    if (!dtoQid.isNullOrEmpty() && dtoQid.equals(candidate.qid, ignoreCase = true)) {
      rules += Rule(100, "QID")
    }
    if (!dtoPassport.isNullOrEmpty() && dtoPassport.equals(candidate.passportNumber, ignoreCase = true)) {
      rules += Rule(98, "PASSPORT")
    }
    if (!dtoIdNumber.isNullOrEmpty() && dtoIdNumber.equals(candidate.idNumber, ignoreCase = true)) {
      val score = if (!dto.idType.isNullOrBlank() && dto.idType.equals(candidate.idType, ignoreCase = true)) 96 else 95
      rules += Rule(score, "ID_NUMBER")
    }
    if (!dtoPhone.isNullOrEmpty() && dtoPhone == candidate.phone?.trim()) {
      rules += Rule(95, "PHONE")
    }
    if (!dtoEmail.isNullOrEmpty() && dtoEmail.equals(candidate.email, ignoreCase = true)) {
      rules += Rule(95, "EMAIL")
    }

    for (identifier in candidateIdentifiers) {
      when {
        identifier.idType.equals("QID", ignoreCase = true) &&
          !dtoQid.isNullOrEmpty() &&
          identifier.idValue.trim().equals(dtoQid, ignoreCase = true) -> {
          rules += Rule(97, "QID")
        }

        identifier.idType.equals("PASSPORT", ignoreCase = true) &&
          !dtoPassport.isNullOrEmpty() &&
          identifier.idValue.trim().equals(dtoPassport, ignoreCase = true) -> {
          rules += Rule(97, "PASSPORT")
        }

        identifier.idType.equals("ID_NUMBER", ignoreCase = true) &&
          !dtoIdNumber.isNullOrEmpty() &&
          identifier.idValue.trim().equals(dtoIdNumber, ignoreCase = true) -> {
          rules += Rule(95, "ID_NUMBER")
        }
      }
    }

    for (inputIdentifier in dto.identifiers.orEmpty()) {
      val inputType = inputIdentifier.idType?.trim()
      val inputValue = inputIdentifier.idValue?.trim()
      if (inputValue.isNullOrEmpty()) continue
      when {
        inputType.equals("QID", ignoreCase = true) && inputValue.equals(candidate.qid, ignoreCase = true) -> {
          rules += Rule(97, "QID")
        }

        inputType.equals("PASSPORT", ignoreCase = true) && inputValue.equals(candidate.passportNumber, ignoreCase = true) -> {
          rules += Rule(97, "PASSPORT")
        }

        inputType.equals("ID_NUMBER", ignoreCase = true) && inputValue.equals(candidate.idNumber, ignoreCase = true) -> {
          rules += Rule(96, "ID_NUMBER")
        }
      }
      val exactIdentifier =
        candidateIdentifiers.any {
          it.idType.equals(inputType, ignoreCase = true) && it.idValue.trim().equals(inputValue, ignoreCase = true)
        }
      if (exactIdentifier) {
        rules += Rule(96, "IDENTIFIER")
      }
    }

    val inputName = buildName(dto.firstName, dto.middleName, dto.lastName)
    val candidateName = buildName(candidate.firstName, candidate.middleName, candidate.lastName)
    val dobMatches = dto.dateOfBirth != null && dto.dateOfBirth == candidate.dateOfBirth

    if (dobMatches && namesEqualExact(candidate, dto) && !dto.gender.isNullOrBlank() &&
      dto.gender.equals(candidate.gender, ignoreCase = true)
    ) {
      rules += Rule(85, "NAME_DOB_GENDER")
    }

    if (dobMatches && inputName.isNotBlank() && candidateName.isNotBlank()) {
      val similarity = nameSimilarity(inputName, candidateName)
      if (similarity >= 0.7) {
        val fuzzyScore =
          when {
            normalize(inputName) == normalize(candidateName) && similarity >= 0.85 -> 80
            normalize(inputName) == normalize(candidateName) -> 76
            similarity >= 0.9 -> 74
            similarity >= 0.8 -> 72
            else -> 70
          }
        rules += Rule(fuzzyScore, "NAME_DOB")
      }
    }

    val best = rules.maxByOrNull { it.score } ?: return null
    val matchedOn = rules.filter { it.score == best.score }.map { it.reason }.distinct()
    return MatchCandidateDto(
      patientId = candidate.id ?: return null,
      mrn = candidate.mrn,
      name = candidateName,
      dob = candidate.dateOfBirth,
      gender = candidate.gender,
      score = best.score,
      matchedOn = matchedOn,
    )
  }

  private fun namesEqualExact(
    candidate: PatientRecord,
    dto: PatientCreateDto,
  ): Boolean {
    val inputFirst = dto.firstName?.trim()
    val inputLast = dto.lastName?.trim()
    return !inputFirst.isNullOrEmpty() &&
      !inputLast.isNullOrEmpty() &&
      candidate.firstName.equals(inputFirst, ignoreCase = true) &&
      candidate.lastName.equals(inputLast, ignoreCase = true)
  }

  private fun buildName(
    first: String?,
    middle: String?,
    last: String?,
  ): String = listOf(first, middle, last).filter { !it.isNullOrBlank() }.joinToString(" ").trim()

  private fun nameSimilarity(
    input: String,
    candidate: String,
  ): Double {
    if (input.isBlank() || candidate.isBlank()) return 0.0
    val distance = levenshtein(input, candidate)
    return 1.0 - distance.toDouble() / maxOf(input.length, candidate.length).toDouble()
  }

  private fun normalize(value: String?): String {
    val cleaned = value?.lowercase()?.filter { it in 'a'..'z' } ?: ""
    if (cleaned.isEmpty()) return ""
    return cleaned.first().toString() + cleaned.length.toString().padStart(4, '0')
  }

  private fun levenshtein(
    a: String,
    b: String,
  ): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
      current[0] = i
      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
      }
      val swap = previous
      previous = current
      current = swap
    }
    return previous[b.length]
  }
}
