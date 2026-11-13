package com.squamed.core.ipd.repository

import com.squamed.core.ipd.domain.Admission
import com.squamed.core.ipd.domain.AdmissionTransfer
import com.squamed.core.ipd.domain.Attendant
import com.squamed.core.ipd.domain.DischargeSummary
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AdmissionRepository : JpaRepository<Admission, UUID> {
  fun findByPatientId(patientId: UUID): List<Admission>

  fun findByStatus(status: String): List<Admission>

  fun findByWardId(wardId: UUID): List<Admission>

  fun findByBedId(bedId: UUID): List<Admission>
}

interface AdmissionTransferRepository : JpaRepository<AdmissionTransfer, UUID> {
  fun findByAdmissionIdOrderByTransferredAtDesc(admissionId: UUID): List<AdmissionTransfer>
}

interface DischargeSummaryRepository : JpaRepository<DischargeSummary, UUID> {
  fun findByAdmissionId(admissionId: UUID): Optional<DischargeSummary>
}

interface AttendantRepository : JpaRepository<Attendant, UUID> {
  fun findByAdmissionId(admissionId: UUID): List<Attendant>
}
