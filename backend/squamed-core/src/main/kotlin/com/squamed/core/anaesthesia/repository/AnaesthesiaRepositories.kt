package com.squamed.core.anaesthesia.repository

import com.squamed.core.anaesthesia.domain.AnaesthesiaEvaluation
import com.squamed.core.anaesthesia.domain.AnaesthesiaMedication
import com.squamed.core.anaesthesia.domain.AnaesthesiaRecord
import com.squamed.core.anaesthesia.domain.IntraOpMonitoring
import com.squamed.core.anaesthesia.domain.PacuRecord
import com.squamed.core.anaesthesia.domain.RecoveryRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AnaesthesiaEvaluationRepository : JpaRepository<AnaesthesiaEvaluation, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope (U13) — admin callers pass null.
  @Query("select e from AnaesthesiaEvaluation e where (:hid is null or e.hospitalId = :hid) and e.surgeryScheduleId = :surgeryScheduleId")
  fun findBySurgeryScheduleId(
    @Param("hid") hospitalId: UUID?,
    @Param("surgeryScheduleId") surgeryScheduleId: UUID,
  ): List<AnaesthesiaEvaluation>

  @Query("select e from AnaesthesiaEvaluation e where (:hid is null or e.hospitalId = :hid) and e.patientId = :patientId")
  fun findByPatientId(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<AnaesthesiaEvaluation>

  @Query("select e from AnaesthesiaEvaluation e where (:hid is null or e.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<AnaesthesiaEvaluation>
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
