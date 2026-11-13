package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.ServicePoint
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ServicePointRepository : JpaRepository<ServicePoint, UUID> {
  fun findByQueueType(queueType: String): List<ServicePoint>

  fun findByStatus(status: String): List<ServicePoint>
}
