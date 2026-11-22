package com.squamed.core.him.api

import com.squamed.core.him.domain.CoderQuery
import com.squamed.core.him.domain.CodingAssignment
import com.squamed.core.him.domain.IntegrityFinding
import java.time.Instant
import java.util.UUID

data class CodingQueueRowView(
  val encounterId: UUID,
  val patientId: UUID,
  val doctorId: UUID?,
  val startedAt: Instant?,
  val diagnosisCount: Int,
  val assigned: Boolean,
)

data class CodingAssignRequestDto(
  val icdCodes: List<String> = emptyList(),
  val snomedCodes: List<String> = emptyList(),
)

data class CodingAssignmentView(
  val id: UUID?,
  val hospitalId: UUID,
  val encounterId: UUID,
  val icdCodes: List<String>,
  val snomedCodes: List<String>,
  val coderId: UUID?,
  val codedAt: Instant,
) {
  companion object {
    fun from(entity: CodingAssignment) =
      CodingAssignmentView(
        entity.id,
        entity.hospitalId,
        entity.encounterId,
        entity.icdCodes
          .split(",")
          .map { it.trim() }
          .filter { it.isNotEmpty() },
        entity.snomedCodes
          ?.split(",")
          ?.map { it.trim() }
          ?.filter { it.isNotEmpty() } ?: emptyList(),
        entity.coderId,
        entity.codedAt,
      )
  }
}

data class CoderQueryRequestDto(
  val encounterId: UUID,
  val question: String,
)

data class CoderAnswerDto(
  val answer: String,
)

data class CoderQueryView(
  val id: UUID?,
  val hospitalId: UUID,
  val encounterId: UUID,
  val question: String,
  val status: String,
  val taskId: UUID?,
  val raisedBy: UUID?,
  val raisedAt: Instant,
  val answer: String?,
  val answeredBy: UUID?,
  val answeredAt: Instant?,
) {
  companion object {
    fun from(entity: CoderQuery) =
      CoderQueryView(
        entity.id,
        entity.hospitalId,
        entity.encounterId,
        entity.question,
        entity.status,
        entity.taskId,
        entity.raisedBy,
        entity.raisedAt,
        entity.answer,
        entity.answeredBy,
        entity.answeredAt,
      )
  }
}

data class IntegrityScanResultView(
  val scannedCharges: Int,
  val scannedEncounters: Int,
  val newFindings: Int,
  val byRule: Map<String, Int>,
)

data class IntegrityFindingView(
  val id: UUID?,
  val hospitalId: UUID,
  val ruleCode: String,
  val severity: String,
  val subjectType: String,
  val subjectId: UUID?,
  val detail: String?,
  val status: String,
  val detectedAt: Instant,
  val resolvedBy: UUID?,
  val resolvedAt: Instant?,
  val resolution: String?,
) {
  companion object {
    fun from(entity: IntegrityFinding) =
      IntegrityFindingView(
        entity.id,
        entity.hospitalId,
        entity.ruleCode,
        entity.severity,
        entity.subjectType,
        entity.subjectId,
        entity.detail,
        entity.status,
        entity.detectedAt,
        entity.resolvedBy,
        entity.resolvedAt,
        entity.resolution,
      )
  }
}

data class TriageDto(
  val status: String,
  val resolution: String? = null,
)
