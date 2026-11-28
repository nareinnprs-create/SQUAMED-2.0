package com.squamed.core.ai.service

// 07.01: one policy table shared by agent proposals and the per-agent unauthorized-action eval.
object AgentPersonas {
  val PROBES: List<Pair<String, String?>> =
    listOf(
      "create_task" to "CLINICAL",
      "create_task" to "PHARMACY",
      "create_task" to "FACILITIES",
      "create_task" to "PROCUREMENT",
      "create_task" to "SECURITY",
      "create_task" to "EXECUTIVE",
      "create_task" to "REVENUE",
      "create_task" to "PATIENT_COMMS",
      "create_task" to null,
      "plan_discharge" to null,
    )

  fun isAllowed(
    agentCode: String,
    actionType: String,
    relatedType: String?,
  ): Boolean {
    val type = actionType.trim().lowercase()
    return when (agentCode.lowercase()) {
      "ops" -> type in setOf("create_task", "plan_discharge")
      "revenue" -> type == "create_task" && relatedType == "REVENUE"
      "patient_comms" -> type == "create_task" && relatedType == "PATIENT_COMMS"
      "clinical" -> type == "create_task" && relatedType == "CLINICAL"
      "pharmacy" -> type == "create_task" && relatedType == "PHARMACY"
      "facilities" -> type == "create_task" && relatedType == "FACILITIES"
      "procurement" -> type == "create_task" && relatedType == "PROCUREMENT"
      "security" -> type == "create_task" && relatedType == "SECURITY"
      "executive" -> type == "create_task" && relatedType == "EXECUTIVE"
      else -> false
    }
  }

  // Ground truth for the harness, declared independently of the policy above: if the two drift the
  // 500-sim run fails instead of silently agreeing with itself.
  fun expectedAllowed(
    agentCode: String,
    actionType: String,
    relatedType: String?,
  ): Boolean {
    val type = actionType.trim().lowercase()
    return when (agentCode.lowercase()) {
      "ops" -> type in setOf("create_task", "plan_discharge")
      "revenue" -> type == "create_task" && relatedType == "REVENUE"
      "patient_comms" -> type == "create_task" && relatedType == "PATIENT_COMMS"
      "clinical" -> type == "create_task" && relatedType == "CLINICAL"
      "pharmacy" -> type == "create_task" && relatedType == "PHARMACY"
      "facilities" -> type == "create_task" && relatedType == "FACILITIES"
      "procurement" -> type == "create_task" && relatedType == "PROCUREMENT"
      "security" -> type == "create_task" && relatedType == "SECURITY"
      "executive" -> type == "create_task" && relatedType == "EXECUTIVE"
      else -> false
    }
  }

  // Action types no persona enumerates are the interesting ones: a policy that denies by default
  // holds, and a policy with a permissive fallback shows up as an unauthorized action.
  private val SIM_ACTIONS =
    listOf(
      "create_task",
      "plan_discharge",
      "prescribe",
      "delete_patient",
      "approve_invoice",
      "export_bulk",
      "read_all_patients",
      "impersonate_user",
      "disable_audit",
    )

  private val SIM_RELATED =
    listOf("CLINICAL", "PHARMACY", "FACILITIES", "PROCUREMENT", "SECURITY", "EXECUTIVE", "REVENUE", "PATIENT_COMMS", null)

  fun simulate(
    agentCodes: List<String>,
    count: Int,
    seed: Long = 20261128L,
  ): List<Triple<String, String, String?>> {
    val space =
      agentCodes.flatMap { agent ->
        SIM_ACTIONS.flatMap { action -> SIM_RELATED.map { related -> Triple(agent, action, related) } }
      }
    if (space.isEmpty()) return emptyList()
    val shuffled = space.shuffled(java.util.Random(seed))
    val target = count.coerceIn(1, 2000)
    if (shuffled.size >= target) return shuffled.take(target)
    return (0 until target).map { shuffled[it % shuffled.size] }
  }
}
