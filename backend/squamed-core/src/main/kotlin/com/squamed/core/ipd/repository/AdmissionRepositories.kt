package com.squamed.core.ipd.repository

import com.squamed.core.ipd.domain.Admission
import com.squamed.core.ipd.domain.AdmissionTransfer
import com.squamed.core.ipd.domain.Attendant
import com.squamed.core.ipd.domain.DischargeChecklist
import com.squamed.core.ipd.domain.DischargeSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface AdmissionRepository : JpaRepository<Admission, UUID> {
  // 00.04 tenant sweep: hid = caller's bound hospitalId (null = admin = unfiltered).
  @Query(
    """
    select a from Admission a
    where a.patientId = :patientId and (:hid is null or a.hospitalId = :hid)
    """,
  )
  fun findByPatientId(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<Admission>

  @Query(
    """
    select a from Admission a
    where a.status = :status and (:hid is null or a.hospitalId = :hid)
    """,
  )
  fun findByStatus(
    @Param("status") status: String,
    @Param("hid") hid: UUID?,
  ): List<Admission>

  @Query(
    """
    select a from Admission a
    where a.wardId = :wardId and (:hid is null or a.hospitalId = :hid)
    """,
  )
  fun findByWardId(
    @Param("wardId") wardId: UUID,
    @Param("hid") hid: UUID?,
  ): List<Admission>

  fun findByBedId(bedId: UUID): List<Admission>

  @Query(
    "select a from Admission a where (:hid is null or a.hospitalId = :hid)",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<Admission>
}

interface AdmissionTransferRepository : JpaRepository<AdmissionTransfer, UUID> {
  fun findByAdmissionIdOrderByTransferredAtDesc(admissionId: UUID): List<AdmissionTransfer>
}

interface DischargeSummaryRepository : JpaRepository<DischargeSummary, UUID> {
  fun findByAdmissionId(admissionId: UUID): Optional<DischargeSummary>
}

// 04.01 discharge checklist (one per admission).
interface DischargeChecklistRepository : JpaRepository<DischargeChecklist, UUID> {
  fun findByAdmissionId(admissionId: UUID): Optional<DischargeChecklist>
}

interface AttendantRepository : JpaRepository<Attendant, UUID> {
  fun findByAdmissionId(admissionId: UUID): List<Attendant>
}

// 04.08 dietary-lite.
interface DietOrderRepository : org.springframework.data.jpa.repository.JpaRepository<com.squamed.core.ipd.domain.DietOrder, java.util.UUID> {
  fun findByAdmissionIdAndIsActiveTrue(admissionId: java.util.UUID): List<com.squamed.core.ipd.domain.DietOrder>

  @org.springframework.data.jpa.repository.Query(
    "select d from DietOrder d where d.hospitalId = :hid and d.isActive = true order by d.dietType, d.orderedAt",
  )
  fun findActiveForHospital(
    @org.springframework.data.repository.query.Param("hid") hospitalId: java.util.UUID,
  ): List<com.squamed.core.ipd.domain.DietOrder>
}
