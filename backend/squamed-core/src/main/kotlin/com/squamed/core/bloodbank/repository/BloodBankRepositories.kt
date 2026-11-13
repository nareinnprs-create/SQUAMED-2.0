package com.squamed.core.bloodbank.repository

import com.squamed.core.bloodbank.domain.BloodDonor
import com.squamed.core.bloodbank.domain.BloodGrouping
import com.squamed.core.bloodbank.domain.BloodIssue
import com.squamed.core.bloodbank.domain.BloodReservation
import com.squamed.core.bloodbank.domain.BloodUnit
import com.squamed.core.bloodbank.domain.Crossmatch
import com.squamed.core.bloodbank.domain.TransfusionReaction
import com.squamed.core.bloodbank.domain.TransfusionRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BloodDonorRepository : JpaRepository<BloodDonor, UUID> {
  fun findByStatus(status: String): List<BloodDonor>

  fun findByBloodGroup(bloodGroup: String): List<BloodDonor>
}

interface BloodUnitRepository : JpaRepository<BloodUnit, UUID> {
  fun findByStatus(status: String): List<BloodUnit>

  fun findByBloodGroup(bloodGroup: String): List<BloodUnit>

  fun findByComponent(component: String): List<BloodUnit>
}

interface BloodGroupingRepository : JpaRepository<BloodGrouping, UUID> {
  fun findByUnitId(unitId: UUID): BloodGrouping?
}

interface CrossmatchRepository : JpaRepository<Crossmatch, UUID> {
  fun findByPatientId(patientId: UUID): List<Crossmatch>

  fun findByUnitId(unitId: UUID): List<Crossmatch>
}

interface BloodReservationRepository : JpaRepository<BloodReservation, UUID> {
  fun findByPatientId(patientId: UUID): List<BloodReservation>

  fun findByUnitId(unitId: UUID): List<BloodReservation>
}

interface BloodIssueRepository : JpaRepository<BloodIssue, UUID> {
  fun findByPatientId(patientId: UUID): List<BloodIssue>

  fun findByUnitId(unitId: UUID): List<BloodIssue>
}

interface TransfusionRecordRepository : JpaRepository<TransfusionRecord, UUID> {
  fun findByPatientId(patientId: UUID): List<TransfusionRecord>
}

interface TransfusionReactionRepository : JpaRepository<TransfusionReaction, UUID> {
  fun findByTransfusionRecordId(transfusionRecordId: UUID): List<TransfusionReaction>
}
