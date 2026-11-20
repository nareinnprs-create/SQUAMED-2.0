package com.squamed.core.radiology.repository

import com.squamed.core.radiology.domain.RadiologyReport
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RadiologyReportRepository : JpaRepository<RadiologyReport, UUID> {
  fun findByOrderIdOrderByReportVersionDesc(orderId: UUID): List<RadiologyReport>
}
