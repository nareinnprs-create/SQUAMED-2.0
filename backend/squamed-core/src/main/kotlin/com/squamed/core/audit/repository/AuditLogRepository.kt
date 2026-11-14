package com.squamed.core.audit.repository

import com.squamed.core.audit.domain.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
  fun findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
    entityType: String,
    entityId: String,
  ): List<AuditLog>

  fun findByActorUserIdOrderByOccurredAtDesc(actorUserId: UUID): List<AuditLog>

  fun findAllByOrderByOccurredAtDesc(): List<AuditLog>

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
