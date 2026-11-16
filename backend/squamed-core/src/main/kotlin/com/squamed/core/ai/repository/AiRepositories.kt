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
}
