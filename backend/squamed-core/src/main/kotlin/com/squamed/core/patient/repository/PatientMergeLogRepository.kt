package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientMergeLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatientMergeLogRepository : JpaRepository<PatientMergeLog, UUID> {
  fun findByHospitalIdOrderByMergedAtDesc(hospitalId: UUID): List<PatientMergeLog>

  fun findByHospitalIdAndTargetPatientIdAndDuplicatePatientId(
    hospitalId: UUID,
    targetPatientId: UUID,
    duplicatePatientId: UUID,
  ): List<PatientMergeLog>
}
