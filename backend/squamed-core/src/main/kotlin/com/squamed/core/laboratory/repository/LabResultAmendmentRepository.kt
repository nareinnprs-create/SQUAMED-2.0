package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabResultAmendment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LabResultAmendmentRepository : JpaRepository<LabResultAmendment, UUID> {
  fun findByLabOrderIdOrderByAmendedAtAsc(labOrderId: UUID): List<LabResultAmendment>

  fun findByLabTestRequestIdOrderByAmendedAtDesc(labTestRequestId: UUID): List<LabResultAmendment>
}
