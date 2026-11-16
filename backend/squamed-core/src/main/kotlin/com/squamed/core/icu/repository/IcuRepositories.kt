package com.squamed.core.icu.repository

import com.squamed.core.icu.domain.IcuCriticalAlert
import com.squamed.core.icu.domain.IcuFluidBalance
import com.squamed.core.icu.domain.IcuInfusionRecord
import com.squamed.core.icu.domain.IcuVentilationRecord
import com.squamed.core.icu.domain.IcuVitalsEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IcuVitalsEntryRepository : JpaRepository<IcuVitalsEntry, UUID> {
  // 00.04 tenant sweep: hid = caller's bound hospitalId (null = admin = unfiltered).
  @Query(
    """
    select v from IcuVitalsEntry v
    where v.patientId = :patientId and (:hid is null or v.hospitalId = :hid)
    order by v.recordedAt desc
    """,
  )
  fun findByPatientIdOrderByRecordedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<IcuVitalsEntry>

  @Query(
    "select v from IcuVitalsEntry v where (:hid is null or v.hospitalId = :hid) order by v.recordedAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<IcuVitalsEntry>
}

interface IcuFluidBalanceRepository : JpaRepository<IcuFluidBalance, UUID> {
  @Query(
    """
    select f from IcuFluidBalance f
    where f.patientId = :patientId and (:hid is null or f.hospitalId = :hid)
    order by f.balanceAt desc
    """,
  )
  fun findByPatientIdOrderByBalanceAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<IcuFluidBalance>

  @Query(
    "select f from IcuFluidBalance f where (:hid is null or f.hospitalId = :hid) order by f.balanceAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<IcuFluidBalance>
}

interface IcuVentilationRecordRepository : JpaRepository<IcuVentilationRecord, UUID> {
  @Query(
    """
    select v from IcuVentilationRecord v
    where v.patientId = :patientId and (:hid is null or v.hospitalId = :hid)
    order by v.recordedAt desc
    """,
  )
  fun findByPatientIdOrderByRecordedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<IcuVentilationRecord>

  @Query(
    "select v from IcuVentilationRecord v where (:hid is null or v.hospitalId = :hid) order by v.recordedAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<IcuVentilationRecord>
}

interface IcuInfusionRecordRepository : JpaRepository<IcuInfusionRecord, UUID> {
  @Query(
    """
    select i from IcuInfusionRecord i
    where i.patientId = :patientId and (:hid is null or i.hospitalId = :hid)
    order by i.startedAt desc
    """,
  )
  fun findByPatientIdOrderByStartedAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<IcuInfusionRecord>

  @Query(
    "select i from IcuInfusionRecord i where (:hid is null or i.hospitalId = :hid) order by i.startedAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<IcuInfusionRecord>
}

interface IcuCriticalAlertRepository : JpaRepository<IcuCriticalAlert, UUID> {
  @Query(
    """
    select a from IcuCriticalAlert a
    where a.patientId = :patientId and (:hid is null or a.hospitalId = :hid)
    order by a.triggeredAt desc
    """,
  )
  fun findByPatientIdOrderByTriggeredAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("hid") hid: UUID?,
  ): List<IcuCriticalAlert>

  @Query(
    """
    select a from IcuCriticalAlert a
    where a.patientId = :patientId and a.status = :status and (:hid is null or a.hospitalId = :hid)
    order by a.triggeredAt desc
    """,
  )
  fun findByPatientIdAndStatusOrderByTriggeredAtDesc(
    @Param("patientId") patientId: UUID,
    @Param("status") status: String,
    @Param("hid") hid: UUID?,
  ): List<IcuCriticalAlert>

  @Query(
    "select a from IcuCriticalAlert a where (:hid is null or a.hospitalId = :hid) order by a.triggeredAt desc",
  )
  fun findAllByHospital(
    @Param("hid") hid: UUID?,
  ): List<IcuCriticalAlert>
}
