package com.squamed.core.qms.service

import com.squamed.core.qms.domain.Capa
import com.squamed.core.qms.domain.HandHygiene
import com.squamed.core.qms.domain.InfectionCase
import com.squamed.core.qms.domain.LitigationHold
import com.squamed.core.qms.domain.Outbreak
import com.squamed.core.qms.domain.QmsAudit
import com.squamed.core.qms.domain.QmsIndicator
import com.squamed.core.qms.domain.QmsRisk
import com.squamed.core.qms.repository.CapaRepository
import com.squamed.core.qms.repository.HandHygieneRepository
import com.squamed.core.qms.repository.InfectionCaseRepository
import com.squamed.core.qms.repository.LitigationHoldRepository
import com.squamed.core.qms.repository.OutbreakRepository
import com.squamed.core.qms.repository.QmsAuditRepository
import com.squamed.core.qms.repository.QmsIndicatorRepository
import com.squamed.core.qms.repository.QmsRiskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class QmsService(
  private val indicatorRepository: QmsIndicatorRepository,
  private val capaRepository: CapaRepository,
  private val auditRepository: QmsAuditRepository,
  private val riskRepository: QmsRiskRepository,
  private val infectionRepository: InfectionCaseRepository,
  private val outbreakRepository: OutbreakRepository,
  private val handHygieneRepository: HandHygieneRepository,
  private val holdRepository: LitigationHoldRepository,
) {
  @Transactional
  fun upsertIndicator(
    hospitalId: UUID,
    code: String,
    name: String,
    target: BigDecimal,
    actual: BigDecimal,
    period: String,
    actorId: UUID?,
  ): QmsIndicator {
    if (code.isBlank() || name.isBlank() || period.isBlank()) throw IllegalArgumentException("INDICATOR_FIELDS_REQUIRED")
    val indicator =
      indicatorRepository.findByHospitalIdAndCodeAndPeriod(hospitalId, code.trim().uppercase(), period.trim())
        ?: QmsIndicator(hospitalId = hospitalId, code = code.trim().uppercase(), name = name.trim(), period = period.trim())
          .also { it.createdBy = actorId }
    indicator.name = name.trim()
    indicator.target = target
    indicator.actual = actual
    indicator.status = if (actual.compareTo(target) >= 0) "ON_TRACK" else "OFF_TRACK"
    indicator.updatedBy = actorId
    return indicatorRepository.save(indicator)
  }

  @Transactional(readOnly = true)
  fun indicators(hospitalId: UUID): List<QmsIndicator> = indicatorRepository.findByHospitalIdOrderByCodeAsc(hospitalId)

  @Transactional
  fun createCapa(
    hospitalId: UUID,
    code: String,
    title: String,
    rootCause: String?,
    action: String,
    dueOn: LocalDate?,
    actorId: UUID?,
  ): Capa {
    if (code.isBlank() || title.isBlank() || action.isBlank()) throw IllegalArgumentException("CAPA_FIELDS_REQUIRED")
    return capaRepository.save(
      Capa(
        hospitalId = hospitalId,
        code = code.trim().uppercase(),
        title = title.trim(),
        rootCause = rootCause,
        action = action.trim(),
        dueOn = dueOn,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun closeCapa(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): Capa {
    val capa = capaRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("CAPA_NOT_FOUND")
    if (capa.status == "CLOSED") throw IllegalStateException("CAPA_ALREADY_CLOSED")
    capa.status = "CLOSED"
    capa.closedAt = Instant.now()
    capa.updatedBy = actorId
    return capaRepository.save(capa)
  }

  @Transactional(readOnly = true)
  fun capas(
    hospitalId: UUID,
    status: String?,
  ): List<Capa> =
    if (status.isNullOrBlank()) {
      capaRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)
    } else {
      capaRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status.uppercase())
    }

  @Transactional
  fun createAudit(
    hospitalId: UUID,
    title: String,
    scheduledOn: LocalDate,
    actorId: UUID?,
  ): QmsAudit {
    if (title.isBlank()) throw IllegalArgumentException("AUDIT_TITLE_REQUIRED")
    return auditRepository.save(
      QmsAudit(hospitalId = hospitalId, title = title.trim(), scheduledOn = scheduledOn).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun completeAudit(
    id: UUID,
    hospitalId: UUID,
    findings: String?,
    actorId: UUID?,
  ): QmsAudit {
    val audit = auditRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("AUDIT_NOT_FOUND")
    if (audit.status == "COMPLETED") throw IllegalStateException("AUDIT_ALREADY_COMPLETED")
    audit.status = "COMPLETED"
    audit.findings = findings
    audit.completedAt = Instant.now()
    audit.updatedBy = actorId
    return auditRepository.save(audit)
  }

  @Transactional(readOnly = true)
  fun audits(hospitalId: UUID): List<QmsAudit> = auditRepository.findByHospitalIdOrderByScheduledOnDesc(hospitalId)

  @Transactional
  fun createRisk(
    hospitalId: UUID,
    title: String,
    likelihood: Int,
    impact: Int,
    owner: String?,
    actorId: UUID?,
  ): QmsRisk {
    if (title.isBlank()) throw IllegalArgumentException("RISK_TITLE_REQUIRED")
    if (likelihood !in 1..5 || impact !in 1..5) throw IllegalArgumentException("RISK_SCORE_RANGE_INVALID")
    return riskRepository.save(
      QmsRisk(
        hospitalId = hospitalId,
        title = title.trim(),
        likelihood = likelihood,
        impact = impact,
        score = likelihood * impact,
        owner = owner,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional(readOnly = true)
  fun risks(hospitalId: UUID): List<QmsRisk> = riskRepository.findByHospitalIdOrderByScoreDesc(hospitalId)

  @Transactional
  fun recordInfection(
    hospitalId: UUID,
    patientId: UUID,
    infectionType: String,
    organism: String?,
    identifiedOn: LocalDate,
    actorId: UUID?,
  ): InfectionCase {
    val type = infectionType.trim().uppercase()
    if (type !in setOf("CLABSI", "CAUTI", "VAP", "SSI", "OTHER")) throw IllegalArgumentException("INFECTION_TYPE_INVALID")
    return infectionRepository.save(
      InfectionCase(
        hospitalId = hospitalId,
        patientId = patientId,
        infectionType = type,
        organism = organism,
        identifiedOn = identifiedOn,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun resolveInfection(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): InfectionCase {
    val case = infectionRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("INFECTION_NOT_FOUND")
    if (case.status == "RESOLVED") throw IllegalStateException("INFECTION_ALREADY_RESOLVED")
    case.status = "RESOLVED"
    case.resolvedAt = Instant.now()
    case.updatedBy = actorId
    return infectionRepository.save(case)
  }

  @Transactional(readOnly = true)
  fun infections(
    hospitalId: UUID,
    status: String?,
  ): List<InfectionCase> =
    if (status.isNullOrBlank()) {
      infectionRepository.findByHospitalIdOrderByIdentifiedOnDesc(hospitalId)
    } else {
      infectionRepository.findByHospitalIdAndStatus(hospitalId, status.uppercase())
    }

  @Transactional
  fun declareOutbreak(
    hospitalId: UUID,
    code: String,
    pathogen: String,
    actorId: UUID?,
  ): Outbreak {
    if (code.isBlank() || pathogen.isBlank()) throw IllegalArgumentException("OUTBREAK_FIELDS_REQUIRED")
    if (outbreakRepository.findByHospitalIdAndCode(hospitalId, code.trim().uppercase()) != null) throw IllegalArgumentException("OUTBREAK_CODE_EXISTS")
    return outbreakRepository.save(
      Outbreak(hospitalId = hospitalId, code = code.trim().uppercase(), pathogen = pathogen.trim()).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun closeOutbreak(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): Outbreak {
    val outbreak = outbreakRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("OUTBREAK_NOT_FOUND")
    if (outbreak.status == "CLOSED") throw IllegalStateException("OUTBREAK_ALREADY_CLOSED")
    outbreak.status = "CLOSED"
    outbreak.closedAt = Instant.now()
    outbreak.updatedBy = actorId
    return outbreakRepository.save(outbreak)
  }

  @Transactional(readOnly = true)
  fun outbreaks(
    hospitalId: UUID,
    status: String?,
  ): List<Outbreak> =
    if (status.isNullOrBlank()) {
      outbreakRepository.findByHospitalIdAndStatus(hospitalId, "ACTIVE")
    } else {
      outbreakRepository.findByHospitalIdAndStatus(hospitalId, status.uppercase())
    }

  @Transactional
  fun recordHandHygiene(
    hospitalId: UUID,
    wardId: UUID?,
    period: String,
    opportunities: Int,
    compliant: Int,
    actorId: UUID?,
  ): HandHygiene {
    if (period.isBlank()) throw IllegalArgumentException("HAND_HYGIENE_PERIOD_REQUIRED")
    if (opportunities <= 0 || compliant < 0 || compliant > opportunities) throw IllegalArgumentException("HAND_HYGIENE_COUNTS_INVALID")
    val row =
      handHygieneRepository.findByHospitalIdAndWardIdAndPeriod(hospitalId, wardId, period.trim())
        ?: HandHygiene(hospitalId = hospitalId, wardId = wardId, period = period.trim(), opportunities = opportunities, compliant = compliant)
          .also { it.createdBy = actorId }
    row.opportunities = opportunities
    row.compliant = compliant
    row.updatedBy = actorId
    return handHygieneRepository.save(row)
  }

  @Transactional(readOnly = true)
  fun handHygiene(hospitalId: UUID): List<Map<String, Any?>> =
    handHygieneRepository.findByHospitalIdOrderByPeriodDesc(hospitalId).map {
      mapOf(
        "id" to it.id,
        "wardId" to it.wardId,
        "period" to it.period,
        "opportunities" to it.opportunities,
        "compliant" to it.compliant,
        "compliancePct" to
          BigDecimal
            .valueOf(it.compliant.toLong() * 100)
            .divide(BigDecimal.valueOf(it.opportunities.toLong()), 2, RoundingMode.HALF_UP),
      )
    }

  @Transactional
  fun placeHold(
    hospitalId: UUID,
    caseRef: String,
    subjectType: String,
    subjectId: UUID,
    reason: String,
    actorId: UUID?,
  ): LitigationHold {
    if (caseRef.isBlank() || reason.isBlank()) throw IllegalArgumentException("LEGAL_HOLD_FIELDS_REQUIRED")
    return holdRepository.save(
      LitigationHold(
        hospitalId = hospitalId,
        caseRef = caseRef.trim(),
        subjectType = subjectType.trim(),
        subjectId = subjectId,
        reason = reason.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun releaseHold(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): LitigationHold {
    val hold = holdRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("LEGAL_HOLD_NOT_FOUND")
    if (!hold.active) throw IllegalStateException("LEGAL_HOLD_ALREADY_RELEASED")
    hold.active = false
    hold.releasedAt = Instant.now()
    hold.updatedBy = actorId
    return holdRepository.save(hold)
  }

  @Transactional(readOnly = true)
  fun holds(hospitalId: UUID): List<LitigationHold> = holdRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional(readOnly = true)
  fun summary(hospitalId: UUID): Map<String, Any?> =
    mapOf(
      "indicatorsOffTrack" to indicators(hospitalId).count { it.status == "OFF_TRACK" },
      "openCapas" to capas(hospitalId, "OPEN").size,
      "plannedAudits" to audits(hospitalId).count { it.status == "PLANNED" },
      "highRisks" to risks(hospitalId).count { it.score >= 15 },
      "activeInfections" to infections(hospitalId, "ACTIVE").size,
      "activeOutbreaks" to outbreaks(hospitalId, "ACTIVE").size,
      "activeHolds" to holds(hospitalId).count { it.active },
    )
}
