package com.squamed.core.interop.fhir

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.consent.repository.ConsentRepository
import com.squamed.core.interop.domain.FhirExport
import com.squamed.core.interop.repository.FhirExportRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 06.07 outbound interop: pushes governed records to a peer HAPI FHIR server.
 * Documents (DiagnosticReport) need a granted `data_sharing` consent; without one they are
 * recorded WITHHELD so the receiver knows they exist rather than silently missing them.
 */
@Service
class FhirOutboundService(
  private val fhirService: FhirService,
  private val exportRepository: FhirExportRepository,
  private val patientRepository: PatientRecordRepository,
  private val consentRepository: ConsentRepository,
  private val objectMapper: ObjectMapper,
  @Value("\${squamed.fhir.peer-url:http://localhost:8081/fhir}") private val defaultPeerUrl: String,
) {
  private val http: HttpClient =
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  @Transactional
  fun exportPatient(
    hospitalId: UUID,
    patientId: UUID,
    targetBase: String?,
    actorId: UUID?,
  ): Map<String, Any?> {
    val patient = patientRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    if (patient.hospitalId != hospitalId) throw IllegalArgumentException("PATIENT_NOT_IN_HOSPITAL")
    val base = (targetBase ?: defaultPeerUrl).trimEnd('/')
    val sharingConsent = hasSharingConsent(patientId)

    // shortcut: per-patient reads filter the existing all* lists; add patient-scoped repository
    // queries if a patient ever accumulates enough vitals/prescriptions for this to matter.
    val resources =
      buildList {
        add(fhirService.patient(patientId))
        fhirService.encountersByPatient(patientId).forEach { add(it) }
        fhirService.allObservations().filter { ownedBy(it, patientId) }.forEach { add(it) }
        fhirService.allMedicationRequests().filter { ownedBy(it, patientId) }.forEach { add(it) }
        fhirService.allDiagnosticReports().filter { ownedBy(it, patientId) }.forEach { add(it) }
      }

    val results = resources.map { resource -> push(base, hospitalId, patientId, resource, sharingConsent, actorId) }
    return mapOf(
      "target" to base,
      "patientId" to patientId,
      "exported" to results.count { it["status"] == "EXPORTED" },
      "failed" to results.count { it["status"] == "FAILED" },
      "withheld" to results.filter { it["status"] == "WITHHELD" },
      "resources" to results,
    )
  }

  @Transactional(readOnly = true)
  fun history(hospitalId: UUID): List<FhirExport> = exportRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  private fun push(
    base: String,
    hospitalId: UUID,
    patientId: UUID,
    resource: Map<String, Any?>,
    sharingConsent: Boolean,
    actorId: UUID?,
  ): Map<String, Any?> {
    val type = resource["resourceType"] as String
    val id = UUID.fromString(resource["id"] as String)
    val record =
      FhirExport(
        hospitalId = hospitalId,
        patientId = patientId,
        targetBase = base,
        resourceType = type,
        resourceId = id,
        status = "FAILED",
      ).also {
        it.createdBy = actorId
        it.updatedBy = actorId
      }

    if (type == "DiagnosticReport" && !sharingConsent) {
      record.status = "WITHHELD"
      record.detail = "CONSENT_REQUIRED: no granted data_sharing consent"
      exportRepository.save(record)
      return view(record)
    }

    val violations = FhirProfileValidator.validate(type, resource)
    if (violations.isNotEmpty()) {
      record.detail = violations.joinToString("; ").take(500)
      exportRepository.save(record)
      return view(record)
    }

    val payload = objectMapper.writeValueAsString(prune(resource))
    val response =
      try {
        send(base, type, id, payload)
      } catch (ex: Exception) {
        record.detail = "PEER_UNREACHABLE: ${ex.message}".take(500)
        exportRepository.save(record)
        return view(record)
      }
    record.httpStatus = response.first
    if (response.first in 200..299) {
      record.status = "EXPORTED"
      record.exportedAt = Instant.now()
    } else {
      record.detail = "PEER_REJECTED: HTTP ${response.first} ${response.second}".take(500)
    }
    exportRepository.save(record)
    return view(record)
  }

  private fun send(
    base: String,
    type: String,
    id: UUID,
    body: String,
  ): Pair<Int, String> {
    val request =
      HttpRequest
        .newBuilder(URI.create("$base/$type/$id"))
        .timeout(Duration.ofSeconds(20))
        .header("Content-Type", "application/fhir+json")
        .PUT(HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofString())
    return response.statusCode() to response.body().replace(Regex("\\s+"), " ")
  }

  private fun hasSharingConsent(patientId: UUID): Boolean {
    val now = Instant.now()
    return consentRepository.findByPatientId(patientId).any {
      it.purpose == "data_sharing" && it.status == "granted" && (it.expiresAt == null || it.expiresAt!!.isAfter(now))
    }
  }

  private fun ownedBy(
    resource: Map<String, Any?>,
    patientId: UUID,
  ): Boolean = (resource["subject"] as? Map<*, *>)?.get("reference") == "Patient/$patientId"

  // FHIR JSON has no null values: HAPI rejects e.g. `"issued": null` as an invalid date.
  // The internal maps are built with optional keys, so drop them at the wire boundary.
  private fun prune(value: Any?): Any? =
    when (value) {
      is Map<*, *> -> {
        buildMap {
          value.forEach { (k, v) -> if (v != null) put(k.toString(), prune(v)) }
        }
      }

      is List<*> -> {
        value.mapNotNull { prune(it) }
      }

      else -> {
        value
      }
    }

  private fun view(record: FhirExport): Map<String, Any?> =
    mapOf(
      "resourceType" to record.resourceType,
      "resourceId" to record.resourceId,
      "status" to record.status,
      "httpStatus" to record.httpStatus,
      "detail" to record.detail,
    )
}
