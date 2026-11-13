package com.squamed.core.anaesthesia.repository

import com.squamed.core.anaesthesia.domain.AnaesthesiaEvaluation
import com.squamed.core.anaesthesia.domain.AnaesthesiaMedication
import com.squamed.core.anaesthesia.domain.AnaesthesiaRecord
import com.squamed.core.anaesthesia.domain.IntraOpMonitoring
import com.squamed.core.anaesthesia.domain.PacuRecord
import com.squamed.core.anaesthesia.domain.RecoveryRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnaesthesiaEvaluationRepository : JpaRepository<AnaesthesiaEvaluation, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<AnaesthesiaEvaluation>

  fun findByPatientId(patientId: UUID): List<AnaesthesiaEvaluation>
}

interface AnaesthesiaRecordRepository : JpaRepository<AnaesthesiaRecord, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<AnaesthesiaRecord>

  fun findFirstBySurgeryScheduleIdOrderByCreatedAtDesc(surgeryScheduleId: UUID): AnaesthesiaRecord?
}

interface IntraOpMonitoringRepository : JpaRepository<IntraOpMonitoring, UUID> {
  fun findByAnaesthesiaRecordId(anaesthesiaRecordId: UUID): List<IntraOpMonitoring>
}

interface AnaesthesiaMedicationRepository : JpaRepository<AnaesthesiaMedication, UUID> {
  fun findByAnaesthesiaRecordId(anaesthesiaRecordId: UUID): List<AnaesthesiaMedication>
}

interface RecoveryRecordRepository : JpaRepository<RecoveryRecord, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<RecoveryRecord>
}

interface PacuRecordRepository : JpaRepository<PacuRecord, UUID> {
  fun findBySurgeryScheduleId(surgeryScheduleId: UUID): List<PacuRecord>
}
