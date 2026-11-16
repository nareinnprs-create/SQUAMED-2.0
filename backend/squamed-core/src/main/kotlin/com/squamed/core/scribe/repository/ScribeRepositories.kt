package com.squamed.core.scribe.repository

import com.squamed.core.scribe.domain.ScribeSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ScribeSessionRepository : JpaRepository<ScribeSession, UUID> {
  // 00.04 tenant sweep: hid = caller's bound hospitalId (null = admin = unfiltered).
  @Query(
    """
    select s from ScribeSession s
    where (:hid is null or s.hospitalId = :hid)
    order by s.createdAt desc
    """,
  )
  fun findAllByOrderByCreatedAtDesc(
    @Param("hid") hid: UUID?,
  ): List<ScribeSession>
}
