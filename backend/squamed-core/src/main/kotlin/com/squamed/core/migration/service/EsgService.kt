package com.squamed.core.migration.service

import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.migration.domain.EsgMetric
import com.squamed.core.migration.repository.EsgMetricRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

// 07.10 ESG: energy/water/waste/carbon metrics per period with a simple intensity view.
@Service
class EsgService(
  private val metricRepository: EsgMetricRepository,
  private val admissionRepository: AdmissionRepository,
) {
  companion object {
    private val METRICS = setOf("ENERGY_KWH", "WATER_L", "WASTE_KG", "CARBON_KG")
    private val PERIOD = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
  }

  @Transactional
  fun recordMetric(
    hospitalId: UUID,
    period: String,
    metricType: String,
    value: BigDecimal,
    actorId: UUID?,
  ): EsgMetric {
    if (!PERIOD.matches(period)) throw IllegalArgumentException("ESG_PERIOD_INVALID")
    val type = metricType.trim().uppercase()
    if (type !in METRICS) throw IllegalArgumentException("ESG_METRIC_INVALID")
    if (value.signum() < 0) throw IllegalArgumentException("ESG_VALUE_INVALID")
    val row =
      metricRepository.findByHospitalIdAndPeriodAndMetricType(hospitalId, period, type)
        ?: EsgMetric(hospitalId = hospitalId, period = period, metricType = type, value = value)
          .also { it.createdBy = actorId }
    row.value = value
    row.updatedBy = actorId
    return metricRepository.save(row)
  }

  @Transactional(readOnly = true)
  fun metrics(
    hospitalId: UUID,
    period: String,
  ): List<EsgMetric> = metricRepository.findByHospitalIdAndPeriodOrderByMetricTypeAsc(hospitalId, period)

  @Transactional(readOnly = true)
  fun summary(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    if (!PERIOD.matches(period)) throw IllegalArgumentException("ESG_PERIOD_INVALID")
    val rows = metricRepository.findByHospitalIdAndPeriodOrderByMetricTypeAsc(hospitalId, period)
    val totals = METRICS.associateWith { type -> rows.firstOrNull { it.metricType == type }?.value ?: BigDecimal.ZERO }
    val start = LocalDate.parse("$period-01")
    val from = start.atStartOfDay().toInstant(ZoneOffset.UTC)
    val to = start.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val admissions =
      admissionRepository
        .findAllByHospital(hospitalId)
        .count {
          val admittedAt = it.admittedAt
          admittedAt != null && !admittedAt.isBefore(from) && admittedAt.isBefore(to)
        }
    val carbon = totals.getValue("CARBON_KG")
    return mapOf(
      "period" to period,
      "totals" to totals,
      "admissions" to admissions,
      "carbonPerAdmissionKg" to
        if (admissions == 0) {
          null
        } else {
          carbon.divide(BigDecimal.valueOf(admissions.toLong()), 2, RoundingMode.HALF_UP)
        },
    )
  }
}
