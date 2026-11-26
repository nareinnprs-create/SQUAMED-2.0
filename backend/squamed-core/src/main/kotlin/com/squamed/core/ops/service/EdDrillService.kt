package com.squamed.core.ops.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.ops.api.EdEncounterCreateDto
import com.squamed.core.ops.domain.EdDrill
import com.squamed.core.ops.repository.EdDrillRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class DrillCasualtyInput(
  val patientId: UUID,
  val triageLevel: Int,
  val chiefComplaint: String,
)

/**
 * 07.05 drill harness: runs a mass-casualty exercise through the real ED paths and records timings.
 * Validation happens before anything is declared, and the stand-down runs even when a casualty fails,
 * so a broken drill can never leave the ED stuck in "mass casualty active".
 */
@Service
class EdDrillService(
  private val edService: EdService,
  private val drillRepository: EdDrillRepository,
  private val objectMapper: ObjectMapper,
) {
  @Transactional
  fun run(
    hospitalId: UUID,
    name: String,
    casualties: List<DrillCasualtyInput>,
    actorId: UUID?,
  ): Map<String, Any?> {
    require(name.isNotBlank()) { "DRILL_NAME_REQUIRED" }
    require(casualties.isNotEmpty()) { "DRILL_CASUALTIES_REQUIRED" }
    casualties.forEach {
      require(it.triageLevel in 1..5) { "DRILL_TRIAGE_INVALID" }
      require(it.chiefComplaint.isNotBlank()) { "DRILL_COMPLAINT_REQUIRED" }
    }

    val started = System.currentTimeMillis()
    val drill = drillRepository.save(EdDrill(hospitalId = hospitalId, name = name, plannedCasualties = casualties.size)).also { it.createdBy = actorId }
    val declared = edService.declareMassCasualty(hospitalId, "drill: $name", actorId)
    drill.declaredAt = declared.declaredAt
    val created = mutableListOf<String>()
    var failure: String? = null
    try {
      casualties.forEach { casualty ->
        val encounter =
          edService.create(
            hospitalId,
            EdEncounterCreateDto(casualty.patientId, casualty.triageLevel, casualty.chiefComplaint),
            actorId,
          )
        created.add(encounter.id.toString())
      }
    } catch (ex: Exception) {
      failure = ex.message
    } finally {
      // Always stand down: a failed drill must not leave the ED in mass-casualty mode.
      drill.stoodDownAt = edService.standDownMassCasualty(declared.id!!, hospitalId, actorId).stoodDownAt
    }
    drill.createdCasualties = created.size
    drill.triageDistribution = objectMapper.valueToTree(casualties.groupingBy { it.triageLevel }.eachCount())
    drill.status = if (failure == null) "COMPLETED" else "FAILED"
    drill.detail = failure?.take(500)
    drill.elapsedMs = System.currentTimeMillis() - started
    drill.updatedBy = actorId
    val saved = drillRepository.save(drill)
    return mapOf(
      "id" to saved.id,
      "name" to saved.name,
      "status" to saved.status,
      "planned" to saved.plannedCasualties,
      "created" to saved.createdCasualties,
      "triageDistribution" to saved.triageDistribution,
      "declaredAt" to saved.declaredAt,
      "stoodDownAt" to saved.stoodDownAt,
      "elapsedMs" to saved.elapsedMs,
      "detail" to saved.detail,
    )
  }

  @Transactional(readOnly = true)
  fun drills(hospitalId: UUID): List<EdDrill> = drillRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
}
