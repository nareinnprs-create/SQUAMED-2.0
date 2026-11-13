package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatientRecordRepository : JpaRepository<PatientRecord, UUID> {
  fun findByMrnIgnoreCase(mrn: String): PatientRecord?

  fun findByHospitalIdAndMrnIgnoreCase(
    hospitalId: UUID,
    mrn: String,
  ): PatientRecord?

  fun findByPhone(phone: String): List<PatientRecord>

  fun findByPhoneContaining(phone: String): List<PatientRecord>

  fun findByQid(qid: String): List<PatientRecord>

  fun findByEmailIgnoreCase(email: String): List<PatientRecord>

  fun findByLastNameContainingIgnoreCaseAndFirstNameContainingIgnoreCase(
    lastName: String,
    firstName: String,
  ): List<PatientRecord>

  fun findTopByOrderByMrnDesc(): PatientRecord?

  fun findByFirstNameContainingIgnoreCase(firstName: String): List<PatientRecord>

  fun findByLastNameContainingIgnoreCase(lastName: String): List<PatientRecord>

  fun findByPassportNumber(passportNumber: String): List<PatientRecord>

  fun findByIdNumber(idNumber: String): List<PatientRecord>

  fun findByHospitalId(hospitalId: UUID): List<PatientRecord>
}
