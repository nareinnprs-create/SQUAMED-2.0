package com.squamed.core.laboratory.service

import com.squamed.core.laboratory.domain.MicroAst
import com.squamed.core.laboratory.domain.MicroCulture
import com.squamed.core.laboratory.domain.PathSlide
import com.squamed.core.laboratory.domain.PathSpecimen
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.laboratory.repository.MicroAstRepository
import com.squamed.core.laboratory.repository.MicroCultureRepository
import com.squamed.core.laboratory.repository.PathSlideRepository
import com.squamed.core.laboratory.repository.PathSpecimenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class AstEntry(
  val antibioticCode: String,
  val antibioticName: String,
  val susceptibility: String,
  val mic: BigDecimal?,
)

@Service
class LabDepthService(
  private val cultureRepository: MicroCultureRepository,
  private val astRepository: MicroAstRepository,
  private val specimenRepository: PathSpecimenRepository,
  private val slideRepository: PathSlideRepository,
  private val labOrderRepository: LabOrderRepository,
) {
  // -- microbiology ---------------------------------------------------------

  @Transactional
  fun createCulture(
    hospitalId: UUID,
    labOrderId: UUID,
    specimenType: String,
    notes: String?,
    actorId: UUID?,
  ): MicroCulture {
    if (specimenType.isBlank()) throw IllegalArgumentException("MICRO_SPECIMEN_TYPE_REQUIRED")
    labOrderRepository.findByIdAndHospitalId(labOrderId, hospitalId)
      ?: throw IllegalArgumentException("LAB_ORDER_NOT_FOUND")
    if (cultureRepository.findByLabOrderId(labOrderId) != null) throw IllegalStateException("MICRO_CULTURE_EXISTS")
    return cultureRepository.save(
      MicroCulture(
        hospitalId = hospitalId,
        labOrderId = labOrderId,
        specimenType = specimenType.trim(),
        notes = notes?.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun identifyOrganism(
    id: UUID,
    hospitalId: UUID,
    organismCode: String,
    organismName: String,
    actorId: UUID?,
  ): MicroCulture {
    if (organismCode.isBlank() || organismName.isBlank()) throw IllegalArgumentException("MICRO_ORGANISM_REQUIRED")
    val culture = findCulture(id, hospitalId)
    if (culture.status != "PENDING") throw IllegalStateException("MICRO_ORGANISM_ALREADY_IDENTIFIED")
    culture.organismCode = organismCode.trim().uppercase()
    culture.organismName = organismName.trim()
    culture.status = "ORGANISM_IDENTIFIED"
    culture.identifiedAt = Instant.now()
    culture.identifiedBy = actorId
    culture.updatedBy = actorId
    return cultureRepository.save(culture)
  }

  @Transactional
  fun recordAst(
    id: UUID,
    hospitalId: UUID,
    panel: String,
    entries: List<AstEntry>,
    actorId: UUID?,
  ): List<MicroAst> {
    if (panel.isBlank()) throw IllegalArgumentException("MICRO_AST_PANEL_REQUIRED")
    if (entries.isEmpty()) throw IllegalArgumentException("MICRO_AST_REQUIRED")
    val culture = findCulture(id, hospitalId)
    if (culture.status == "FINAL") throw IllegalStateException("MICRO_CULTURE_FINALIZED")
    if (culture.organismCode == null) throw IllegalArgumentException("MICRO_ORGANISM_REQUIRED")
    val seen = mutableSetOf<String>()
    entries.forEach { entry ->
      val code = entry.antibioticCode.trim().uppercase()
      if (code.isBlank() || entry.antibioticName.isBlank()) throw IllegalArgumentException("MICRO_AST_FIELDS_REQUIRED")
      if (entry.susceptibility.trim().uppercase() !in setOf("S", "I", "R")) throw IllegalArgumentException("MICRO_AST_RESULT_INVALID")
      if (entry.mic != null && entry.mic < BigDecimal.ZERO) throw IllegalArgumentException("MICRO_AST_MIC_INVALID")
      if (!seen.add(code) || astRepository.findByCultureIdAndAntibioticCode(checkNotNull(culture.id), code) != null) {
        throw IllegalArgumentException("MICRO_AST_DUPLICATE")
      }
    }
    return entries.map { entry ->
      astRepository.save(
        MicroAst(
          hospitalId = hospitalId,
          cultureId = checkNotNull(culture.id),
          antibioticCode = entry.antibioticCode.trim().uppercase(),
          antibioticName = entry.antibioticName.trim(),
          susceptibility = entry.susceptibility.trim().uppercase(),
          mic = entry.mic,
          panel = panel.trim(),
        ).also { it.createdBy = actorId },
      )
    }
  }

  @Transactional
  fun finalizeCulture(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): MicroCulture {
    val culture = findCulture(id, hospitalId)
    if (culture.status == "FINAL") throw IllegalStateException("MICRO_CULTURE_ALREADY_FINAL")
    if (culture.organismCode == null) throw IllegalArgumentException("MICRO_ORGANISM_REQUIRED")
    if (astRepository.countByCultureId(checkNotNull(culture.id)) == 0L) throw IllegalArgumentException("MICRO_AST_REQUIRED")
    culture.status = "FINAL"
    culture.finalizedAt = Instant.now()
    culture.finalizedBy = actorId
    culture.updatedBy = actorId
    return cultureRepository.save(culture)
  }

  @Transactional(readOnly = true)
  fun cultures(
    hospitalId: UUID,
    status: String?,
  ): List<MicroCulture> =
    if (status.isNullOrBlank()) {
      cultureRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
    } else {
      cultureRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status.trim().uppercase())
    }

  @Transactional(readOnly = true)
  fun cultureDetail(
    id: UUID,
    hospitalId: UUID,
  ): Map<String, Any?> {
    val culture = findCulture(id, hospitalId)
    return mapOf(
      "culture" to culture,
      "ast" to astRepository.findByCultureIdOrderByAntibioticNameAsc(checkNotNull(culture.id)),
    )
  }

  @Transactional(readOnly = true)
  fun antibiogram(
    hospitalId: UUID,
    organismCode: String,
    from: LocalDate?,
    to: LocalDate?,
  ): Map<String, Any?> {
    if (organismCode.isBlank()) throw IllegalArgumentException("MICRO_ORGANISM_REQUIRED")
    val end = (to ?: LocalDate.now()).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
    val start = (from ?: (to ?: LocalDate.now()).minusDays(365)).atStartOfDay().toInstant(ZoneOffset.UTC)
    if (start.isAfter(end)) throw IllegalArgumentException("MICRO_ANTIBIOGRAM_RANGE_INVALID")
    val cultures =
      cultureRepository.findByHospitalIdAndOrganismCodeAndStatusAndFinalizedAtBetween(
        hospitalId,
        organismCode.trim().uppercase(),
        "FINAL",
        start,
        end,
      )
    val astRows = astRepository.findByCultureIdInOrderByAntibioticNameAsc(cultures.mapNotNull { it.id })
    val perAntibiotic =
      astRows.groupBy { it.antibioticCode }.map { (code, rows) ->
        val susceptible = rows.count { it.susceptibility == "S" }
        val intermediate = rows.count { it.susceptibility == "I" }
        val resistant = rows.count { it.susceptibility == "R" }
        mapOf(
          "antibioticCode" to code,
          "antibioticName" to rows.first().antibioticName,
          "isolates" to rows.size,
          "susceptible" to susceptible,
          "intermediate" to intermediate,
          "resistant" to resistant,
          "susceptiblePct" to
            BigDecimal
              .valueOf(susceptible.toLong() * 100)
              .divide(BigDecimal.valueOf(rows.size.toLong()), 2, RoundingMode.HALF_UP),
        )
      }
    return mapOf(
      "organismCode" to organismCode.trim().uppercase(),
      "from" to start.atZone(ZoneOffset.UTC).toLocalDate(),
      "to" to end.atZone(ZoneOffset.UTC).toLocalDate(),
      "isolates" to cultures.size,
      "antibiotics" to perAntibiotic,
    )
  }

  // -- histopathology / cytology -------------------------------------------

  @Transactional
  fun accessionSpecimen(
    hospitalId: UUID,
    patientId: UUID,
    specimenType: String,
    site: String?,
    actorId: UUID?,
  ): PathSpecimen {
    val type = specimenType.trim().uppercase()
    if (type !in setOf("HISTO", "CYTO")) throw IllegalArgumentException("PATH_SPECIMEN_TYPE_INVALID")
    return specimenRepository.save(
      PathSpecimen(
        hospitalId = hospitalId,
        patientId = patientId,
        accessionNo = generateAccessionNo(),
        specimenType = type,
        site = site?.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun gross(
    id: UUID,
    hospitalId: UUID,
    description: String,
    actorId: UUID?,
  ): PathSpecimen {
    if (description.isBlank()) throw IllegalArgumentException("PATH_GROSS_REQUIRED")
    val specimen = findSpecimen(id, hospitalId)
    if (specimen.status != "ACCESSIONED") throw IllegalStateException("PATH_ALREADY_GROSSED")
    specimen.grossDescription = description.trim()
    specimen.grossedBy = actorId
    specimen.grossedAt = Instant.now()
    specimen.status = "GROSSED"
    specimen.updatedBy = actorId
    return specimenRepository.save(specimen)
  }

  @Transactional
  fun addSlide(
    id: UUID,
    hospitalId: UUID,
    stain: String,
    actorId: UUID?,
  ): PathSlide {
    if (stain.isBlank()) throw IllegalArgumentException("PATH_SLIDE_STAIN_REQUIRED")
    val specimen = findSpecimen(id, hospitalId)
    if (specimen.status == "SIGNED_OUT") throw IllegalStateException("PATH_SPECIMEN_SIGNED_OUT")
    // A slide added after the set was complete re-opens the specimen until every slide is ready again.
    if (specimen.status == "SLIDES_READY") {
      specimen.status = "GROSSED"
      specimen.updatedBy = actorId
    }
    val next = (slideRepository.countBySpecimenId(checkNotNull(specimen.id)) + 1).toInt()
    return slideRepository.save(
      PathSlide(
        hospitalId = hospitalId,
        specimenId = checkNotNull(specimen.id),
        slideNo = next,
        stain = stain.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun markSlideReady(
    specimenId: UUID,
    slideId: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): Map<String, Any?> {
    val specimen = findSpecimen(specimenId, hospitalId)
    if (specimen.status == "SIGNED_OUT") throw IllegalStateException("PATH_SPECIMEN_SIGNED_OUT")
    val slide = slideRepository.findByIdAndSpecimenId(slideId, checkNotNull(specimen.id)) ?: throw IllegalArgumentException("PATH_SLIDE_NOT_FOUND")
    if (slide.status == "READY") throw IllegalStateException("PATH_SLIDE_ALREADY_READY")
    slide.status = "READY"
    slide.readyAt = Instant.now()
    slide.updatedBy = actorId
    slideRepository.save(slide)
    val slides = slideRepository.findBySpecimenIdOrderBySlideNoAsc(checkNotNull(specimen.id))
    if (specimen.status == "GROSSED" && slides.isNotEmpty() && slides.all { it.status == "READY" }) {
      specimen.status = "SLIDES_READY"
      specimen.updatedBy = actorId
      specimenRepository.save(specimen)
    }
    return mapOf("slide" to slide, "specimenStatus" to specimen.status)
  }

  @Transactional
  fun signOut(
    id: UUID,
    hospitalId: UUID,
    diagnosisCode: String,
    diagnosisText: String,
    actorId: UUID?,
  ): PathSpecimen {
    if (diagnosisCode.isBlank() || diagnosisText.isBlank()) throw IllegalArgumentException("PATH_DIAGNOSIS_REQUIRED")
    val specimen = findSpecimen(id, hospitalId)
    if (specimen.status == "SIGNED_OUT") throw IllegalStateException("PATH_ALREADY_SIGNED_OUT")
    if (specimen.status != "SLIDES_READY") throw IllegalArgumentException("PATH_SLIDES_NOT_READY")
    specimen.diagnosisCode = diagnosisCode.trim()
    specimen.diagnosisText = diagnosisText.trim()
    specimen.signedBy = actorId
    specimen.signedAt = Instant.now()
    specimen.status = "SIGNED_OUT"
    specimen.updatedBy = actorId
    return specimenRepository.save(specimen)
  }

  @Transactional(readOnly = true)
  fun specimens(hospitalId: UUID): List<PathSpecimen> = specimenRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional(readOnly = true)
  fun specimenDetail(
    id: UUID,
    hospitalId: UUID,
  ): Map<String, Any?> {
    val specimen = findSpecimen(id, hospitalId)
    return mapOf(
      "specimen" to specimen,
      "slides" to slideRepository.findBySpecimenIdOrderBySlideNoAsc(checkNotNull(specimen.id)),
    )
  }

  private fun findCulture(
    id: UUID,
    hospitalId: UUID,
  ): MicroCulture = cultureRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("MICRO_CULTURE_NOT_FOUND")

  private fun findSpecimen(
    id: UUID,
    hospitalId: UUID,
  ): PathSpecimen = specimenRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("PATH_SPECIMEN_NOT_FOUND")

  private fun generateAccessionNo(): String {
    // shortcut: max+1 accession, upgrade to a DB sequence when pathology volume needs concurrent accessioning.
    val latest = specimenRepository.findTopByOrderByAccessionNoDesc()
    val next =
      latest
        ?.accessionNo
        ?.split("-")
        ?.lastOrNull()
        ?.toIntOrNull()
        ?.plus(1) ?: 1
    return String.format("PTH-%d-%04d", LocalDate.now().year, next)
  }
}
