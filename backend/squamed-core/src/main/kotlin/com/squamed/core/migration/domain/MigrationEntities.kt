package com.squamed.core.migration.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "job", schema = "migration")
class MigrationJob(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "source_type", nullable = false, length = 20) var sourceType: String,
  @Column(name = "entity_type", nullable = false, length = 30) var entityType: String = "PATIENT",
  @Column(name = "status", nullable = false, length = 20) var status: String = "CREATED",
  @Column(name = "total_rows", nullable = false) var totalRows: Int = 0,
  @Column(name = "valid_rows", nullable = false) var validRows: Int = 0,
  @Column(name = "error_rows", nullable = false) var errorRows: Int = 0,
  @Column(name = "imported_at") var importedAt: Instant? = null,
  @Column(name = "rolled_back_at") var rolledBackAt: Instant? = null,
) : BaseEntity()

@Entity
@Table(name = "staged_row", schema = "migration")
class StagedRow(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "job_id", nullable = false) var jobId: UUID,
  @Column(name = "row_no", nullable = false) var rowNo: Int,
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  var payload: com.fasterxml.jackson.databind.JsonNode,
  @Column(name = "status", nullable = false, length = 20) var status: String = "VALID",
  @Column(name = "error_message", length = 400) var errorMessage: String? = null,
  @Column(name = "entity_id") var entityId: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "participation", schema = "benchmark")
class BenchmarkParticipation(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "enabled", nullable = false) var enabled: Boolean = false,
) : BaseEntity()

@Entity
@Table(name = "metric", schema = "esg")
class EsgMetric(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "period", nullable = false, length = 7) var period: String,
  @Column(name = "metric_type", nullable = false, length = 20) var metricType: String,
  @Column(name = "value", nullable = false) var value: BigDecimal,
) : BaseEntity()
