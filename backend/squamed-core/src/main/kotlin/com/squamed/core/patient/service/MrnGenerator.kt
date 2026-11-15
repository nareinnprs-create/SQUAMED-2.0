package com.squamed.core.patient.service

import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.stereotype.Service
import java.time.Year

@Service
class MrnGenerator(
  private val patientRecordRepository: PatientRecordRepository,
) {
  companion object {
    private const val MRN_PREFIX = "MQ"
  }

  /**
   * Collision-safe MRN (V53): `nextval` is atomic under concurrency, so concurrent creates can never
   * mint the same MRN (the old max+1 lookup raced and produced 409s on the unique constraint).
   */
  fun next(): String {
    val sequence = patientRecordRepository.nextMrnSequence()
    return String.format("%s-%d-%04d", MRN_PREFIX, Year.now().value, sequence)
  }
}
