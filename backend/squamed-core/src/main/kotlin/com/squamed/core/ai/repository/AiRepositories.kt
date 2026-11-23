package com.squamed.core.ai.repository

import com.squamed.core.ai.domain.AgentConfig
import com.squamed.core.ai.domain.InteractionLog
import com.squamed.core.ai.domain.ModelConfig
import com.squamed.core.ai.domain.ToolConfig
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ModelConfigRepository : JpaRepository<ModelConfig, UUID> {
  fun findByProviderIgnoreCase(provider: String): List<ModelConfig>

  fun findByEnabledAndIsDefaultTrue(enabled: Boolean): ModelConfig?

  fun findAllByOrderByCreatedAtAsc(): List<ModelConfig>
}

interface AgentConfigRepository : JpaRepository<AgentConfig, UUID> {
  fun findByCodeIgnoreCase(code: String): AgentConfig?

  fun findAllByOrderByCreatedAtAsc(): List<AgentConfig>
}

interface ToolConfigRepository : JpaRepository<ToolConfig, UUID> {
  fun findByEnabledTrue(): List<ToolConfig>

  fun findAllByOrderByToolCodeAsc(): List<ToolConfig>
}

interface InteractionLogRepository : JpaRepository<InteractionLog, UUID> {
  // 00.04 tenant sweep: top-100 feed filtered on the caller's hospital (null = admin = unfiltered);
  // limit moves from the `Top100` derivation into the Pageable so the query can carry the hid predicate.
  @Query(
    """
    select l from InteractionLog l
    where (:hid is null or l.hospitalId = :hid)
    order by l.createdAt desc
    """,
  )
  fun findTop100ByOrderByCreatedAtDesc(
    @Param("hid") hid: UUID?,
    pageable: Pageable,
  ): List<InteractionLog>

  // 05.01: today's token spend per capability drives the hard daily cap.
  @Query(
    """
    select coalesce(sum(l.tokensUsed), 0) from InteractionLog l
    where l.hospitalId = :hid and l.capability = :cap and l.createdAt >= :from
    """,
  )
  fun sumTokensSince(
    @Param("hid") hid: UUID,
    @Param("cap") cap: String,
    @Param("from") from: java.time.Instant,
  ): Long

  @Query(
    """
    select coalesce(sum(l.costUsd), 0) from InteractionLog l
    where l.hospitalId = :hid and l.capability = :cap and l.createdAt >= :from
    """,
  )
  fun sumCostSince(
    @Param("hid") hid: UUID,
    @Param("cap") cap: String,
    @Param("from") from: java.time.Instant,
  ): java.math.BigDecimal

  // 05.07 cost dashboard: rows are [capability, requests, tokens, cost].
  @Query(
    """
    select l.capability, count(l), coalesce(sum(l.tokensUsed), 0), coalesce(sum(l.costUsd), 0)
    from InteractionLog l
    where l.hospitalId = :hid and l.createdAt between :from and :to
    group by l.capability order by l.capability
    """,
  )
  fun costByCapability(
    @Param("hid") hid: UUID,
    @Param("from") from: java.time.Instant,
    @Param("to") to: java.time.Instant,
  ): List<Array<Any?>>

  // Rows are [modelName, requests, tokens, cost].
  @Query(
    """
    select l.modelName, count(l), coalesce(sum(l.tokensUsed), 0), coalesce(sum(l.costUsd), 0)
    from InteractionLog l
    where l.hospitalId = :hid and l.createdAt between :from and :to
    group by l.modelName order by l.modelName
    """,
  )
  fun costByModel(
    @Param("hid") hid: UUID,
    @Param("from") from: java.time.Instant,
    @Param("to") to: java.time.Instant,
  ): List<Array<Any?>>
}

interface GatewayPolicyRepository : JpaRepository<com.squamed.core.ai.domain.GatewayPolicy, UUID> {
  fun findByHospitalIdAndCapability(
    hospitalId: UUID,
    capability: String,
  ): com.squamed.core.ai.domain.GatewayPolicy?

  fun findByHospitalIdOrderByCapabilityAsc(hospitalId: UUID): List<com.squamed.core.ai.domain.GatewayPolicy>
}

// 05.03 command-bar action drafts.
interface ActionDraftRepository : JpaRepository<com.squamed.core.ai.domain.ActionDraft, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): com.squamed.core.ai.domain.ActionDraft?

  fun findByHospitalIdAndStatusOrderByCreatedAtDesc(
    hospitalId: UUID,
    status: String,
  ): List<com.squamed.core.ai.domain.ActionDraft>

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<com.squamed.core.ai.domain.ActionDraft>
}

// 05.07 governance: eval results + incidents + cost aggregation.
interface EvalRunRepository : JpaRepository<com.squamed.core.ai.domain.EvalRun, UUID> {
  fun findByHospitalIdOrderByRecordedAtDesc(hospitalId: UUID): List<com.squamed.core.ai.domain.EvalRun>

  fun findByHospitalIdAndSubjectOrderByRecordedAtDesc(
    hospitalId: UUID,
    subject: String,
  ): List<com.squamed.core.ai.domain.EvalRun>
}

interface AiIncidentRepository : JpaRepository<com.squamed.core.ai.domain.AiIncident, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): com.squamed.core.ai.domain.AiIncident?

  fun findByHospitalIdOrderByReportedAtDesc(hospitalId: UUID): List<com.squamed.core.ai.domain.AiIncident>

  fun findByHospitalIdAndStatusOrderByReportedAtDesc(
    hospitalId: UUID,
    status: String,
  ): List<com.squamed.core.ai.domain.AiIncident>
}
