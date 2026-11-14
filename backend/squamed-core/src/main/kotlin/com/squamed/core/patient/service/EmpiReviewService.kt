package com.squamed.core.patient.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.patient.api.EmpiReviewDto
import com.squamed.core.patient.api.MatchCandidateDto
import com.squamed.core.patient.api.MergeLogDto
import com.squamed.core.patient.domain.EmpiReview
import com.squamed.core.patient.repository.EmpiReviewRepository
import com.squamed.core.patient.repository.PatientMergeLogRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class EmpiReviewService(
  private val empiReviewRepository: EmpiReviewRepository,
  private val patientMergeLogRepository: PatientMergeLogRepository,
  private val patientService: PatientService,
  private val objectMapper: ObjectMapper,
) {
  /** Registration auto-enqueue: every scored match at/above the queue threshold becomes a review row. */
  @Transactional
  fun enqueueFromMatches(
    matches: List<MatchCandidateDto>,
    duplicatePatientId: UUID,
    hospitalId: UUID,
    submitterId: UUID,
  ) {
    for (match in matches) {
      // The newly registered record is the duplicate; the existing record is the merge target.
      val targetPatientId = match.patientId
      if (empiReviewRepository.existsByHospitalIdAndTargetPatientIdAndDuplicatePatientIdAndStatus(
          hospitalId,
          targetPatientId,
          duplicatePatientId,
          EmpiReview.STATUS_PENDING,
        )
      ) {
        continue
      }
      empiReviewRepository.save(
        EmpiReview(
          hospitalId = hospitalId,
          targetPatientId = targetPatientId,
          duplicatePatientId = duplicatePatientId,
          score = match.score,
          matchedOn = match.matchedOn.joinToString(","),
          status = EmpiReview.STATUS_PENDING,
          submittedBy = submitterId,
        ),
      )
    }
  }

  /** Manual merge request: POST /patients/merge creates a review intent (two-person rule); it no longer merges directly. */
  @Transactional
  fun submitIntent(
    targetId: UUID,
    duplicateId: UUID,
    hospitalId: UUID,
    submitterId: UUID,
  ): EmpiReviewDto {
    if (targetId == duplicateId) throw IllegalArgumentException("CANNOT_MERGE_PATIENT_WITH_ITSELF")
    empiReviewRepository
      .findByHospitalIdAndTargetPatientIdAndDuplicatePatientIdAndStatus(
        hospitalId,
        targetId,
        duplicateId,
        EmpiReview.STATUS_PENDING,
      )?.let { return EmpiReviewDto.from(it) }
    val review =
      empiReviewRepository.save(
        EmpiReview(
          hospitalId = hospitalId,
          targetPatientId = targetId,
          duplicatePatientId = duplicateId,
          score = 0,
          matchedOn = "MANUAL",
          status = EmpiReview.STATUS_PENDING,
          submittedBy = submitterId,
        ),
      )
    return EmpiReviewDto.from(review)
  }

  /** Two-person rule: approver must hold patient:merge:cosign (endpoint-enforced) and be a distinct user. */
  @Transactional
  fun approve(
    reviewId: UUID,
    approverId: UUID,
    approverHospitalId: UUID?,
  ): EmpiReviewDto {
    val review = findReview(reviewId)
    requireHospital(approverHospitalId, review.hospitalId)
    if (review.status != EmpiReview.STATUS_PENDING) throw IllegalArgumentException("REVIEW_NOT_PENDING")
    if (approverId == review.submittedBy) throw IllegalArgumentException("TWO_PERSON_RULE_SELF_APPROVAL")
    val result = patientService.mergePatient(review.targetPatientId, review.duplicatePatientId, approverId)
    review.status = EmpiReview.STATUS_APPROVED
    review.decidedBy = approverId
    review.decidedAt = Instant.now()
    review.mergeId = result.mergeId
    return EmpiReviewDto.from(empiReviewRepository.save(review))
  }

  @Transactional
  fun reject(
    reviewId: UUID,
    deciderId: UUID,
    deciderHospitalId: UUID?,
  ): EmpiReviewDto {
    val review = findReview(reviewId)
    requireHospital(deciderHospitalId, review.hospitalId)
    if (review.status != EmpiReview.STATUS_PENDING) throw IllegalArgumentException("REVIEW_NOT_PENDING")
    review.status = EmpiReview.STATUS_REJECTED
    review.decidedBy = deciderId
    review.decidedAt = Instant.now()
    return EmpiReviewDto.from(empiReviewRepository.save(review))
  }

  @Transactional(readOnly = true)
  fun listReviews(
    hospitalId: UUID,
    status: String?,
  ): List<EmpiReviewDto> =
    (
      if (status == null) {
        empiReviewRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
      } else {
        empiReviewRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status)
      }
    ).map { EmpiReviewDto.from(it) }

  @Transactional
  fun unmerge(
    mergeId: UUID,
    actorId: UUID,
    actorHospitalId: UUID?,
  ): MergeLogDto {
    val log =
      patientMergeLogRepository.findById(mergeId).orElseThrow { IllegalArgumentException("MERGE_NOT_FOUND") }
    requireHospital(actorHospitalId, log.hospitalId)
    return patientService.unmergePatient(mergeId, actorId)
  }

  @Transactional(readOnly = true)
  fun listMerges(hospitalId: UUID): List<MergeLogDto> =
    patientMergeLogRepository
      .findByHospitalIdOrderByMergedAtDesc(hospitalId)
      .map { MergeLogDto.from(it, parseSnapshot(it.snapshot)) }

  private fun parseSnapshot(snapshot: String): Map<String, List<String>> =
    objectMapper.readValue(snapshot, object : TypeReference<Map<String, List<String>>>() {})

  private fun findReview(id: UUID): EmpiReview = empiReviewRepository.findById(id).orElseThrow { IllegalArgumentException("REVIEW_NOT_FOUND") }

  private fun requireHospital(
    actorHospitalId: UUID?,
    rowHospitalId: UUID,
  ) {
    if (actorHospitalId != null && actorHospitalId != rowHospitalId) {
      throw AccessDeniedException("TENANT_FORBIDDEN")
    }
  }
}
