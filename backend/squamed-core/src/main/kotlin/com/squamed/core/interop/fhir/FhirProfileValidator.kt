package com.squamed.core.interop.fhir

/**
 * Local profile check for the five outbound resources (06.07). The rules are the mandatory
 * EHR-M-aligned elements we can enforce without an external validator; HAPI still validates
 * cardinality on write, and anything it rejects is recorded as FAILED rather than assumed.
 */
object FhirProfileValidator {
  private val GENDERS = setOf("male", "female", "other", "unknown")
  private val ENCOUNTER_STATUS = setOf("planned", "arrived", "triaged", "in-progress", "onleave", "finished", "cancelled", "entered-in-error", "unknown")
  private val OBSERVATION_STATUS = setOf("registered", "preliminary", "final", "amended", "corrected", "cancelled", "entered-in-error", "unknown")
  private val DIAGNOSTIC_STATUS =
    setOf("registered", "partial", "preliminary", "final", "amended", "corrected", "appended", "cancelled", "entered-in-error", "unknown")
  private val MEDICATION_STATUS = setOf("active", "cancelled", "completed", "draft", "entered-in-error", "stopped", "unknown")

  // `?.toString()` on a null Instant used to emit the literal string "null"; reject it here too so a
  // regression fails locally instead of at the peer.
  private fun isDate(value: Any?): Boolean = value is String && value.isNotBlank() && value != "null"

  fun validate(
    resourceType: String,
    resource: Map<String, Any?>,
  ): List<String> {
    val problems = mutableListOf<String>()

    fun require(
      path: String,
      present: Boolean,
      message: String,
    ) {
      if (!present) problems.add("$resourceType.$path: $message")
    }
    when (resourceType) {
      "Patient" -> {
        val identifiers = resource["identifier"] as? List<*>
        require("identifier", !identifiers.isNullOrEmpty(), "at least one identifier is required")
        val names = resource["name"] as? List<*>
        require("name", !names.isNullOrEmpty(), "a human name is required")
        require("gender", resource["gender"] in GENDERS, "gender must be one of ${GENDERS.joinToString()}")
        require("birthDate", isDate(resource["birthDate"]), "birthDate must be an ISO date")
      }

      "Encounter" -> {
        require("status", resource["status"] in ENCOUNTER_STATUS, "status must be one of $ENCOUNTER_STATUS")
        require("class", (resource["class"] as? Map<*, *>)?.get("code") != null, "class.code is required")
        require("subject", (resource["subject"] as? Map<*, *>)?.get("reference") != null, "subject.reference is required")
        require("period", isDate((resource["period"] as? Map<*, *>)?.get("start")), "period.start must be an ISO date/time")
      }

      "Observation" -> {
        require("status", resource["status"] in OBSERVATION_STATUS, "status must be one of $OBSERVATION_STATUS")
        require("code", resource["code"] != null, "code is required")
        require("subject", (resource["subject"] as? Map<*, *>)?.get("reference") != null, "subject.reference is required")
        require("effectiveDateTime", isDate(resource["effectiveDateTime"]), "effectiveDateTime must be an ISO date/time")
      }

      "DiagnosticReport" -> {
        require("status", resource["status"] in DIAGNOSTIC_STATUS, "status must be one of $DIAGNOSTIC_STATUS")
        require("code", resource["code"] != null, "code is required")
        require("subject", (resource["subject"] as? Map<*, *>)?.get("reference") != null, "subject.reference is required")
      }

      "MedicationRequest" -> {
        require("status", resource["status"] in MEDICATION_STATUS, "status must be one of $MEDICATION_STATUS")
        require("intent", !(resource["intent"] as? String).isNullOrBlank(), "intent is required")
        require("subject", (resource["subject"] as? Map<*, *>)?.get("reference") != null, "subject.reference is required")
        require("medicationCodeableConcept", resource["medicationCodeableConcept"] != null, "medication is required")
        require("authoredOn", isDate(resource["authoredOn"]), "authoredOn must be an ISO date/time")
      }

      else -> {
        problems.add("$resourceType: no outbound profile is defined")
      }
    }
    return problems
  }
}
