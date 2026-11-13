package com.squamed.core.nursing.repository

import com.squamed.core.nursing.domain.MedicationAdministrationRecord
import com.squamed.core.nursing.domain.NursingAssessment
import com.squamed.core.nursing.domain.NursingCarePlan
import com.squamed.core.nursing.domain.NursingHandover
import com.squamed.core.nursing.domain.NursingNote
import com.squamed.core.nursing.domain.RiskAssessment
import com.squamed.core.nursing.domain.VitalsEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NursingAssessmentRepository : JpaRepository<NursingAssessment, UUID> {
  fun findByPatientIdOrderByAssessedAtDesc(patientId: UUID): List<NursingAssessment>
}

interface NursingCarePlanRepository : JpaRepository<NursingCarePlan, UUID> {
  fun findByPatientIdOrderByCreatedAtDesc(patientId: UUID): List<NursingCarePlan>
}

interface NursingNoteRepository : JpaRepository<NursingNote, UUID> {
  fun findByPatientIdOrderByAuthoredAtDesc(patientId: UUID): List<NursingNote>
}

interface NursingHandoverRepository : JpaRepository<NursingHandover, UUID> {
  fun findByWardIdOrderByAuthoredAtDesc(wardId: UUID): List<NursingHandover>

  fun findAllByOrderByAuthoredAtDesc(): List<NursingHandover>
}

interface VitalsEntryRepository : JpaRepository<VitalsEntry, UUID> {
  fun findByPatientIdOrderByRecordedAtDesc(patientId: UUID): List<VitalsEntry>
}

interface MedicationAdministrationRecordRepository : JpaRepository<MedicationAdministrationRecord, UUID> {
  fun findByPatientIdOrderByScheduledAtDesc(patientId: UUID): List<MedicationAdministrationRecord>
}

interface RiskAssessmentRepository : JpaRepository<RiskAssessment, UUID> {
  fun findByPatientIdOrderByAssessedAtDesc(patientId: UUID): List<RiskAssessment>
}
