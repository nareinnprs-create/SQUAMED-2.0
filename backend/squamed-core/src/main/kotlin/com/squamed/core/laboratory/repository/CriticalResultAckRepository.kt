package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.CriticalResultAck
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CriticalResultAckRepository : JpaRepository<CriticalResultAck, UUID> {
  fun findByCriticalResultIdOrderByOccurredAtAsc(criticalResultId: UUID): List<CriticalResultAck>
}
