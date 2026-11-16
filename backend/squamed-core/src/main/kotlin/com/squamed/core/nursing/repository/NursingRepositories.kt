package com.squamed.core.nursing.repository

import com.squamed.core.nursing.domain.MedicationAdministrationRecord
import com.squamed.core.nursing.domain.NursingAssessment
import com.squamed.core.nursing.domain.NursingCarePlan
import com.squamed.core.nursing.domain.NursingHandover
import com.squamed.core.nursing.domain.NursingNote
import com.squamed.core.nursing.domain.RiskAssessment
import com.squamed.core.nursing.domain.VitalsEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NursingAssessmentRepository : JpaRepository<NursingAssessment, UUID> {
  // 00.04 tenant sweep: hid = caller's bound hospitalId (null = admin = unfiltered).
  @Query(
    """
    select a from NursingAssessment a
    where a.patientId = :patientId and (:hid is null or a.hospitalId = :hid)
    order by a.assessedAt desc
    """,
  )
  fun findByPatientIdOrderByAssessedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<NursingAssessment>

  @Query(
    "select a from NursingAssessment a where (:hid is null or a.hospitalId = :hid) order by a.assessedAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<NursingAssessment>
}

interface NursingCarePlanRepository : JpaRepository<NursingCarePlan, UUID> {
  @Query(
    """
    select p from NursingCarePlan p
    where p.patientId = :patientId and (:hid is null or p.hospitalId = :hid)
    order by p.createdAt desc
    """,
  )
  fun findByPatientIdOrderByCreatedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<NursingCarePlan>

  @Query(
    "select p from NursingCarePlan p where (:hid is null or p.hospitalId = :hid) order by p.createdAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<NursingCarePlan>
}

interface NursingNoteRepository : JpaRepository<NursingNote, UUID> {
  @Query(
    """
    select n from NursingNote n
    where n.patientId = :patientId and (:hid is null or n.hospitalId = :hid)
    order by n.authoredAt desc
    """,
  )
  fun findByPatientIdOrderByAuthoredAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<NursingNote>

  @Query(
    "select n from NursingNote n where (:hid is null or n.hospitalId = :hid) order by n.authoredAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<NursingNote>
}

interface NursingHandoverRepository : JpaRepository<NursingHandover, UUID> {
  @Query(
    """
    select h from NursingHandover h
    where h.wardId = :wardId and (:hid is null or h.hospitalId = :hid)
    order by h.authoredAt desc
    """,
  )
  fun findByWardIdOrderByAuthoredAtDesc(
    @Param("wardId") wardId: UUID,
    @Param("hid") hid: UUID?,
  ): List<NursingHandover>

  @Query(
    "select h from NursingHandover h where (:hid is null or h.hospitalId = :hid) order by h.authoredAt desc",
  )
  fun findAllByOrderByAuthoredAtDesc(
    @Param("hid") hid: UUID?,
  ): List<NursingHandover>
}

interface VitalsEntryRepository : JpaRepository<VitalsEntry, UUID> {
  @Query(
    """
    select v from VitalsEntry v
    where v.patientId = :patientId and (:hid is null or v.hospitalId = :hid)
    order by v.recordedAt desc
    """,
  )
  fun findByPatientIdOrderByRecordedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<VitalsEntry>

  @Query(
    "select v from VitalsEntry v where (:hid is null or v.hospitalId = :hid) order by v.recordedAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<VitalsEntry>
}

interface MedicationAdministrationRecordRepository : JpaRepository<MedicationAdministrationRecord, UUID> {
  @Query(
    """
    select m from MedicationAdministrationRecord m
    where m.patientId = :patientId and (:hid is null or m.hospitalId = :hid)
    order by m.scheduledAt desc
    """,
  )
  fun findByPatientIdOrderByScheduledAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<MedicationAdministrationRecord>

  @Query(
    "select m from MedicationAdministrationRecord m where (:hid is null or m.hospitalId = :hid) order by m.scheduledAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<MedicationAdministrationRecord>
}

interface RiskAssessmentRepository : JpaRepository<RiskAssessment, UUID> {
  @Query(
    """
    select r from RiskAssessment r
    where r.patientId = :patientId and (:hid is null or r.hospitalId = :hid)
    order by r.assessedAt desc
    """,
  )
  fun findByPatientIdOrderByAssessedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<RiskAssessment>

  @Query(
    "select r from RiskAssessment r where (:hid is null or r.hospitalId = :hid) order by r.assessedAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<RiskAssessment>
}
