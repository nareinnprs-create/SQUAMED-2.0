package com.squamed.core.workflow.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.notification.domain.Notification
import com.squamed.core.notification.repository.NotificationRepository
import com.squamed.core.task.domain.Task
import com.squamed.core.task.repository.TaskRepository
import com.squamed.core.workflow.api.CreateWorkflowDefinitionRequest
import com.squamed.core.workflow.api.DecideApprovalRequest
import com.squamed.core.workflow.api.TriggerWorkflowRequest
import com.squamed.core.workflow.api.WorkflowApprovalView
import com.squamed.core.workflow.api.WorkflowDefinitionDto
import com.squamed.core.workflow.api.WorkflowInstanceView
import com.squamed.core.workflow.api.WorkflowPendingApprovalView
import com.squamed.core.workflow.api.WorkflowStepConfigDto
import com.squamed.core.workflow.api.WorkflowStepInstanceView
import com.squamed.core.workflow.domain.WorkflowApproval
import com.squamed.core.workflow.domain.WorkflowDefinition
import com.squamed.core.workflow.domain.WorkflowInstance
import com.squamed.core.workflow.domain.WorkflowStepInstance
import com.squamed.core.workflow.repository.WorkflowApprovalRepository
import com.squamed.core.workflow.repository.WorkflowDefinitionRepository
import com.squamed.core.workflow.repository.WorkflowInstanceRepository
import com.squamed.core.workflow.repository.WorkflowStepInstanceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class WorkflowService(
  private val definitionRepository: WorkflowDefinitionRepository,
  private val instanceRepository: WorkflowInstanceRepository,
  private val stepRepository: WorkflowStepInstanceRepository,
  private val approvalRepository: WorkflowApprovalRepository,
  private val taskRepository: TaskRepository,
  private val notificationRepository: NotificationRepository,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    private val ALLOWED_STEP_TYPES = setOf("TASK", "APPROVAL", "NOTIFICATION", "ESCALATION", "ACTION", "CONDITION", "WAIT")
    private val CONDITION_PATTERN = Regex("""\s*([A-Za-z0-9_.]+)\s*(==|!=|>=|<=|>|<)\s*(.+?)\s*""")
    private val STEP_CONFIG = object : TypeReference<Map<String, Any?>>() {}
    private val PAYLOAD = object : TypeReference<Map<String, Any?>>() {}
    private val STEPS = object : TypeReference<List<WorkflowStepConfigDto>>() {}

    private const val STATUS_DRAFT = "DRAFT"
    private const val STATUS_ACTIVE = "ACTIVE"
    private const val STATUS_DISABLED = "DISABLED"
    private const val INSTANCE_RUNNING = "RUNNING"
    private const val INSTANCE_WAITING_APPROVAL = "WAITING_APPROVAL"
    private const val INSTANCE_WAITING = "WAITING"
    private const val INSTANCE_COMPLETED = "COMPLETED"
    private const val INSTANCE_TERMINATED = "TERMINATED"
    private const val INSTANCE_FAILED = "FAILED"
    private const val STEP_PENDING = "PENDING"
    private const val STEP_RUNNING = "RUNNING"
    private const val STEP_WAITING_APPROVAL = "WAITING_APPROVAL"
    private const val STEP_WAITING = "WAITING"
    private const val STEP_COMPLETED = "COMPLETED"
    private const val STEP_SKIPPED = "SKIPPED"
    private const val STEP_FAILED = "FAILED"
  }

  @Transactional
  fun createDefinition(req: CreateWorkflowDefinitionRequest): WorkflowDefinitionDto {
    val code = req.code?.trim().orEmpty()
    val name = req.name?.trim().orEmpty()
    require(code.isNotBlank()) { "WORKFLOW_CODE_REQUIRED" }
    require(name.isNotBlank()) { "WORKFLOW_NAME_REQUIRED" }
    val steps = validateSteps(req.steps ?: emptyList())
    val existing = definitionRepository.findByCodeOrderByVersionDesc(code)
    val nextVersion = if (existing.isEmpty()) 1L else existing.first().version + 1
    val definition =
      definitionRepository.save(
        WorkflowDefinition(
          code = code,
          name = name,
          description = req.description,
          triggerType = req.triggerType?.uppercase() ?: "MANUAL",
          definitionJson = serializeSteps(steps),
          status = STATUS_DRAFT,
          version = nextVersion,
        ),
      )
    return toDefinitionDto(definition)
  }

  @Transactional
  fun updateDefinition(
    id: UUID,
    req: CreateWorkflowDefinitionRequest,
  ): WorkflowDefinitionDto {
    val definition = findDefinition(id)
    require(definition.status != STATUS_ACTIVE) { "WORKFLOW_ACTIVE_NOT_EDITABLE" }
    val code = req.code?.trim().orEmpty()
    val name = req.name?.trim().orEmpty()
    if (code.isNotBlank()) definition.code = code
    if (name.isNotBlank()) definition.name = name
    req.description?.let { definition.description = it }
    req.triggerType?.let { definition.triggerType = it.uppercase() }
    val steps = req.steps
    if (steps != null) {
      definition.definitionJson = serializeSteps(validateSteps(steps))
    }
    return toDefinitionDto(definitionRepository.save(definition))
  }

  @Transactional
  fun activateDefinition(id: UUID): WorkflowDefinitionDto {
    val definition = findDefinition(id)
    if (definition.status == STATUS_ACTIVE) return toDefinitionDto(definition)
    require(definition.definitionJson.isNotBlank()) { "WORKFLOW_ACTIVATE_REQUIRES_STEPS" }
    definition.status = STATUS_ACTIVE
    return toDefinitionDto(definitionRepository.save(definition))
  }

  @Transactional
  fun deactivateDefinition(id: UUID): WorkflowDefinitionDto {
    val definition = findDefinition(id)
    if (definition.status == STATUS_ACTIVE) {
      definition.status = STATUS_DISABLED
      definitionRepository.save(definition)
    }
    return toDefinitionDto(definition)
  }

  @Transactional(readOnly = true)
  fun listDefinitions(): List<WorkflowDefinitionDto> = definitionRepository.findAllByOrderByCreatedAtDesc().map { toDefinitionDto(it) }

  @Transactional(readOnly = true)
  fun getDefinition(id: UUID): WorkflowDefinitionDto = toDefinitionDto(findDefinition(id))

  @Transactional
  fun trigger(
    req: TriggerWorkflowRequest,
    actorUserId: UUID?,
    hospitalId: UUID?,
  ): WorkflowInstanceView {
    val definition = resolveDefinition(req)
    require(definition.status == STATUS_ACTIVE) { "WORKFLOW_DEFINITION_MUST_BE_ACTIVE" }
    val steps = parseSteps(definition.definitionJson)
    require(steps.isNotEmpty()) { "WORKFLOW_DEFINITION_MUST_BE_ACTIVE" }
    val instance =
      instanceRepository
        .save(
          WorkflowInstance(
            hospitalId = hospitalId,
            definitionId = definition.id ?: throw IllegalArgumentException("WORKFLOW_DEFINITION_NOT_FOUND"),
            definitionCode = definition.code,
            status = INSTANCE_RUNNING,
            triggerPayload = req.payload?.let { objectMapper.writeValueAsString(it) },
          ),
        ).also { it.createdBy = actorUserId }
    val instanceId = instance.id ?: throw IllegalStateException("WORKFLOW_INSTANCE_ID_MISSING")
    for (step in steps) {
      stepRepository.save(
        WorkflowStepInstance(
          instanceId = instanceId,
          stepKey = step.stepKey,
          stepType = step.stepType.uppercase(),
          status = STEP_PENDING,
          stepConfig = step.config?.let { objectMapper.writeValueAsString(it) },
          sequenceOrder = step.sequenceOrder,
        ),
      )
    }
    run(instance, actorUserId)
    return toInstanceView(instance)
  }

  @Transactional
  fun decideApproval(
    instanceId: UUID,
    approvalId: UUID,
    req: DecideApprovalRequest,
    actorUserId: UUID?,
    force: Boolean,
  ): WorkflowInstanceView {
    val instance = findInstance(instanceId)
    val approval = findApproval(approvalId)
    require(approval.status == "PENDING") { "WORKFLOW_APPROVAL_NOT_PENDING" }
    if (!force && (actorUserId == null || actorUserId != approval.approverId)) {
      throw IllegalArgumentException("WORKFLOW_NOT_YOUR_APPROVAL")
    }
    val decision = req.decision?.uppercase() ?: throw IllegalArgumentException("WORKFLOW_DECISION_REQUIRED")
    require(decision == "APPROVED" || decision == "REJECTED") { "WORKFLOW_INVALID_DECISION" }
    approval.comments = req.comments
    approval.decidedBy = actorUserId
    approval.decidedAt = Instant.now()
    approval.status = decision
    approvalRepository.save(approval)

    if (decision == "REJECTED") {
      val step = findStep(approval.stepInstanceId)
      step.status = STEP_FAILED
      step.endedAt = Instant.now()
      step.result = objectMapper.writeValueAsString(mapOf("rejectedBy" to actorUserId?.toString()))
      stepRepository.save(step)
      instance.status = INSTANCE_TERMINATED
      instance.endedAt = Instant.now()
      instanceRepository.save(instance)
      return toInstanceView(instance)
    }

    val allApproved = approvalRepository.findByStepInstanceId(approval.stepInstanceId).all { it.status == "APPROVED" }
    if (allApproved) {
      val step = findStep(approval.stepInstanceId)
      completeStep(step, objectMapper.writeValueAsString(mapOf("approved" to true)))
      instance.status = INSTANCE_RUNNING
      instanceRepository.save(instance)
      run(instance, actorUserId)
    }
    return toInstanceView(instance)
  }

  @Transactional
  fun resume(
    instanceId: UUID,
    actorUserId: UUID?,
  ): WorkflowInstanceView {
    val instance = findInstance(instanceId)
    require(instance.status == INSTANCE_WAITING) { "WORKFLOW_INSTANCE_NOT_WAITING" }
    val currentStep =
      stepRepository
        .findByInstanceIdOrderBySequenceOrderAsc(instanceId)
        .firstOrNull { it.status == STEP_WAITING }
        ?: throw IllegalStateException("WORKFLOW_WAITING_STEP_NOT_FOUND")
    currentStep.status = STEP_COMPLETED
    currentStep.endedAt = Instant.now()
    currentStep.result = "resumed"
    stepRepository.save(currentStep)
    instance.status = INSTANCE_RUNNING
    instanceRepository.save(instance)
    run(instance, actorUserId)
    return toInstanceView(instance)
  }

  @Transactional(readOnly = true)
  fun listInstances(
    status: String?,
    hospitalId: UUID?,
  ): List<WorkflowInstanceView> {
    val instances =
      if (status == null) {
        instanceRepository.findAllByOrderByStartedAtDesc(hospitalId)
      } else {
        instanceRepository.findByStatus(status, hospitalId)
      }
    return instances.sortedByDescending { it.startedAt }.map { toInstanceView(it) }
  }

  @Transactional(readOnly = true)
  fun getInstance(
    id: UUID,
    hospitalId: UUID?,
  ): WorkflowInstanceView {
    val instance = findInstance(id)
    // 00.04 tenant sweep: instance reads are gated on the row's hospital (null rows denied for bound users).
    requireSameHospital(hospitalId, instance.hospitalId)
    return toInstanceView(instance)
  }

  @Transactional(readOnly = true)
  fun pendingApprovals(actorUserId: UUID): List<WorkflowPendingApprovalView> =
    approvalRepository.findByApproverIdAndStatus(actorUserId, "PENDING").map { approval ->
      val step = findStep(approval.stepInstanceId)
      val instance = findInstance(step.instanceId)
      WorkflowPendingApprovalView(
        approvalId = approval.id,
        instanceId = instance.id,
        instanceStatus = instance.status,
        definitionCode = instance.definitionCode,
        stepKey = step.stepKey,
        stepType = step.stepType,
        approverId = approval.approverId,
        status = approval.status,
        comments = approval.comments,
        decidedBy = approval.decidedBy,
        decidedAt = approval.decidedAt,
        createdAt = approval.createdAt,
      )
    }

  private fun run(
    instance: WorkflowInstance,
    actorUserId: UUID?,
  ) {
    val instanceId = instance.id ?: return
    val steps = stepRepository.findByInstanceIdOrderBySequenceOrderAsc(instanceId)
    for (step in steps) {
      if (step.status != STEP_PENDING) continue
      step.status = STEP_RUNNING
      step.startedAt = Instant.now()
      stepRepository.save(step)
      try {
        when (step.stepType) {
          "NOTIFICATION" -> {
            executeNotification(instance, step)
          }

          "TASK" -> {
            executeTask(instance, step, "WORKFLOW", null)
          }

          "CONDITION" -> {
            executeCondition(instance, step)
          }

          "APPROVAL" -> {
            executeApproval(instance, step)
            return
          }

          "WAIT" -> {
            executeWait(instance, step)
            return
          }

          "ESCALATION" -> {
            executeEscalation(instance, step)
          }

          "ACTION" -> {
            executeAction(instance, step)
          }

          else -> {
            markStepFailed(step, "Unsupported step type: ${step.stepType}")
            failInstance(instance)
            return
          }
        }
      } catch (e: Exception) {
        logger.warn(e) { "Workflow step ${step.stepKey} failed for instance ${instance.id}: ${e.message}" }
        markStepFailed(step, e.message ?: "Workflow step failed")
        failInstance(instance)
        return
      }
    }
    if (steps.any { it.status == STEP_FAILED }) {
      failInstance(instance)
    } else if (steps.all { it.status == STEP_COMPLETED || it.status == STEP_SKIPPED }) {
      instance.status = INSTANCE_COMPLETED
      instance.endedAt = Instant.now()
      instanceRepository.save(instance)
    }
  }

  private fun executeNotification(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
  ) {
    val config = stepConfig(step)
    val recipientId =
      uuidValue(config["recipientId"])
        ?: throw IllegalArgumentException("WORKFLOW_NOTIFICATION_RECIPIENT_REQUIRED")
    val notification =
      notificationRepository.save(
        Notification(
          hospitalId = instance.hospitalId,
          recipientId = recipientId,
          channel = "in_app",
          type = config["type"] as? String ?: "workflow",
          title = config["title"] as? String ?: "Workflow notification",
          body = config["body"] as? String,
          deliveredAt = Instant.now(),
        ),
      )
    completeStep(step, objectMapper.writeValueAsString(mapOf("createdNotificationId" to notification.id?.toString())))
  }

  private fun executeTask(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
    relatedType: String,
    dueInMinutes: Int?,
  ) {
    val config = stepConfig(step)
    val title = config["title"] as? String ?: throw IllegalArgumentException("WORKFLOW_TASK_TITLE_REQUIRED")
    val task =
      taskRepository.save(
        Task(
          hospitalId = instance.hospitalId,
          title = title,
          description = config["description"] as? String,
          assigneeId = uuidValue(config["assigneeId"]),
          priority = config["priority"] as? String ?: "normal",
          status = "pending",
          dueAt = dueInMinutes?.let { Instant.now().plusSeconds(it * 60L) },
          relatedType = relatedType,
          relatedId = instance.id,
        ),
      )
    completeStep(step, objectMapper.writeValueAsString(mapOf("createdTaskId" to task.id?.toString())))
  }

  private fun executeCondition(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
  ) {
    val config = stepConfig(step)
    val expression =
      config["expression"] as? String
        ?: throw IllegalArgumentException("WORKFLOW_CONDITION_EXPRESSION_REQUIRED")
    val payload = parsePayload(instance.triggerPayload)
    val evaluated = evaluateCondition(expression, payload)
    if (evaluated) {
      completeStep(step, objectMapper.writeValueAsString(mapOf("condition" to true, "expression" to expression)))
    } else {
      val ifFalse = (config["ifFalse"] as? String)?.lowercase() ?: "skip"
      step.status = if (ifFalse == "skip") STEP_SKIPPED else STEP_FAILED
      step.endedAt = Instant.now()
      step.result = objectMapper.writeValueAsString(mapOf("condition" to false, "expression" to expression))
      stepRepository.save(step)
    }
  }

  private fun executeApproval(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
  ) {
    val config = stepConfig(step)
    val approvers = config["approvers"]
    val approverIds = if (approvers is List<*>) approvers.mapNotNull { uuidValue(it) } else emptyList<UUID>()
    require(approverIds.isNotEmpty()) { "WORKFLOW_APPROVERS_REQUIRED" }
    val stepId = step.id ?: throw IllegalStateException("WORKFLOW_STEP_ID_MISSING")
    approverIds.distinct().forEach { approverId ->
      approvalRepository.save(WorkflowApproval(stepInstanceId = stepId, approverId = approverId))
    }
    step.status = STEP_WAITING_APPROVAL
    stepRepository.save(step)
    instance.status = INSTANCE_WAITING_APPROVAL
    instance.currentStepKey = step.stepKey
    instanceRepository.save(instance)
  }

  private fun executeWait(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
  ) {
    step.status = STEP_WAITING
    stepRepository.save(step)
    instance.status = INSTANCE_WAITING
    instance.currentStepKey = step.stepKey
    instanceRepository.save(instance)
  }

  private fun executeEscalation(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
  ) {
    val config = stepConfig(step)
    require(uuidValue(config["assigneeId"]) != null) { "WORKFLOW_ESCALATION_ASSIGNEE_REQUIRED" }
    executeTask(instance, step, "WORKFLOW_ESCALATION", (config["dueInMinutes"] as? Number)?.toInt())
  }

  private fun executeAction(
    instance: WorkflowInstance,
    step: WorkflowStepInstance,
  ) {
    val config = stepConfig(step)
    completeStep(step, objectMapper.writeValueAsString(mapOf("action" to "performed", "type" to (config["type"] as? String))))
  }

  private fun evaluateCondition(
    expression: String,
    payload: Map<String, Any?>,
  ): Boolean {
    val match = CONDITION_PATTERN.matchEntire(expression.trim()) ?: return false
    val field = match.groupValues[1]
    val op = match.groupValues[2]
    var rawValue = match.groupValues[3].trim()
    if (rawValue.length >= 2 && rawValue.first() == '"' && rawValue.last() == '"') {
      rawValue = rawValue.substring(1, rawValue.length - 1)
    }
    val actual = resolvePointer(payload, field) ?: return false
    return when (op) {
      "==" -> valuesEqual(actual, rawValue)
      "!=" -> !valuesEqual(actual, rawValue)
      ">", ">=", "<", "<=" -> compareNumeric(actual, rawValue, op)
      else -> false
    }
  }

  private fun resolvePointer(
    payload: Map<String, Any?>,
    path: String,
  ): Any? {
    var current: Any? = payload
    for (part in path.split(".")) {
      current = if (current is Map<*, *>) current[part] else return null
      if (current == null) return null
    }
    return current
  }

  private fun valuesEqual(
    actual: Any?,
    rawValue: String,
  ): Boolean {
    if (actual == null) return false
    val rawNumber = rawValue.toDoubleOrNull()
    if (rawNumber != null) {
      val actualNumber = (actual as? Number)?.toDouble()
      if (actualNumber != null) return actualNumber == rawNumber
    }
    return actual.toString() == rawValue
  }

  private fun compareNumeric(
    actual: Any?,
    rawValue: String,
    op: String,
  ): Boolean {
    val a = (actual as? Number)?.toDouble() ?: return false
    val b = rawValue.toDoubleOrNull() ?: return false
    return when (op) {
      ">" -> a > b
      ">=" -> a >= b
      "<" -> a < b
      "<=" -> a <= b
      else -> false
    }
  }

  private fun completeStep(
    step: WorkflowStepInstance,
    result: String?,
  ) {
    step.status = STEP_COMPLETED
    step.endedAt = Instant.now()
    step.result = result
    stepRepository.save(step)
  }

  private fun markStepFailed(
    step: WorkflowStepInstance,
    error: String,
  ) {
    step.status = STEP_FAILED
    step.endedAt = Instant.now()
    step.result = runCatching { objectMapper.writeValueAsString(mapOf("error" to error)) }.getOrNull()
    stepRepository.save(step)
  }

  private fun failInstance(instance: WorkflowInstance) {
    instance.status = INSTANCE_FAILED
    instance.endedAt = Instant.now()
    instanceRepository.save(instance)
  }

  private fun validateSteps(steps: List<WorkflowStepConfigDto>): List<WorkflowStepConfigDto> {
    require(steps.isNotEmpty()) { "WORKFLOW_STEPS_REQUIRED" }
    val keys = steps.map { it.stepKey.trim() }
    require(keys.none { it.isBlank() }) { "WORKFLOW_STEP_KEY_REQUIRED" }
    require(keys.distinct().size == keys.size) { "WORKFLOW_STEP_KEYS_MUST_BE_UNIQUE" }
    steps.forEach { step ->
      require(step.stepType.uppercase() in ALLOWED_STEP_TYPES) { "WORKFLOW_UNSUPPORTED_STEP_TYPE:${step.stepType}" }
    }
    return steps.sortedBy { it.sequenceOrder }
  }

  private fun serializeSteps(steps: List<WorkflowStepConfigDto>): String = objectMapper.writeValueAsString(steps)

  private fun parseSteps(json: String?): List<WorkflowStepConfigDto> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      objectMapper.readValue(json, STEPS)
    } catch (e: Exception) {
      logger.warn { "Failed to parse workflow definition steps: ${e.message}" }
      emptyList()
    }
  }

  private fun stepConfig(step: WorkflowStepInstance): Map<String, Any?> {
    if (step.stepConfig.isNullOrBlank()) return emptyMap()
    return try {
      objectMapper.readValue(step.stepConfig, STEP_CONFIG)
    } catch (e: Exception) {
      emptyMap()
    }
  }

  private fun parsePayload(payload: String?): Map<String, Any?> {
    if (payload.isNullOrBlank()) return emptyMap()
    return try {
      objectMapper.readValue(payload, PAYLOAD)
    } catch (e: Exception) {
      emptyMap()
    }
  }

  private fun uuidValue(value: Any?): UUID? =
    when (value) {
      is UUID -> value
      is String -> runCatching { UUID.fromString(value) }.getOrNull()
      else -> null
    }

  private fun resolveDefinition(req: TriggerWorkflowRequest): WorkflowDefinition {
    val definitionId = req.definitionId
    if (definitionId != null) {
      return findDefinition(definitionId)
    }
    val code = req.code?.trim().orEmpty()
    if (code.isNotBlank()) {
      return definitionRepository.findByCodeOrderByVersionDesc(code).firstOrNull()
        ?: throw IllegalArgumentException("WORKFLOW_DEFINITION_NOT_FOUND")
    }
    throw IllegalArgumentException("WORKFLOW_DEFINITION_ID_OR_CODE_REQUIRED")
  }

  private fun toDefinitionDto(definition: WorkflowDefinition) =
    WorkflowDefinitionDto(
      id = definition.id,
      code = definition.code,
      name = definition.name,
      description = definition.description,
      triggerType = definition.triggerType,
      status = definition.status,
      version = definition.version,
      steps = parseSteps(definition.definitionJson),
    )

  private fun toInstanceView(instance: WorkflowInstance): WorkflowInstanceView {
    val instanceId = instance.id
    val steps =
      if (instanceId ==
        null
      ) {
        emptyList<WorkflowStepInstance>()
      } else {
        stepRepository.findByInstanceIdOrderBySequenceOrderAsc(instanceId)
      }
    val approvals = steps.mapNotNull { it.id }.flatMap { approvalRepository.findByStepInstanceId(it) }
    return WorkflowInstanceView(
      id = instance.id,
      definitionId = instance.definitionId,
      definitionCode = instance.definitionCode,
      status = instance.status,
      triggerPayload = instance.triggerPayload,
      currentStepKey = instance.currentStepKey,
      startedAt = instance.startedAt,
      endedAt = instance.endedAt,
      stepInstances = steps.map { WorkflowStepInstanceView.from(it) },
      approvals = approvals.map { WorkflowApprovalView.from(it) },
    )
  }

  private fun findDefinition(id: UUID): WorkflowDefinition =
    definitionRepository.findById(id).orElseThrow { IllegalArgumentException("WORKFLOW_DEFINITION_NOT_FOUND") }

  private fun findInstance(id: UUID): WorkflowInstance = instanceRepository.findById(id).orElseThrow { IllegalArgumentException("WORKFLOW_INSTANCE_NOT_FOUND") }

  private fun findStep(id: UUID): WorkflowStepInstance = stepRepository.findById(id).orElseThrow { IllegalArgumentException("WORKFLOW_STEP_NOT_FOUND") }

  private fun findApproval(id: UUID): WorkflowApproval = approvalRepository.findById(id).orElseThrow { IllegalArgumentException("WORKFLOW_APPROVAL_NOT_FOUND") }
}
