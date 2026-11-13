package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabTestRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LabTestRequestRepository : JpaRepository<LabTestRequest, UUID> {
  fun findByLabOrderId(labOrderId: UUID): List<LabTestRequest>

  fun findByLabOrderIdIn(labOrderIds: Collection<UUID>): List<LabTestRequest>
}
