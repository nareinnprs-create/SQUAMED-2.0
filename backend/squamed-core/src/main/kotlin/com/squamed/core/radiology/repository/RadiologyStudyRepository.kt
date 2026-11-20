package com.squamed.core.radiology.repository

import com.squamed.core.radiology.domain.RadiologyStudy
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RadiologyStudyRepository : JpaRepository<RadiologyStudy, UUID> {
  fun findByOrderId(orderId: UUID): RadiologyStudy?
}
