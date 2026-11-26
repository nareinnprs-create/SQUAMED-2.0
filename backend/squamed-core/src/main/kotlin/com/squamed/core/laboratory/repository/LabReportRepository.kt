package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LabReportRepository : JpaRepository<LabReport, UUID> {
  fun findTopByLabOrderIdOrderByReportVersionDesc(labOrderId: UUID): LabReport?

  fun findAllByLabOrderIdOrderByReportVersionDesc(labOrderId: UUID): List<LabReport>

  // 00.04 tenant sweep: null-safe hospital scope (U8) - admin callers pass null.
  @Query("select r from LabReport r where (:hid is null or r.hospitalId = :hid)")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<LabReport>

  // 07.06 kiosk report collection: released reports for one patient.
  @Query(
    "select r from LabReport r, com.squamed.core.laboratory.domain.LabOrder o " +
      "where o.id = r.labOrderId and o.patientId = :pid and r.hospitalId = :hid order by r.publishedAt desc",
  )
  fun findForPatient(
    @Param("hid") hospitalId: UUID,
    @Param("pid") patientId: UUID,
  ): List<LabReport>
}
