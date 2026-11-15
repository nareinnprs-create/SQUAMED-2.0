package com.squamed.core.task.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
import com.squamed.core.identity.repository.UserRoleRepository
import com.squamed.core.task.api.TaskCreateDto
import com.squamed.core.task.api.TaskLinkDto
import com.squamed.core.task.api.TaskUpdateDto
import com.squamed.core.task.api.TaskView
import com.squamed.core.task.domain.Task
import com.squamed.core.task.domain.TaskLink
import com.squamed.core.task.repository.TaskLinkRepository
import com.squamed.core.task.repository.TaskRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class TaskService(
  private val taskRepository: TaskRepository,
  private val taskLinkRepository: TaskLinkRepository,
  private val eventPublisher: DomainEventPublisher,
  private val configDocumentService: ConfigDocumentService,
  private val userRoleRepository: UserRoleRepository,
  private val meterRegistry: MeterRegistry,
) {
  companion object {
    val OPEN_STATUSES = listOf("pending", "in_progress", "escalated")
    private val CLOSED_STATUSES = listOf("completed", "cancelled")
    private val VALID_STATUSES = setOf("pending", "in_progress", "completed", "cancelled")

    /** Fallback when a hospital has no task-sla config document (hours per priority). */
    private val DEFAULT_SLA_HOURS: Map<String, Double> =
      mapOf("critical" to 1.0, "urgent" to 4.0, "high" to 12.0, "normal" to 24.0, "low" to 48.0)
    private const val DEFAULT_FALLBACK_HOURS = 24.0

    /** Actor stamp for cron-driven escalations (updatedBy on the task row). */
    private val SYSTEM_ACTOR: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
  }

  @Transactional
  fun createTask(
    dto: TaskCreateDto,
    creatorId: UUID,
  ): TaskView {
    val task =
      taskRepository
        .save(
          Task(
            hospitalId = dto.hospitalId,
            title = dto.title ?: throw IllegalArgumentException("TASK_TITLE_REQUIRED"),
            description = dto.description,
            assigneeId = dto.assigneeId,
            assignerId = dto.assignerId ?: creatorId,
            status = "pending",
            priority = dto.priority ?: "normal",
            dueAt = dto.dueAt,
            relatedType = dto.relatedType,
            relatedId = dto.relatedId,
          ),
        ).also { it.createdBy = creatorId }
    val taskId = task.id ?: throw IllegalStateException("TASK_ID_MISSING")
    replaceLinks(taskId, task.hospitalId, dto.links, creatorId)
    publish("task.created", task, mapOf("title" to task.title))
    return view(taskId)
  }

  @Transactional(readOnly = true)
  fun listTasks(
    assigneeId: UUID?,
    status: String?,
    hospitalId: UUID?,
  ): List<TaskView> {
    val tasks =
      when {
        assigneeId != null -> taskRepository.findByAssigneeId(assigneeId)
        status != null -> taskRepository.findByStatus(status)
        else -> taskRepository.findAll()
      }
    // ADR-13 stage 1: hospital users only see their own hospital's tasks (admins see all).
    val scoped = if (hospitalId == null) tasks else tasks.filter { it.hospitalId == hospitalId }
    return scoped
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { TaskView.from(it, taskLinkRepository.findByTaskId(it.id!!)) }
  }

  @Transactional(readOnly = true)
  fun getTask(
    id: UUID,
    hospitalId: UUID?,
  ): TaskView {
    val task = resolveTask(id, hospitalId)
    return TaskView.from(task, taskLinkRepository.findByTaskId(id))
  }

  /** Reverse subject lookup: open work attached to a patient/invoice/department/encounter. */
  @Transactional(readOnly = true)
  fun tasksForSubject(
    subjectType: String,
    subjectId: UUID,
    hospitalId: UUID?,
  ): List<TaskView> =
    taskLinkRepository
      .findBySubject(subjectType, subjectId, hospitalId)
      .mapNotNull { taskRepository.findById(it.taskId).orElse(null) }
      .map { TaskView.from(it, taskLinkRepository.findByTaskId(it.id!!)) }

  @Transactional
  fun updateTask(
    id: UUID,
    dto: TaskUpdateDto,
    updaterId: UUID,
    hospitalId: UUID?,
  ): TaskView {
    val task = resolveTask(id, hospitalId)
    task.title = dto.title ?: task.title
    task.description = dto.description ?: task.description
    task.assigneeId = dto.assigneeId ?: task.assigneeId
    task.priority = dto.priority ?: task.priority
    task.dueAt = dto.dueAt ?: task.dueAt
    task.relatedType = dto.relatedType ?: task.relatedType
    task.relatedId = dto.relatedId ?: task.relatedId
    task.updatedBy = updaterId
    taskRepository.save(task)
    if (dto.links != null) {
      replaceLinks(id, task.hospitalId, dto.links, updaterId)
    }
    return view(id)
  }

  @Transactional
  fun setStatus(
    id: UUID,
    newStatus: String,
    updaterId: UUID,
    hospitalId: UUID?,
  ): TaskView {
    if (newStatus !in VALID_STATUSES) {
      throw IllegalArgumentException("TASK_INVALID_STATUS")
    }
    val task = resolveTask(id, hospitalId)
    // Terminal-state guard (CS-08): a reordered/duplicated offline flush must not regress a
    // completed task (late in_progress arriving after completed would null completedAt).
    if (task.status == "completed" && newStatus != "completed") {
      throw IllegalStateException("TASK_COMPLETED_TERMINAL")
    }
    val wasCompleted = task.status == "completed"
    task.status = newStatus
    task.updatedBy = updaterId
    if (newStatus == "completed") {
      task.completedAt = Instant.now()
    } else if (wasCompleted) {
      task.completedAt = null
    }
    taskRepository.save(task)
    if (newStatus == "completed") {
      publish("task.completed", task, mapOf("title" to task.title))
    }
    return view(id)
  }

  @Transactional
  fun assignTask(
    id: UUID,
    assigneeId: UUID,
    assignerId: UUID,
    hospitalId: UUID?,
  ): TaskView {
    val task = resolveTask(id, hospitalId)
    task.assigneeId = assigneeId
    task.assignerId = assignerId
    task.updatedBy = assignerId
    taskRepository.save(task)
    publish("task.assigned", task, mapOf("title" to task.title, "assigneeId" to assigneeId.toString()))
    return view(id)
  }

  /** Chunk lifecycle `escalate`: manual escalation (SLA scan escalates through the same method). */
  @Transactional
  fun escalateTask(
    id: UUID,
    actorId: UUID,
    reason: String?,
    hospitalId: UUID?,
    extraRecipientIds: List<UUID> = emptyList(),
  ): TaskView {
    val task = resolveTask(id, hospitalId)
    if (task.status in CLOSED_STATUSES) {
      throw IllegalArgumentException("TASK_ALREADY_CLOSED")
    }
    task.status = "escalated"
    task.escalatedAt = Instant.now()
    task.escalationCount += 1
    task.updatedBy = actorId
    taskRepository.save(task)
    publish(
      "task.escalated",
      task,
      mapOf(
        "title" to task.title,
        "reason" to (reason ?: "MANUAL"),
        "escalationCount" to task.escalationCount,
      ),
      extraRecipientIds,
    )
    return view(id)
  }

  /** Monitor visibility: open tasks whose due date has passed. */
  @Transactional(readOnly = true)
  fun overdueTasks(hospitalId: UUID?): List<TaskView> {
    val now = Instant.now()
    return taskRepository
      .findByStatusNotIn(CLOSED_STATUSES)
      .filter { it.dueAt != null && it.dueAt!!.isBefore(now) }
      .filter { hospitalId == null || it.hospitalId == hospitalId }
      .map { TaskView.from(it, taskLinkRepository.findByTaskId(it.id!!)) }
  }

  /** Scheduled monitor pass (item 9); `POST /tasks/sla/scan` runs the same scan on demand. */
  @Scheduled(cron = "\${squamed.tasks.sla-cron:0 */15 * * * *}")
  @Transactional
  fun slaScanJob() {
    scanAndEscalate(SYSTEM_ACTOR)
  }

  /** Scan pass, config-driven (item 9 "SLA + escalation rules from config"): one pass over all
   * open tasks, escalating every SLA breach. Breach rule: dueAt passed, or — when the task has
   * no dueAt — created at/before now minus the priority's configured SLA hours. */
  @Transactional
  fun scanAndEscalate(actorId: UUID): Map<String, Any?> {
    val now = Instant.now()
    val open = taskRepository.findByStatusNotIn(CLOSED_STATUSES)
    val rulesCache = HashMap<UUID?, SlaRules>()
    var escalated = 0
    for (task in open) {
      if (task.status == "escalated") continue // already escalated; next breach waits for completion
      if (task.hospitalId != null && !rulesCache.containsKey(task.hospitalId)) {
        rulesCache[task.hospitalId] =
          configDocumentService.latest(task.hospitalId!!, "task-sla")?.let { SlaRules.of(it.content) } ?: SlaRules.DEFAULT
      }
      val rules = rulesCache[task.hospitalId] ?: SlaRules.DEFAULT
      val sla = rules.ruleFor(task.priority)
      val breached =
        if (task.dueAt != null) {
          task.dueAt!!.isBefore(now)
        } else {
          val createdAt = task.createdAt ?: now
          !createdAt.plus(Duration.ofMillis((sla.hours * 3600_000.0).toLong())).isAfter(now)
        }
      if (!breached) continue
      val roleTargets =
        if (task.hospitalId != null && sla.escalateToRole != null) {
          userRoleRepository.findUserIdsByHospitalIdAndRoleName(task.hospitalId!!, sla.escalateToRole!!)
        } else {
          emptyList()
        }
      escalateTask(task.id!!, actorId, "SLA_BREACH:${task.priority}", task.hospitalId, roleTargets)
      meterRegistry
        .counter("squamed.sla.breaches", "priority", task.priority)
        .increment()
      escalated += 1
    }
    return mapOf("escalated" to escalated, "checked" to open.size)
  }

  private fun resolveTask(
    id: UUID,
    hospitalId: UUID?,
  ): Task =
    if (hospitalId == null) {
      taskRepository.findById(id).orElseThrow { IllegalArgumentException("TASK_NOT_FOUND") }
    } else {
      taskRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("TASK_NOT_FOUND")
    }

  private fun view(id: UUID): TaskView {
    val task = taskRepository.findById(id).orElseThrow { IllegalStateException("TASK_ID_MISSING") }
    return TaskView.from(task, taskLinkRepository.findByTaskId(id))
  }

  /** Chunk item 9: replace the subject attachment set (patient/encounter/invoice/department). */
  private fun replaceLinks(
    taskId: UUID,
    hospitalId: UUID?,
    links: List<TaskLinkDto>?,
    actorId: UUID,
  ) {
    if (links == null) return
    taskLinkRepository.deleteByTaskId(taskId)
    val deduped =
      links
        .filter { it.subjectType != null && it.subjectId != null }
        .distinctBy { it.subjectType to it.subjectId }
    for (link in deduped) {
      val type = link.subjectType!!.lowercase()
      require(type in TaskLink.SUBJECT_TYPES) { "TASK_INVALID_SUBJECT_TYPE: $type" }
      taskLinkRepository.save(
        TaskLink(
          hospitalId = hospitalId,
          taskId = taskId,
          subjectType = type,
          subjectId = link.subjectId!!,
        ),
      )
    }
  }

  private fun publish(
    eventType: String,
    task: Task,
    payload: Map<String, Any?>,
    extraRecipientIds: List<UUID> = emptyList(),
  ) {
    val recipients = (listOfNotNull(task.assigneeId, task.assignerId) + extraRecipientIds).distinct()
    if (recipients.isEmpty()) return
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "task-service",
        entityType = "task",
        entityId = task.id ?: throw IllegalStateException("TASK_ID_MISSING"),
        hospitalId = task.hospitalId,
        payload = payload,
        recipientIds = recipients,
      ),
    )
  }

  /** One priority rule from the `task-sla` config document. */
  data class SlaRule(
    val hours: Double,
    val escalateToRole: String?,
  )

  class SlaRules(
    private val byPriority: Map<String, SlaRule>,
    private val defaultHours: Double,
  ) {
    fun ruleFor(priority: String): SlaRule = byPriority[priority.lowercase()] ?: SlaRule(defaultHours, null)

    companion object {
      val DEFAULT = SlaRules(mapOf(), DEFAULT_FALLBACK_HOURS)

      fun of(content: JsonNode): SlaRules {
        val byPriority = HashMap<String, SlaRule>()
        val rulesNode = content.get("rules")
        if (rulesNode != null && rulesNode.isArray) {
          for (rule in rulesNode) {
            val priority = rule.get("priority")?.asText() ?: continue
            val hoursNode = rule.get("slaHours")
            if (hoursNode == null || !hoursNode.isNumber) continue
            byPriority[priority.lowercase()] =
              SlaRule(
                hours = hoursNode.asDouble(),
                escalateToRole = rule.get("escalateToRole")?.asText(),
              )
          }
        }
        val defaultNode = content.get("defaultSlaHours")
        return SlaRules(byPriority, if (defaultNode?.isNumber == true) defaultNode.asDouble() else DEFAULT_FALLBACK_HOURS)
      }
    }
  }
}
