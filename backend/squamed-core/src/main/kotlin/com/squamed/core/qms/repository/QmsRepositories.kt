package com.squamed.core.qms.repository

import com.squamed.core.qms.domain.Capa
import com.squamed.core.qms.domain.HandHygiene
import com.squamed.core.qms.domain.InfectionCase
import com.squamed.core.qms.domain.LitigationHold
import com.squamed.core.qms.domain.Outbreak
import com.squamed.core.qms.domain.QmsAudit
import com.squamed.core.qms.domain.QmsIndicator
import com.squamed.core.qms.domain.QmsRisk
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface QmsIndicatorRepository : JpaRepository<QmsIndicator, UUID> {
  fun findByHospitalIdAndCodeAndPeriod(
    hospitalId: UUID,
    code: String,
    period: String,
  ): QmsIndicator?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<QmsIndicator>
}

interface CapaRepository : JpaRepository<Capa, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Capa?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<Capa>

  fun findByHospitalIdAndStatusOrderByCreatedAtDesc(
    hospitalId: UUID,
    status: String,
  ): List<Capa>
}

interface QmsAuditRepository : JpaRepository<QmsAudit, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): QmsAudit?

  fun findByHospitalIdOrderByScheduledOnDesc(hospitalId: UUID): List<QmsAudit>
}

interface QmsRiskRepository : JpaRepository<QmsRisk, UUID> {
  fun findByHospitalIdOrderByScoreDesc(hospitalId: UUID): List<QmsRisk>
}

interface InfectionCaseRepository : JpaRepository<InfectionCase, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): InfectionCase?

  fun findByHospitalIdOrderByIdentifiedOnDesc(hospitalId: UUID): List<InfectionCase>

  fun findByHospitalIdAndStatus(
    hospitalId: UUID,
    status: String,
  ): List<InfectionCase>
}

interface OutbreakRepository : JpaRepository<Outbreak, UUID> {
  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): Outbreak?

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Outbreak?

  fun findByHospitalIdAndStatus(
    hospitalId: UUID,
    status: String,
  ): List<Outbreak>
}

interface HandHygieneRepository : JpaRepository<HandHygiene, UUID> {
  fun findByHospitalIdAndWardIdAndPeriod(
    hospitalId: UUID,
    wardId: UUID?,
    period: String,
  ): HandHygiene?

  fun findByHospitalIdOrderByPeriodDesc(hospitalId: UUID): List<HandHygiene>
}

interface LitigationHoldRepository : JpaRepository<LitigationHold, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): LitigationHold?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<LitigationHold>
}
