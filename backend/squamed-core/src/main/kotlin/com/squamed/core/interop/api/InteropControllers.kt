package com.squamed.core.interop.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.interop.fhir.FhirService
import com.squamed.core.interop.hl7.Hl7Service
import com.squamed.core.interop.service.IntegrationConnectionService
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ConnectionCreateRequest(
  val name: String,
  val connectionType: String,
  val endpoint: String? = null,
  val username: String? = null,
  val password: String? = null,
)

data class ConnectionUpdateRequest(
  val name: String? = null,
  val connectionType: String? = null,
  val endpoint: String? = null,
  val username: String? = null,
  val password: String? = null,
  val enabled: Boolean? = null,
)

@RestController
@RequestMapping("/api/v1/interop")
class InteropController(
  private val connectionService: IntegrationConnectionService,
) {
  @GetMapping("/connections")
  @RequiresPermission("integration:read:integration")
  @Transactional(readOnly = true)
  fun list(): ApiResponse<List<Map<String, Any?>>> {
    val connections =
      connectionService.list().map { conn ->
        mapOf(
          "id" to conn.id,
          "name" to conn.name,
          "connectionType" to conn.connectionType,
          "endpoint" to conn.endpoint,
          "enabled" to conn.enabled,
          "lastHealthCheckAt" to conn.lastHealthCheckAt,
          "lastSuccessAt" to conn.lastSuccessAt,
          "lastError" to conn.lastError,
          "errorCount" to conn.errorCount,
        )
      }
    return ApiResponse.ok(connections)
  }

  @PostMapping("/connections")
  @RequiresPermission("integration:manage:integration")
  fun create(
    @RequestBody req: ConnectionCreateRequest,
  ): ApiResponse<Map<String, Any?>> {
    val conn = connectionService.create(req.name, req.connectionType, req.endpoint, req.username, req.password)
    return ApiResponse.ok(mapOf("id" to conn.id, "name" to conn.name, "connectionType" to conn.connectionType))
  }

  @PutMapping("/connections/{id}")
  @RequiresPermission("integration:manage:integration")
  fun update(
    @PathVariable id: UUID,
    @RequestBody req: ConnectionUpdateRequest,
  ): ApiResponse<Map<String, Any?>> {
    val conn = connectionService.update(id, req.name, req.connectionType, req.endpoint, req.username, req.password, req.enabled)
    return ApiResponse.ok(mapOf("id" to conn.id, "name" to conn.name, "enabled" to conn.enabled))
  }

  @DeleteMapping("/connections/{id}")
  @RequiresPermission("integration:manage:integration")
  fun delete(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> {
    connectionService.delete(id)
    return ApiResponse.ok(mapOf("deleted" to true))
  }

  @GetMapping("/health")
  @RequiresPermission("integration:read:integration")
  fun health(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(connectionService.health())
}

data class Hl7ReceiveRequest(
  val raw: String,
)

data class Hl7ParseRequest(
  val raw: String,
)

@RestController
@RequestMapping("/api/v1/hl7")
class Hl7Controller(
  private val hl7Service: Hl7Service,
) {
  @PostMapping("/parse")
  @RequiresPermission("integration:read:integration")
  fun parse(
    @RequestBody req: Hl7ParseRequest,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(hl7Service.parse(req.raw))

  @PostMapping("/messages")
  @RequiresPermission("integration:manage:integration")
  @Transactional
  fun receive(
    @RequestBody req: Hl7ReceiveRequest,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(hl7Service.receive(req.raw, "REST"))

  @GetMapping("/messages")
  @RequiresPermission("integration:read:integration")
  fun history(): ApiResponse<List<Map<String, Any?>>> = ApiResponse.ok(hl7Service.history(100))

  @GetMapping("/health")
  @RequiresPermission("integration:read:integration")
  fun health(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(hl7Service.health())
}

@RestController
@RequestMapping("/api/v1/fhir")
class FhirController(
  private val fhirService: FhirService,
) {
  @GetMapping("/Patient")
  @RequiresPermission("patient:read:patient")
  fun allPatients(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allPatients()))

  @GetMapping("/Patient/{id}")
  @RequiresPermission("patient:read:patient")
  fun patient(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.patient(id))

  @GetMapping("/Practitioner")
  @RequiresPermission("identity:read:user")
  fun allPractitioners(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allPractitioners()))

  @GetMapping("/Practitioner/{id}")
  @RequiresPermission("identity:read:user")
  fun practitioner(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.practitioner(id))

  @GetMapping("/Organization")
  @RequiresPermission("hospital-config:read:hospital")
  fun allOrganizations(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allOrganizations()))

  @GetMapping("/Encounter")
  @RequiresPermission("encounter:read:encounter")
  fun allEncounters(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allEncounters()))

  @GetMapping("/Encounter/{id}")
  @RequiresPermission("encounter:read:encounter")
  fun encounter(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.encounter(id))

  @GetMapping("/Condition")
  @RequiresPermission("clinical:read:encounter")
  fun allConditions(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allConditions()))

  @GetMapping("/Condition/{id}")
  @RequiresPermission("clinical:read:encounter")
  fun condition(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.condition(id))

  @GetMapping("/Observation")
  @RequiresPermission("clinical:read:vital")
  fun allObservations(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allObservations()))

  @GetMapping("/Observation/{id}")
  @RequiresPermission("clinical:read:vital")
  fun observation(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.observation(id))

  @GetMapping("/MedicationRequest")
  @RequiresPermission("pharmacy:read:stock")
  fun allMedicationRequests(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allMedicationRequests()))

  @GetMapping("/MedicationRequest/{id}")
  @RequiresPermission("pharmacy:read:stock")
  fun medicationRequest(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.medicationRequest(id))

  @GetMapping("/ServiceRequest")
  @RequiresPermission("orders:read:order")
  fun allServiceRequests(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allServiceRequests()))

  @GetMapping("/ServiceRequest/{id}")
  @RequiresPermission("orders:read:order")
  fun serviceRequest(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.serviceRequest(id))

  @GetMapping("/AllergyIntolerance")
  @RequiresPermission("patient:read:patient")
  fun allAllergies(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allAllergies()))

  @GetMapping("/AllergyIntolerance/{id}")
  @RequiresPermission("patient:read:patient")
  fun allergy(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.allergyIntolerance(id))

  @GetMapping("/DiagnosticReport")
  @RequiresPermission("laboratory:read:lab")
  fun allReports(): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.bundle(fhirService.allDiagnosticReports()))

  @GetMapping("/DiagnosticReport/{id}")
  @RequiresPermission("laboratory:read:lab")
  fun report(
    @PathVariable id: UUID,
  ): ApiResponse<Map<String, Any?>> = ApiResponse.ok(fhirService.diagnosticReport(id))
}
