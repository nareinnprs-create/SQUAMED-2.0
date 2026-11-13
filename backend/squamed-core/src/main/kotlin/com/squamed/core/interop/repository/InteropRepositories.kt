package com.squamed.core.interop.repository

import com.squamed.core.interop.domain.Hl7MessageLog
import com.squamed.core.interop.domain.IntegrationConnection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IntegrationConnectionRepository : JpaRepository<IntegrationConnection, UUID> {
  fun findAllByOrderByCreatedAtAsc(): List<IntegrationConnection>
}

interface Hl7MessageLogRepository : JpaRepository<Hl7MessageLog, UUID> {
  fun findAllByOrderByCreatedAtDesc(): List<Hl7MessageLog>
}
