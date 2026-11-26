package com.squamed.core.radiology.repository

import com.squamed.core.radiology.domain.RadiologyReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RadiologyReportRepository : JpaRepository<RadiologyReport, UUID> {
  fun findByOrderIdOrderByReportVersionDesc(orderId: UUID): List<RadiologyReport>

  // 07.06 kiosk report collection: signed reports for one patient.
  @Query(
    "select r from RadiologyReport r, com.squamed.core.radiology.domain.RadOrder o " +
      "where o.id = r.orderId and o.patientId = :pid and r.hospitalId = :hid and r.status = 'signed' " +
      "order by r.signedAt desc",
  )
  fun findSignedForPatient(
    @Param("hid") hospitalId: UUID,
    @Param("pid") patientId: UUID,
  ): List<RadiologyReport>
}
