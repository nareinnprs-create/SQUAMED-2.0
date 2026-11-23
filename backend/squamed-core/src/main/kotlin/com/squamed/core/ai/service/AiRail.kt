package com.squamed.core.ai.service

import com.squamed.core.ai.domain.AttentionItem
import com.squamed.core.ai.repository.ActionDraftRepository
import com.squamed.core.ai.repository.AttentionItemRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.surgery.repository.SurgeryScheduleRepository
import com.squamed.core.task.repository.TaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AttentionItemView(
  val id: UUID?,
  val itemType: String,
  val severity: String,
  val title: String,
  val detail: String?,
  val sourceType: String,
  val sourceId: UUID?,
  val status: String,
  val raisedAt: Instant,
  val clearedAt: Instant?,
  val dismissalReason: String?,
) {
  companion object {
    fun from(entity: AttentionItem) =
      AttentionItemView(
        entity.id,
        entity.itemType,
        entity.severity,
        entity.title,
        entity.detail,
        entity.sourceType,
        entity.sourceId,
        entity.status,
        entity.raisedAt,
        entity.clearedAt,
        entity.dismissalReason,
      )
  }
}

private data class RailCandidate(
  val itemType: String,
  val severity: String,
  val title: String,
  val detail: String?,
  val dedupeKey: String,
  val sourceType: String,
  val sourceId: UUID?,
)

// 05.04: the rail assembles attention items from governed platform state. It never mutates the
// source records; acting on an item goes through the source workflow.
@Service
class AiRailService(
  private val itemRepository: AttentionItemRepository,
  private val admissionRepository: AdmissionRepository,
  private val surgeryScheduleRepository: SurgeryScheduleRepository,
  private val taskRepository: TaskRepository,
  private val draftRepository: ActionDraftRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  companion object {
    private const val MAX_ITEMS = 20
    private val SEVERITY_RANK = mapOf("critical" to 0, "high" to 1, "medium" to 2, "low" to 3)
  }

  @Transactional
  fun scan(hospitalId: UUID): List<AttentionItemView> {
    val now = Instant.now()
    val candidates = mutableListOf<RailCandidate>()

    admissionRepository
      .findAllByHospital(hospitalId)
      .filter { it.status == "admitted" && it.expectedDischargeAt != null && it.expectedDischargeAt!!.isBefore(now) }
      .forEach {
        candidates +=
          RailCandidate(
            itemType = "DISCHARGE_BLOCKER",
            severity = "high",
            title = "Discharge overdue for admission ${it.id}",
            detail = "Expected discharge ${it.expectedDischargeAt}",
            dedupeKey = "discharge:${it.id}",
            sourceType = "admission",
            sourceId = it.id,
          )
      }

    surgeryScheduleRepository
      .findAllForHospital(hospitalId)
      .filter { it.status in setOf("scheduled", "confirmed") && it.scheduledStartAt.isBefore(now) }
      .forEach {
        candidates +=
          RailCandidate(
            itemType = "OT_DELAY",
            severity = "high",
            title = "OT case delayed: ${it.procedureName}",
            detail = "Scheduled ${it.scheduledStartAt}, status ${it.status}",
            dedupeKey = "ot:${it.id}",
            sourceType = "surgery_schedule",
            sourceId = it.id,
          )
      }

    draftRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, "PROPOSED").forEach {
      candidates +=
        RailCandidate(
          itemType = "PENDING_AI_APPROVAL",
          severity = "medium",
          title = "AI draft awaiting approval: ${it.summary}",
          detail = "Expires ${it.expiresAt}",
          dedupeKey = "draft:${it.id}",
          sourceType = "ai_action_draft",
          sourceId = it.id,
        )
    }

    taskRepository
      .findByStatusNotIn(listOf("completed", "cancelled"))
      .filter { (it.hospitalId == null || it.hospitalId == hospitalId) && it.dueAt != null && it.dueAt!!.isBefore(now) }
      .forEach {
        candidates +=
          RailCandidate(
            itemType = "TASK_OVERDUE",
            severity = "medium",
            title = "Task overdue: ${it.title ?: "untitled"}",
            detail = "Due ${it.dueAt}",
            dedupeKey = "task:${it.id}",
            sourceType = "task",
            sourceId = it.id,
          )
      }

    val keys = candidates.map { it.dedupeKey }.toSet()
    candidates.forEach { candidate ->
      val existing = itemRepository.findByHospitalIdAndDedupeKey(hospitalId, candidate.dedupeKey)
      if (existing == null) {
        val saved =
          itemRepository.save(
            AttentionItem(
              hospitalId = hospitalId,
              itemType = candidate.itemType,
              severity = candidate.severity,
              title = candidate.title,
              detail = candidate.detail,
              dedupeKey = candidate.dedupeKey,
              sourceType = candidate.sourceType,
              sourceId = candidate.sourceId,
            ),
          )
        publish(saved, "ai.attention_item_raised")
      } else if (existing.status == "OPEN") {
        existing.title = candidate.title
        existing.detail = candidate.detail
        existing.severity = candidate.severity
        itemRepository.save(existing)
      }
      // A dismissed/cleared key stays cleared: the rail must not nag about the same condition.
    }

    itemRepository
      .findByHospitalIdAndStatusOrderByRaisedAtDesc(hospitalId, "OPEN")
      .filter { it.dedupeKey !in keys }
      .forEach {
        it.status = "CLEARED"
        it.clearedAt = now
        it.dismissalReason = "condition resolved"
        itemRepository.save(it)
        publish(it, "ai.attention_item_cleared")
      }

    return itemRepository
      .findByHospitalIdAndStatusOrderByRaisedAtDesc(hospitalId, "OPEN")
      .sortedWith(compareBy({ SEVERITY_RANK[it.severity] ?: 9 }, { -it.raisedAt.toEpochMilli() }))
      .take(MAX_ITEMS)
      .map(AttentionItemView::from)
  }

  @Transactional
  fun dismiss(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
    reason: String?,
  ): AttentionItemView {
    val item = itemRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("AI_ATTENTION_NOT_FOUND")
    if (item.status != "OPEN") throw IllegalStateException("AI_ATTENTION_NOT_OPEN")
    item.status = "CLEARED"
    item.clearedAt = Instant.now()
    item.dismissalReason = reason?.trim()?.take(300) ?: "dismissed"
    item.updatedBy = actorId
    val saved = itemRepository.save(item)
    publish(saved, "ai.attention_item_cleared")
    return AttentionItemView.from(saved)
  }

  private fun publish(
    item: AttentionItem,
    eventType: String,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "ai-service",
        entityType = "ai_attention_item",
        entityId = item.id,
        hospitalId = item.hospitalId,
        payload = mapOf("itemType" to item.itemType, "severity" to item.severity, "sourceId" to item.sourceId?.toString()),
      ),
    )
  }
}
