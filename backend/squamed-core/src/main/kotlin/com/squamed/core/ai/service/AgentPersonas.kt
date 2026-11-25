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
}
