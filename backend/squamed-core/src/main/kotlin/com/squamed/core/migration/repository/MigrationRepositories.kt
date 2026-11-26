package com.squamed.core.migration.repository

import com.squamed.core.migration.domain.BenchmarkParticipation
import com.squamed.core.migration.domain.EsgMetric
import com.squamed.core.migration.domain.MigrationJob
import com.squamed.core.migration.domain.StagedRow
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MigrationJobRepository : JpaRepository<MigrationJob, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): MigrationJob?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<MigrationJob>
}

interface StagedRowRepository : JpaRepository<StagedRow, UUID> {
  fun findByJobIdOrderByRowNoAsc(jobId: UUID): List<StagedRow>
}

interface BenchmarkParticipationRepository : JpaRepository<BenchmarkParticipation, UUID> {
  fun findByHospitalId(hospitalId: UUID): BenchmarkParticipation?

  fun findByHospitalIdInAndEnabledTrue(hospitalIds: Collection<UUID>): List<BenchmarkParticipation>
}

interface EsgMetricRepository : JpaRepository<EsgMetric, UUID> {
  fun findByHospitalIdAndPeriodOrderByMetricTypeAsc(
    hospitalId: UUID,
    period: String,
  ): List<EsgMetric>

  fun findByHospitalIdAndPeriodAndMetricType(
    hospitalId: UUID,
    period: String,
    metricType: String,
  ): EsgMetric?
}
