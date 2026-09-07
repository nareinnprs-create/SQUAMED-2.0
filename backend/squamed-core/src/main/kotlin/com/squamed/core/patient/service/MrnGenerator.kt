package com.squamed.core.patient.service

import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.stereotype.Service
import java.time.Year

@Service
class MrnGenerator(
  private val patientRecordRepository: PatientRecordRepository,
) {
  companion object {
    private const val MAX_ATTEMPTS = 3
    private const val MRN_PREFIX = "MQ"
  }

  fun next(): String {
    repeat(MAX_ATTEMPTS) {
      val mrn = generate()
      if (patientRecordRepository.findByMrnIgnoreCase(mrn) == null) {
        return mrn
      }
    }
    throw IllegalStateException("MRN_GENERATION_FAILED")
  }

  private fun generate(): String {
    val year = Year.now().value
    val sequence = latestSequence(year)
    return String.format("%s-%d-%04d", MRN_PREFIX, year, sequence)
  }

  private fun latestSequence(year: Int): Int {
    val latest = patientRecordRepository.findTopByOrderByMrnDesc() ?: return 1
    if (latest.mrn.isBlank()) return 1
    val parts = latest.mrn.split("-")
    if (parts.size < 3) return 1
    val latestYear = parts[1].toIntOrNull() ?: return 1
    if (latestYear != year) return 1
    val sequence = parts[2].toIntOrNull() ?: return 1
    return sequence + 1
  }
}
