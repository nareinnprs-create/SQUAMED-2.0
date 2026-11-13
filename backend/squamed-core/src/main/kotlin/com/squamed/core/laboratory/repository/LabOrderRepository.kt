package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.LabOrder
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface LabOrderRepository : JpaRepository<LabOrder, UUID> {
  fun findByPatientId(patientId: UUID): List<LabOrder>

  fun findByStatus(status: String): List<LabOrder>

  fun findByResultStatus(resultStatus: String): List<LabOrder>

  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): Optional<LabOrder>

  fun findByOrderNumber(orderNumber: String): Optional<LabOrder>

  fun findByPatientIdOrderByOrderedAtDesc(patientId: UUID): List<LabOrder>

  fun findByStatusIn(statuses: Collection<String>): List<LabOrder>

  fun findTopByOrderByOrderNumberDesc(): LabOrder?
}
