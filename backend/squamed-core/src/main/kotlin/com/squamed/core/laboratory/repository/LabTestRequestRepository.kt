package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabTestRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface LabTestRequestRepository : JpaRepository<LabTestRequest, UUID> {
  fun findByLabOrderId(labOrderId: UUID): List<LabTestRequest>

  fun findByLabOrderIdIn(labOrderIds: Collection<UUID>): List<LabTestRequest>

  // Delta check: most recent prior numeric result for the same patient + test code on a different order.
  @Query(
    "select r from LabTestRequest r join LabOrder o on r.labOrderId = o.id " +
      "where o.patientId = :patientId and r.testCode = :testCode and r.labOrderId <> :excludeOrderId " +
      "and r.resultValue is not null and r.resultEnteredAt is not null " +
      "order by r.resultEnteredAt desc",
  )
  fun findPriorResults(
    @Param("patientId") patientId: UUID,
    @Param("testCode") testCode: String,
    @Param("excludeOrderId") excludeOrderId: UUID,
    pageable: Pageable,
  ): List<LabTestRequest>

  // 02.07 renal flag: released numeric results for a patient + test code (eGFR), newest first.
  @Query(
    "select r from LabTestRequest r join LabOrder o on r.labOrderId = o.id " +
      "where o.patientId = :patientId and o.resultStatus = 'released' " +
      "and lower(r.testCode) = lower(:testCode) and r.resultValue is not null " +
      "order by r.resultEnteredAt desc",
  )
  fun findReleasedResults(
    @Param("patientId") patientId: UUID,
    @Param("testCode") testCode: String,
  ): List<LabTestRequest>
}
