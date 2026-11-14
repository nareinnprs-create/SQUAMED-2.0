package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.EmpiReview
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmpiReviewRepository : JpaRepository<EmpiReview, UUID> {
  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<EmpiReview>

  fun findByHospitalIdAndStatusOrderByCreatedAtDesc(
    hospitalId: UUID,
    status: String,
  ): List<EmpiReview>

  fun existsByHospitalIdAndTargetPatientIdAndDuplicatePatientIdAndStatus(
    hospitalId: UUID,
    targetPatientId: UUID,
    duplicatePatientId: UUID,
    status: String,
  ): Boolean

  fun findByHospitalIdAndTargetPatientIdAndDuplicatePatientIdAndStatus(
    hospitalId: UUID,
    targetPatientId: UUID,
    duplicatePatientId: UUID,
    status: String,
  ): EmpiReview?
}
