package com.squamed.core.audit.repository

import com.squamed.core.audit.domain.AuditLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
  /**
   * Keyset pagination for /audit/logs (00.02 cursor convention): newest first, optional filters.
   * First page (no cursor). Optional-filter params sit in column-equality predicates so PG can
   * type them — an untyped null bind (`could not determine data type of parameter $N`) breaks
   * queries where the null has no typed occurrence.
   */
  @Query(
    """
    select a from AuditLog a
    where (:entityType is null or a.entityType = :entityType)
      and (:entityId is null or a.entityId = :entityId)
      and (:actor is null or a.actorUserId = :actor)
    order by a.occurredAt desc, a.id desc
    """,
  )
  fun listLogsFirst(
    @Param("entityType") entityType: String?,
    @Param("entityId") entityId: String?,
    @Param("actor") actor: UUID?,
    pageable: Pageable,
  ): List<AuditLog>

  /**
   * Strictly after the opaque (occurredAt, id) cursor: keyset pages never skip or repeat rows
   * even across tenant chains (the pair is a total deterministic order). Cursor params are
   * non-null here — the caller decodes them or rejects the request.
   */
  @Query(
    """
    select a from AuditLog a
    where (:entityType is null or a.entityType = :entityType)
      and (:entityId is null or a.entityId = :entityId)
      and (:actor is null or a.actorUserId = :actor)
      and (a.occurredAt < :cursorAt or (a.occurredAt = :cursorAt and a.id < :cursorId))
    order by a.occurredAt desc, a.id desc
    """,
  )
  fun listLogsAfter(
    @Param("entityType") entityType: String?,
    @Param("entityId") entityId: String?,
    @Param("actor") actor: UUID?,
    @Param("cursorAt") cursorAt: Instant,
    @Param("cursorId") cursorId: UUID,
    pageable: Pageable,
  ): List<AuditLog>

  /** Null-safe tenant filter: hospitalId == null means the system chain (hospital_id IS NULL). */
  @Query(
    """
    select a from AuditLog a
    where a.chainSeq is not null
      and ((:hospitalId is null and a.hospitalId is null) or a.hospitalId = :hospitalId)
    order by a.chainSeq asc
    """,
  )
  fun findChainedByHospital(
    @Param("hospitalId") hospitalId: UUID?,
  ): List<AuditLog>

  /** Strictly after the cursor: export never re-emits the boundary row. */
  @Query(
    """
    select a from AuditLog a
    where a.chainSeq is not null and a.chainSeq > :sinceSeq
      and ((:hospitalId is null and a.hospitalId is null) or a.hospitalId = :hospitalId)
    order by a.chainSeq asc
    """,
  )
  fun findChainedSince(
    @Param("hospitalId") hospitalId: UUID?,
    @Param("sinceSeq") sinceSeq: Long,
  ): List<AuditLog>

  fun countByHospitalIdAndChainSeqIsNull(hospitalId: UUID?): Long
}
