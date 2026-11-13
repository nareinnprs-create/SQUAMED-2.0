package com.squamed.core.scribe.repository

import com.squamed.core.scribe.domain.ScribeSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ScribeSessionRepository : JpaRepository<ScribeSession, UUID> {
  fun findAllByOrderByCreatedAtDesc(): List<ScribeSession>
}
