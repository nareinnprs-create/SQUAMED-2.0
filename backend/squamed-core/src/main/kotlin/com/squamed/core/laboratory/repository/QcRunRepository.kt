package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.QcRun
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface QcRunRepository : JpaRepository<QcRun, UUID> {
  // Most recent runs for a test on this hospital (null-safe tenant scope, westgard history).
  @Query("select q from QcRun q where (:hid is null or q.hospitalId = :hid) and q.testCode = :testCode order by q.runAt desc")
  fun findRecentForTest(
    @Param("hid") hospitalId: UUID?,
    @Param("testCode") testCode: String,
    pageable: Pageable,
  ): List<QcRun>

  @Query("select q from QcRun q where (:hid is null or q.hospitalId = :hid) order by q.runAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<QcRun>
}
