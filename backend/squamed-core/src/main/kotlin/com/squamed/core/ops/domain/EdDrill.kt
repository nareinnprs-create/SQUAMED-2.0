package com.squamed.core.ops.domain

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** A scripted mass-casualty exercise over the real ED endpoints, kept as evidence (V109). */
@Entity
@Table(name = "ed_drill", schema = "ops")
class EdDrill(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "name", nullable = false, length = 150) var name: String,
  @Column(name = "planned_casualties", nullable = false) var plannedCasualties: Int,
  @Column(name = "created_casualties", nullable = false) var createdCasualties: Int = 0,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "triage_distribution", columnDefinition = "jsonb")
  var triageDistribution: JsonNode? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "RUNNING",
  @Column(name = "detail", length = 500) var detail: String? = null,
  @Column(name = "declared_at") var declaredAt: Instant? = null,
  @Column(name = "stood_down_at") var stoodDownAt: Instant? = null,
  @Column(name = "elapsed_ms") var elapsedMs: Long? = null,
) : BaseEntity()
