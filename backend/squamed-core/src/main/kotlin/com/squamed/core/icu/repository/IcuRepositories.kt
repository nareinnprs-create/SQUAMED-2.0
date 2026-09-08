package com.squamed.core.icu.repository

import com.squamed.core.icu.domain.IcuCriticalAlert
import com.squamed.core.icu.domain.IcuFluidBalance
import com.squamed.core.icu.domain.IcuInfusionRecord
import com.squamed.core.icu.domain.IcuVentilationRecord
import com.squamed.core.icu.domain.IcuVitalsEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IcuVitalsEntryRepository : JpaRepository<IcuVitalsEntry, UUID> {
  fun findByPatientIdOrderByRecordedAtDesc(patientId: UUID): List<IcuVitalsEntry>
}

interface IcuFluidBalanceRepository : JpaRepository<IcuFluidBalance, UUID> {
  fun findByPatientIdOrderByBalanceAtDesc(patientId: UUID): List<IcuFluidBalance>
}

interface IcuVentilationRecordRepository : JpaRepository<IcuVentilationRecord, UUID> {
  fun findByPatientIdOrderByRecordedAtDesc(patientId: UUID): List<IcuVentilationRecord>
}

interface IcuInfusionRecordRepository : JpaRepository<IcuInfusionRecord, UUID> {
  fun findByPatientIdOrderByStartedAtDesc(patientId: UUID): List<IcuInfusionRecord>
}

interface IcuCriticalAlertRepository : JpaRepository<IcuCriticalAlert, UUID> {
  fun findByPatientIdOrderByTriggeredAtDesc(patientId: UUID): List<IcuCriticalAlert>

  fun findByPatientIdAndStatusOrderByTriggeredAtDesc(
    patientId: UUID,
    status: String,
  ): List<IcuCriticalAlert>
}
