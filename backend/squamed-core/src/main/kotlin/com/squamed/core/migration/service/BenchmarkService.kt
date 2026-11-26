package com.squamed.core.migration.service

import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.migration.domain.BenchmarkParticipation
import com.squamed.core.migration.repository.BenchmarkParticipationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

// 07.10 benchmarking: consent-gated, anonymized peer comparison inside a hospital group.
@Service
class BenchmarkService(
  private val participationRepository: BenchmarkParticipationRepository,
  private val hospitalRepository: HospitalRepository,
  private val admissionRepository: AdmissionRepository,
  private val invoiceRepository: InvoiceRepository,
) {
  companion object {
    private val METRICS = setOf("ADMISSIONS", "REVENUE_QAR")
    private val PERIOD = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
  }

  @Transactional
  fun setParticipation(
    hospitalId: UUID,
    enabled: Boolean,
    actorId: UUID?,
  ): BenchmarkParticipation {
    val row =
      participationRepository.findByHospitalId(hospitalId)
        ?: BenchmarkParticipation(hospitalId = hospitalId).also { it.createdBy = actorId }
    row.enabled = enabled
    row.updatedBy = actorId
    return participationRepository.save(row)
  }

  @Transactional(readOnly = true)
  fun participation(hospitalId: UUID): BenchmarkParticipation? = participationRepository.findByHospitalId(hospitalId)

  @Transactional(readOnly = true)
  fun summary(
    hospitalId: UUID,
    metric: String,
    period: String,
  ): Map<String, Any?> {
    val key = metric.trim().uppercase()
    if (key !in METRICS) throw IllegalArgumentException("BENCHMARK_METRIC_INVALID")
    if (!PERIOD.matches(period)) throw IllegalArgumentException("BENCHMARK_PERIOD_INVALID")
    val own = participationRepository.findByHospitalId(hospitalId)
    if (own?.enabled != true) throw IllegalArgumentException("BENCHMARK_NOT_PARTICIPATING")
    val groupId = hospitalRepository.findById(hospitalId).orElse(null)?.groupId
    val peerIds =
      if (groupId == null) {
        listOf(hospitalId)
      } else {
        hospitalRepository.findByGroupId(groupId).mapNotNull { it.id }
      }
    val participating = participationRepository.findByHospitalIdInAndEnabledTrue(peerIds).map { it.hospitalId }.toSet()
    if (hospitalId !in participating) throw IllegalArgumentException("BENCHMARK_NOT_PARTICIPATING")
    val values = participating.map { valueFor(it, key, period) }.sorted()
    val ownValue = valueFor(hospitalId, key, period)
    return mapOf(
      "metric" to key,
      "period" to period,
      "ownValue" to ownValue,
      "peerCount" to values.size,
      "median" to percentile(values, 0.50),
      "p25" to percentile(values, 0.25),
      "p75" to percentile(values, 0.75),
    )
  }

  private fun valueFor(
    hospitalId: UUID,
    metric: String,
    period: String,
  ): BigDecimal {
    val start = LocalDate.parse("$period-01")
    val end = start.plusMonths(1).minusDays(1)
    return when (metric) {
      "ADMISSIONS" -> {
        val from = start.atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        BigDecimal.valueOf(
          admissionRepository
            .findAllByHospital(hospitalId)
            .count {
              val admittedAt = it.admittedAt
              admittedAt != null && !admittedAt.isBefore(from) && admittedAt.isBefore(to)
            }.toLong(),
        )
      }

      else -> {
        val from = start.atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        invoiceRepository
          .findAllForHospital(hospitalId)
          .filter { it.status != "cancelled" && !it.issuedAt.isBefore(from) && it.issuedAt.isBefore(to) }
          .fold(BigDecimal.ZERO) { acc, invoice -> acc + invoice.totalQar }
          .setScale(2, RoundingMode.HALF_UP)
      }
    }
  }

  // Linear interpolation over the sorted values; a single peer returns its own value.
  private fun percentile(
    sorted: List<BigDecimal>,
    p: Double,
  ): BigDecimal {
    if (sorted.isEmpty()) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
    if (sorted.size == 1) return sorted.first().setScale(2, RoundingMode.HALF_UP)
    val pos = (sorted.size - 1) * p
    val lower = sorted[pos.toInt()]
    val upper = sorted[minOf(pos.toInt() + 1, sorted.size - 1)]
    val fraction = BigDecimal.valueOf(pos - pos.toInt())
    return lower.add(upper.subtract(lower).multiply(fraction)).setScale(2, RoundingMode.HALF_UP)
  }
}
