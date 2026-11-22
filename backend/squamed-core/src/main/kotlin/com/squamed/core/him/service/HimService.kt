package com.squamed.core.him.service

import com.squamed.core.billing.repository.ChargeRepository
import com.squamed.core.encounter.repository.DiagnosisRepository
import com.squamed.core.encounter.repository.EncounterRepository
import com.squamed.core.him.api.CoderAnswerDto
import com.squamed.core.him.api.CoderQueryRequestDto
import com.squamed.core.him.api.CoderQueryView
import com.squamed.core.him.api.CodingAssignRequestDto
import com.squamed.core.him.api.CodingAssignmentView
import com.squamed.core.him.api.CodingQueueRowView
import com.squamed.core.him.api.IntegrityFindingView
import com.squamed.core.him.api.IntegrityScanResultView
import com.squamed.core.him.api.TriageDto
import com.squamed.core.him.domain.CoderQuery
import com.squamed.core.him.domain.CodingAssignment
import com.squamed.core.him.domain.IntegrityFinding
import com.squamed.core.him.repository.CoderQueryRepository
import com.squamed.core.him.repository.CodingAssignmentRepository
import com.squamed.core.him.repository.IntegrityFindingRepository
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.service.TaskService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

// 03.05 HIM coding + revenue integrity engine.
@Service
class HimService(
  private val encounterRepository: EncounterRepository,
  private val diagnosisRepository: DiagnosisRepository,
  private val codingAssignmentRepository: CodingAssignmentRepository,
  private val coderQueryRepository: CoderQueryRepository,
  private val integrityFindingRepository: IntegrityFindingRepository,
  private val chargeRepository: ChargeRepository,
  private val taskService: TaskService,
) {
  // -- coding worklist --

  @Transactional(readOnly = true)
  fun codingQueue(hospitalId: UUID?): List<CodingQueueRowView> =
    encounterRepository
      .findByStatus("completed")
      .filter { hospitalId == null || it.hospitalId == hospitalId }
      .sortedByDescending { it.startedAt }
      .map { encounter ->
        val assignment = codingAssignmentRepository.findByEncounterId(encounter.id!!)
        CodingQueueRowView(
          encounterId = encounter.id!!,
          patientId = encounter.patientId,
          doctorId = encounter.doctorId,
          startedAt = encounter.startedAt,
          diagnosisCount = diagnosisRepository.findByEncounterId(encounter.id!!).size,
          assigned = assignment != null,
        )
      }

  @Transactional
  fun assignCoding(
    encounterId: UUID,
    dto: CodingAssignRequestDto,
    principal: SquamedPrincipal,
  ): CodingAssignmentView {
    val encounter = encounterRepository.findById(encounterId).orElseThrow { IllegalArgumentException("ENCOUNTER_NOT_FOUND") }
    if (encounter.status != "completed") throw IllegalArgumentException("ENCOUNTER_NOT_COMPLETED")
    if (dto.icdCodes.isEmpty() || dto.icdCodes.any { it.isBlank() }) throw IllegalArgumentException("CODING_ICD_REQUIRED")
    if (codingAssignmentRepository.findByEncounterId(encounterId) != null) throw IllegalArgumentException("CODING_ALREADY_ASSIGNED")
    val saved =
      codingAssignmentRepository.save(
        CodingAssignment(
          hospitalId = encounter.hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED"),
          encounterId = encounterId,
          icdCodes = dto.icdCodes.joinToString(",") { it.trim() },
          snomedCodes = dto.snomedCodes.joinToString(",").ifEmpty { null },
          coderId = principal.userId,
          codedAt = Instant.now(),
        ),
      )
    return CodingAssignmentView.from(saved)
  }

  @Transactional(readOnly = true)
  fun listAssignments(hospitalId: UUID?): List<CodingAssignmentView> =
    codingAssignmentRepository.findAllForHospital(hospitalId).map { CodingAssignmentView.from(it) }

  @Transactional(readOnly = true)
  fun codingAuditSample(
    hospitalId: UUID?,
    percent: Int,
  ): List<CodingAssignmentView> {
    val assignments = codingAssignmentRepository.findAllForHospital(hospitalId)
    val take = ceil(assignments.size * percent.coerceIn(1, 100) / 100.0).toInt().coerceAtLeast(1)
    return assignments.shuffled().take(take).map { CodingAssignmentView.from(it) }
  }

  // -- coder queries (task engine) --

  @Transactional
  fun raiseQuery(
    dto: CoderQueryRequestDto,
    principal: SquamedPrincipal,
    hospitalId: UUID,
  ): CoderQueryView {
    if (dto.question.isBlank()) throw IllegalArgumentException("CODER_QUERY_REQUIRED")
    val encounter = encounterRepository.findById(dto.encounterId).orElseThrow { IllegalArgumentException("ENCOUNTER_NOT_FOUND") }
    val task =
      taskService.createTask(
        TaskCreateDto(
          hospitalId = hospitalId,
          title = "Coder query for encounter ${dto.encounterId}",
          description = dto.question.trim(),
          priority = "normal",
          relatedType = "ENCOUNTER",
          relatedId = dto.encounterId,
        ),
        principal.userId,
      )
    val saved =
      coderQueryRepository.save(
        CoderQuery(
          hospitalId = hospitalId,
          encounterId = encounter.id!!,
          question = dto.question.trim(),
          taskId = task.id,
          raisedBy = principal.userId,
          raisedAt = Instant.now(),
        ),
      )
    return CoderQueryView.from(saved)
  }

  @Transactional
  fun answerQuery(
    id: UUID,
    dto: CoderAnswerDto,
    principal: SquamedPrincipal,
  ): CoderQueryView {
    val query = coderQueryRepository.findById(id).orElseThrow { IllegalArgumentException("CODER_QUERY_NOT_FOUND") }
    if (query.status != "OPEN") throw IllegalArgumentException("CODER_QUERY_ALREADY_ANSWERED")
    if (dto.answer.isBlank()) throw IllegalArgumentException("CODER_ANSWER_REQUIRED")
    query.status = "ANSWERED"
    query.answer = dto.answer.trim()
    query.answeredBy = principal.userId
    query.answeredAt = Instant.now()
    return CoderQueryView.from(coderQueryRepository.save(query))
  }

  @Transactional(readOnly = true)
  fun listQueries(
    hospitalId: UUID?,
    status: String?,
  ): List<CoderQueryView> = coderQueryRepository.findAllForHospital(hospitalId, status).map { CoderQueryView.from(it) }

  // -- revenue integrity engine --

  @Transactional
  fun scan(
    hospitalId: UUID?,
    unbilledHours: Long,
    principal: SquamedPrincipal,
  ): IntegrityScanResultView {
    val now = Instant.now()
    val charges = chargeRepository.findAllForHospital(hospitalId)
    val posted = charges.filter { it.status == "posted" }
    val byRule = mutableMapOf<String, Int>()
    var created = 0

    fun record(
      rule: String,
      severity: String,
      subjectType: String,
      subjectId: UUID?,
      detail: String,
      dedupeKey: String,
    ) {
      if (integrityFindingRepository.findByDedupeKey(dedupeKey) != null) return
      val finding =
        integrityFindingRepository.save(
          IntegrityFinding(
            hospitalId = hospitalId ?: posted.firstOrNull()?.hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED"),
            ruleCode = rule,
            severity = severity,
            subjectType = subjectType,
            subjectId = subjectId,
            detail = detail,
            dedupeKey = dedupeKey,
            detectedAt = now,
          ),
        )
      taskService.createTask(
        TaskCreateDto(
          hospitalId = finding.hospitalId,
          title = "Integrity: $rule",
          description = detail,
          priority = if (severity == "high") "high" else "normal",
          relatedType = "INTEGRITY",
          relatedId = finding.id,
        ),
        principal.userId,
      )
      byRule[rule] = (byRule[rule] ?: 0) + 1
      created += 1
    }

    // Rule 1: performed but never invoiced past the grace window.
    posted
      .filter { it.postedAt.isBefore(now.minusSeconds(unbilledHours * 3600)) }
      .forEach { charge ->
        record(
          "UNBILLED_CHARGE",
          "high",
          "CHARGE",
          charge.id,
          "Charge ${charge.chargeCode ?: charge.chargeName} posted ${charge.postedAt} is not invoiced",
          "UNBILLED_CHARGE:${charge.id}",
        )
      }

    // Rule 2: identical posted charges for one patient on one day.
    posted
      .filter { it.chargeCode != null }
      .groupBy { listOf(it.patientId, it.chargeCode, it.quantity, it.unitPriceQar).joinToString("|") }
      .filterValues { it.size > 1 }
      .forEach { (key, rows) ->
        val first = rows.minBy { it.postedAt }
        val day = first.postedAt.toString().take(10)
        record(
          "DUPLICATE_CHARGE",
          "high",
          "PATIENT",
          first.patientId,
          "Duplicate charge ${first.chargeCode} x${first.quantity} (${rows.size} rows) on $day",
          "DUPLICATE_CHARGE:$key:$day",
        )
      }

    // Rule 3: completed encounter with no coding assignment.
    val completed = encounterRepository.findByStatus("completed").filter { hospitalId == null || it.hospitalId == hospitalId }
    completed.forEach { encounter ->
      if (codingAssignmentRepository.findByEncounterId(encounter.id!!) == null) {
        record(
          "UNCODED_ENCOUNTER",
          "warning",
          "ENCOUNTER",
          encounter.id,
          "Completed encounter ${encounter.id} has no ICD-10 coding",
          "UNCODED_ENCOUNTER:${encounter.id}",
        )
      }
    }

    return IntegrityScanResultView(
      scannedCharges = posted.size,
      scannedEncounters = completed.size,
      newFindings = created,
      byRule = byRule,
    )
  }

  @Transactional(readOnly = true)
  fun listFindings(
    hospitalId: UUID?,
    status: String?,
  ): List<IntegrityFindingView> = integrityFindingRepository.findAllForHospital(hospitalId, status).map { IntegrityFindingView.from(it) }

  @Transactional
  fun triageFinding(
    id: UUID,
    dto: TriageDto,
    principal: SquamedPrincipal,
  ): IntegrityFindingView {
    val finding = integrityFindingRepository.findById(id).orElseThrow { IllegalArgumentException("FINDING_NOT_FOUND") }
    if (finding.status != "OPEN") throw IllegalArgumentException("FINDING_ALREADY_TRIAGED")
    val status = dto.status.uppercase()
    if (status !in setOf("RESOLVED", "IGNORED")) throw IllegalArgumentException("TRIAGE_STATUS_INVALID")
    val resolution = dto.resolution?.trim()
    if (resolution.isNullOrEmpty()) throw IllegalArgumentException("TRIAGE_RESOLUTION_REQUIRED")
    finding.status = status
    finding.resolution = resolution
    finding.resolvedBy = principal.userId
    finding.resolvedAt = Instant.now()
    return IntegrityFindingView.from(integrityFindingRepository.save(finding))
  }
}
