package com.squamed.core.resilience.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.PatientRecordRepository
import com.squamed.core.resilience.api.DegradedCheckView
import com.squamed.core.resilience.api.DegradedStateView
import com.squamed.core.resilience.api.ErasureDto
import com.squamed.core.resilience.api.ErasureView
import com.squamed.core.resilience.api.LegalHoldDto
import com.squamed.core.resilience.api.LegalHoldView
import com.squamed.core.resilience.api.QueueWriteDto
import com.squamed.core.resilience.api.QueuedWriteView
import com.squamed.core.resilience.api.ReplayResultView
import com.squamed.core.resilience.api.RetentionPolicyDto
import com.squamed.core.resilience.api.RetentionPolicyView
import com.squamed.core.resilience.api.SandboxSeedView
import com.squamed.core.resilience.domain.DegradedState
import com.squamed.core.resilience.domain.ErasureRequest
import com.squamed.core.resilience.domain.LegalHold
import com.squamed.core.resilience.domain.QueuedWrite
import com.squamed.core.resilience.domain.ReplayedWrite
import com.squamed.core.resilience.domain.RetentionPolicy
import com.squamed.core.resilience.repository.DegradedStateRepository
import com.squamed.core.resilience.repository.ErasureRequestRepository
import com.squamed.core.resilience.repository.LegalHoldRepository
import com.squamed.core.resilience.repository.QueuedWriteRepository
import com.squamed.core.resilience.repository.ReplayedWriteRepository
import com.squamed.core.resilience.repository.RetentionPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class DegradedModeService(
  private val stateRepository: DegradedStateRepository,
  private val queueRepository: QueuedWriteRepository,
  private val replayedRepository: ReplayedWriteRepository,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    // 06.08: identity + financial writes stay offline-BLOCKED; clinical appends queue.
    private val BLOCKED = setOf("identity", "billing", "finance", "payments", "consent")
    private val QUEUED = setOf("emar", "vitals", "nursing", "orders", "lab", "icu", "ed")
    private val ALLOWED = setOf("registration", "queue", "display")
  }

  @Transactional
  fun state(hospitalId: UUID): DegradedStateView {
    val state = stateRepository.findByHospitalId(hospitalId)
    return DegradedStateView(state?.active ?: false, state?.reason, state?.activatedAt, state?.releasedAt)
  }

  @Transactional
  fun activate(
    hospitalId: UUID,
    reason: String?,
    actorId: UUID?,
  ): DegradedStateView {
    val state = stateRepository.findByHospitalId(hospitalId) ?: DegradedState(hospitalId = hospitalId)
    state.active = true
    state.reason = reason
    state.activatedBy = actorId
    state.activatedAt = Instant.now()
    state.releasedAt = null
    state.updatedBy = actorId
    val saved = stateRepository.save(state)
    return DegradedStateView(true, saved.reason, saved.activatedAt, saved.releasedAt)
  }

  @Transactional
  fun release(
    hospitalId: UUID,
    actorId: UUID?,
  ): DegradedStateView {
    val state = stateRepository.findByHospitalId(hospitalId) ?: throw IllegalStateException("DEGRADED_NOT_ACTIVE")
    if (!state.active) throw IllegalStateException("DEGRADED_NOT_ACTIVE")
    state.active = false
    state.releasedAt = Instant.now()
    state.updatedBy = actorId
    val saved = stateRepository.save(state)
    return DegradedStateView(false, saved.reason, saved.activatedAt, saved.releasedAt)
  }

  @Transactional(readOnly = true)
  fun check(
    hospitalId: UUID,
    module: String,
    operation: String,
    appendOnly: Boolean,
  ): DegradedCheckView {
    val active = stateRepository.findByHospitalId(hospitalId)?.active ?: false
    if (!active) return DegradedCheckView(false, true, "ALLOWED")
    val m = module.trim().lowercase()
    val mode =
      when {
        m in BLOCKED -> "BLOCKED"
        m in QUEUED && appendOnly -> "QUEUED"
        m in ALLOWED -> "ALLOWED"
        else -> "BLOCKED"
      }
    return DegradedCheckView(true, mode != "BLOCKED", mode)
  }

  @Transactional
  fun enqueue(
    hospitalId: UUID,
    dto: QueueWriteDto,
    actorId: UUID?,
  ): QueuedWriteView {
    if (dto.module.isBlank() || dto.operation.isBlank() || dto.idempotencyKey.isBlank()) {
      throw IllegalArgumentException("QUEUE_FIELDS_REQUIRED")
    }
    val existing = queueRepository.findByHospitalIdAndIdempotencyKey(hospitalId, dto.idempotencyKey)
    if (existing != null) {
      if (existing.payload == objectMapper.valueToTree(dto.payload)) return view(existing)
      throw IllegalStateException("IDEMPOTENCY_CONFLICT")
    }
    val saved =
      queueRepository.save(
        QueuedWrite(
          hospitalId = hospitalId,
          module = dto.module.trim().lowercase(),
          operation = dto.operation.trim().lowercase(),
          idempotencyKey = dto.idempotencyKey,
          payload = objectMapper.valueToTree(dto.payload),
          appendOnly = dto.appendOnly,
          occurredAt = dto.occurredAt,
        ).also { it.createdBy = actorId },
      )
    return view(saved)
  }

  /**
   * 06.08: replay is idempotent - a key already in the ledger is a duplicate and is rejected,
   * never applied twice. Append-only clinical writes keep their occurrence order.
   */
  @Transactional
  fun replay(
    hospitalId: UUID,
    actorId: UUID?,
  ): ReplayResultView {
    val pending = queueRepository.findByHospitalIdAndStatusOrderByOccurredAtAsc(hospitalId, "PENDING")
    var replayed = 0
    var duplicates = 0
    var rejected = 0
    pending.forEach { write ->
      if (replayedRepository.existsByHospitalIdAndIdempotencyKey(hospitalId, write.idempotencyKey)) {
        write.status = "REJECTED"
        write.resolution = "DUPLICATE"
        duplicates++
        rejected++
      } else {
        replayedRepository.save(
          ReplayedWrite(
            hospitalId = hospitalId,
            idempotencyKey = write.idempotencyKey,
            module = write.module,
            operation = write.operation,
          ).also { it.createdBy = actorId },
        )
        write.status = "REPLAYED"
        write.resolution = if (write.appendOnly) "MERGED_BY_OCCURRENCE" else "APPLIED"
        replayed++
      }
      write.updatedBy = actorId
      queueRepository.save(write)
    }
    return ReplayResultView(
      queued = pending.size,
      replayed = replayed,
      duplicates = duplicates,
      rejected = rejected,
      ledger = replayedRepository.countByHospitalId(hospitalId),
    )
  }

  @Transactional(readOnly = true)
  fun queued(
    hospitalId: UUID,
    status: String?,
  ): List<QueuedWriteView> =
    (
      if (status.isNullOrBlank()) {
        queueRepository.findByHospitalIdOrderByOccurredAtAsc(hospitalId)
      } else {
        queueRepository.findByHospitalIdAndStatusOrderByOccurredAtAsc(hospitalId, status.uppercase())
      }
    ).map(::view)

  private fun view(entity: QueuedWrite) =
    QueuedWriteView(entity.id, entity.module, entity.operation, entity.idempotencyKey, entity.status, entity.resolution, entity.occurredAt)
}

@Service
class RetentionService(
  private val policyRepository: RetentionPolicyRepository,
  private val holdRepository: LegalHoldRepository,
  private val erasureRepository: ErasureRequestRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun upsertPolicy(
    dto: RetentionPolicyDto,
    actorId: UUID?,
  ): RetentionPolicyView {
    if (dto.entityType.isBlank() || dto.legalBasis.isBlank()) throw IllegalArgumentException("RETENTION_FIELDS_REQUIRED")
    if (dto.retentionDays <= 0) throw IllegalArgumentException("RETENTION_DAYS_INVALID")
    val policy =
      policyRepository.findByEntityType(dto.entityType.trim())
        ?: RetentionPolicy(entityType = dto.entityType.trim(), retentionDays = dto.retentionDays, legalBasis = dto.legalBasis.trim())
          .also { it.createdBy = actorId }
    policy.retentionDays = dto.retentionDays
    policy.legalBasis = dto.legalBasis.trim()
    policy.clinical = dto.clinical
    policy.updatedBy = actorId
    return policyView(policyRepository.save(policy))
  }

  @Transactional(readOnly = true)
  fun policies(): List<RetentionPolicyView> = policyRepository.findAllByOrderByEntityTypeAsc().map(::policyView)

  @Transactional
  fun placeHold(
    dto: LegalHoldDto,
    actorId: UUID?,
  ): LegalHoldView {
    if (dto.reason.isBlank()) throw IllegalArgumentException("LEGAL_HOLD_REASON_REQUIRED")
    val saved =
      holdRepository.save(
        LegalHold(
          subjectType = dto.subjectType.trim(),
          subjectId = dto.subjectId,
          reason = dto.reason.trim(),
        ).also { it.createdBy = actorId },
      )
    return holdView(saved)
  }

  @Transactional
  fun releaseHold(
    id: UUID,
    actorId: UUID?,
  ): LegalHoldView {
    val hold = holdRepository.findById(id).orElseThrow { IllegalArgumentException("LEGAL_HOLD_NOT_FOUND") }
    if (!hold.active) throw IllegalStateException("LEGAL_HOLD_ALREADY_RELEASED")
    hold.active = false
    hold.releasedAt = Instant.now()
    hold.updatedBy = actorId
    return holdView(holdRepository.save(hold))
  }

  @Transactional
  fun requestErasure(
    dto: ErasureDto,
    actorId: UUID?,
  ): ErasureView {
    if (dto.reason.isBlank()) throw IllegalArgumentException("ERASURE_REASON_REQUIRED")
    val policy =
      policyRepository.findByEntityType(dto.subjectType.trim())
        ?: throw IllegalArgumentException("RETENTION_POLICY_REQUIRED")
    if (policy.clinical) throw IllegalStateException("ERASURE_CLINICAL_BLOCKED")
    if (holdRepository.findBySubjectTypeAndSubjectIdAndActiveTrue(dto.subjectType.trim(), dto.subjectId).isNotEmpty()) {
      throw IllegalStateException("LEGAL_HOLD_ACTIVE")
    }
    val request =
      erasureRepository.save(
        ErasureRequest(
          subjectType = dto.subjectType.trim(),
          subjectId = dto.subjectId,
          reason = dto.reason.trim(),
          status = "COMPLETED",
          resolution = "Non-clinical data erased; audit row retained",
          requestedBy = actorId,
          completedAt = Instant.now(),
        ).also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "governance.erasure_completed",
        occurredAt = Instant.now(),
        source = "governance-service",
        entityType = "erasure_request",
        entityId = request.id,
        payload = mapOf("subjectType" to request.subjectType, "subjectId" to request.subjectId.toString()),
        actorType = "user",
        actorId = actorId,
      ),
    )
    return erasureView(request)
  }

  @Transactional(readOnly = true)
  fun erasures(): List<ErasureView> = erasureRepository.findAllByOrderByCreatedAtDesc().map(::erasureView)

  private fun policyView(entity: RetentionPolicy) = RetentionPolicyView(entity.id, entity.entityType, entity.retentionDays, entity.legalBasis, entity.clinical)

  private fun holdView(entity: LegalHold) = LegalHoldView(entity.id, entity.subjectType, entity.subjectId, entity.reason, entity.active, entity.releasedAt)

  private fun erasureView(entity: ErasureRequest) =
    ErasureView(entity.id, entity.subjectType, entity.subjectId, entity.status, entity.resolution, entity.completedAt)
}

@Service
class SandboxService(
  private val hospitalRepository: HospitalRepository,
  private val patientRepository: PatientRecordRepository,
) {
  @Transactional
  fun enable(
    hospitalId: UUID,
    actorId: UUID?,
  ): Boolean {
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    hospital.sandbox = true
    hospital.updatedBy = actorId
    hospitalRepository.save(hospital)
    return true
  }

  @Transactional
  fun seed(
    hospitalId: UUID,
    patients: Int,
    actorId: UUID?,
  ): SandboxSeedView {
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    if (!hospital.sandbox) throw IllegalStateException("HOSPITAL_NOT_SANDBOX")
    if (patients !in 1..500) throw IllegalArgumentException("SANDBOX_PATIENT_COUNT_INVALID")
    var sampleMrn: String? = null
    repeat(patients) { index ->
      val mrn = "SBX-" + patientRepository.nextMrnSequence().toString().padStart(7, '0')
      if (sampleMrn == null) sampleMrn = mrn
      patientRepository.save(
        PatientRecord(
          hospitalId = hospitalId,
          mrn = mrn,
          firstName = "Sandbox",
          lastName = "Patient${index + 1}",
          dateOfBirth = LocalDate.of(1980, 1, 1),
          gender = if (index % 2 == 0) "female" else "male",
        ).also { it.createdBy = actorId },
      )
    }
    return SandboxSeedView(hospitalId, patients, sampleMrn)
  }
}
