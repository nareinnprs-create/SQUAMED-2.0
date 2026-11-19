package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.SpecimenCustody
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpecimenCustodyRepository : JpaRepository<SpecimenCustody, UUID> {
  fun findByLabOrderIdOrderByOccurredAtAsc(labOrderId: UUID): List<SpecimenCustody>
}
