package com.squamed.core.bi.service

import com.squamed.core.bi.domain.MartSync
import com.squamed.core.bi.repository.MartSyncRepository
import com.squamed.core.billing.repository.InvoiceRepository
import com.squamed.core.events.EventOutboxRepository
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.laboratory.repository.LabOrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class KpiDefinition(
  val code: String,
  val name: String,
  val unit: String,
)

// 06.06 BI: ClickHouse event mart + KPI snapshots, role command centres and exports.
@Service
class BiService(
  private val martRepository: MartSyncRepository,
  private val hospitalRepository: HospitalRepository,
  private val outboxRepository: EventOutboxRepository,
  private val admissionRepository: AdmissionRepository,
  private val invoiceRepository: InvoiceRepository,
  private val labOrderRepository: LabOrderRepository,
  @Value("\${squamed.bi.clickhouse-url:jdbc:clickhouse://localhost:8123/default}") private val clickhouseUrl: String,
) {
  companion object {
    val KPI_DICTIONARY =
      listOf(
        KpiDefinition("ADMISSIONS", "Admissions in period", "count"),
        KpiDefinition("REVENUE_QAR", "Invoiced revenue in period", "QAR"),
        KpiDefinition("LAB_ORDERS", "Lab orders in period", "count"),
      )

    private val ROLES =
      mapOf(
        "CEO" to listOf("ADMISSIONS", "REVENUE_QAR", "LAB_ORDERS"),
        "COO" to listOf("ADMISSIONS", "LAB_ORDERS"),
        "CFO" to listOf("REVENUE_QAR"),
        "CMO" to listOf("ADMISSIONS", "LAB_ORDERS"),
        "NURSING" to listOf("ADMISSIONS"),
        "FACILITIES" to listOf("ADMISSIONS"),
      )

    private val PERIOD = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
  }

  @Transactional
  fun sync(
    hospitalId: UUID,
    actorId: UUID?,
  ): Map<String, Any?> {
    val mart = martRepository.findByHospitalId(hospitalId) ?: MartSync(hospitalId = hospitalId).also { it.createdBy = actorId }
    val cursor = mart.lastEventAt ?: Instant.EPOCH
    val events = outboxRepository.findSentForHospitalAfter(hospitalId, cursor)
    val period = LocalDate.now().toString().substring(0, 7)
    val values = operationalKpis(hospitalId, period)
    connection().use { conn ->
      ensureSchema(conn)
      if (events.isNotEmpty()) {
        conn
          .prepareStatement(
            "INSERT INTO squamed_bi.events (event_id, event_type, hospital_id, occurred_at, payload) VALUES (?, ?, ?, ?, ?)",
          ).use { ps ->
            events.forEach { event ->
              ps.setString(1, event.eventId.toString())
              ps.setString(2, event.eventType)
              ps.setString(3, hospitalId.toString())
              ps.setTimestamp(4, Timestamp.from(event.occurredAt))
              ps.setString(5, event.payload.toString() ?: "{}")
              ps.addBatch()
            }
            ps.executeBatch()
          }
      }
      conn
        .prepareStatement(
          "INSERT INTO squamed_bi.kpi_snapshot (hospital_id, period, metric, value, generated_at) VALUES (?, ?, ?, ?, ?)",
        ).use { ps ->
          values.forEach { (metric, value) ->
            ps.setString(1, hospitalId.toString())
            ps.setString(2, period)
            ps.setString(3, metric)
            ps.setDouble(4, value.toDouble())
            ps.setTimestamp(5, Timestamp.from(Instant.now()))
            ps.addBatch()
          }
          ps.executeBatch()
        }
    }
    mart.lastEventAt = events.maxOfOrNull { it.occurredAt } ?: mart.lastEventAt
    mart.lastSyncedAt = Instant.now()
    mart.eventsSynced = mart.eventsSynced + events.size
    mart.status = "OK"
    mart.updatedBy = actorId
    martRepository.save(mart)
    return mapOf(
      "hospitalId" to hospitalId,
      "eventsSynced" to events.size,
      "period" to period,
      "kpis" to values,
      "syncedAt" to mart.lastSyncedAt,
    )
  }

  @Transactional(readOnly = true)
  fun kpis(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    requirePeriod(period)
    val values = clickhouseKpis(hospitalId, period)
    return mapOf(
      "period" to period,
      "kpis" to
        KPI_DICTIONARY.map {
          mapOf(
            "code" to it.code,
            "name" to it.name,
            "unit" to it.unit,
            "value" to (values[it.code] ?: BigDecimal.ZERO),
          )
        },
    )
  }

  @Transactional(readOnly = true)
  fun dashboard(
    hospitalId: UUID,
    role: String,
    period: String,
  ): Map<String, Any?> {
    requirePeriod(period)
    val codes = ROLES[role.trim().uppercase()] ?: throw IllegalArgumentException("BI_ROLE_INVALID")
    val values = clickhouseKpis(hospitalId, period)
    return mapOf(
      "role" to role.trim().uppercase(),
      "period" to period,
      "metrics" to
        codes.map { code ->
          val definition = KPI_DICTIONARY.first { it.code == code }
          mapOf(
            "code" to code,
            "name" to definition.name,
            "unit" to definition.unit,
            "value" to (values[code] ?: BigDecimal.ZERO),
          )
        },
    )
  }

  @Transactional(readOnly = true)
  fun exportCsv(
    hospitalId: UUID,
    period: String,
  ): String {
    requirePeriod(period)
    val values = clickhouseKpis(hospitalId, period)
    return buildString {
      appendLine("metric,value,unit")
      KPI_DICTIONARY.forEach {
        appendLine("${it.code},${values[it.code] ?: BigDecimal.ZERO},${it.unit}")
      }
    }
  }

  @Transactional(readOnly = true)
  fun reconcile(
    hospitalId: UUID,
    period: String,
  ): Map<String, Any?> {
    requirePeriod(period)
    val martValues = clickhouseKpis(hospitalId, period)
    val live = operationalKpis(hospitalId, period)
    val mismatches =
      live.mapNotNull { (metric, value) ->
        val martValue = martValues[metric] ?: BigDecimal.ZERO
        if ((martValue - value).abs() > BigDecimal("0.01")) "$metric: mart=$martValue live=$value" else null
      }
    return mapOf(
      "period" to period,
      "mart" to martValues,
      "live" to live,
      "mismatches" to mismatches,
      "reconciled" to mismatches.isEmpty(),
    )
  }

  // Nightly dimensional sync: refresh every hospital's KPIs after the day rolls.
  @Scheduled(cron = "0 0 2 * * *")
  fun nightlySync() {
    hospitalRepository.findAll().forEach { hospital ->
      hospital.id?.let { id ->
        runCatching { sync(id, null) }
      }
    }
  }

  private fun operationalKpis(
    hospitalId: UUID,
    period: String,
  ): Map<String, BigDecimal> {
    val (from, to) = periodRange(period)
    val admissions =
      admissionRepository
        .findAllByHospital(hospitalId)
        .count {
          val admittedAt = it.admittedAt
          admittedAt != null && !admittedAt.isBefore(from) && admittedAt.isBefore(to)
        }
    val revenue =
      invoiceRepository
        .findAllForHospital(hospitalId)
        .filter { it.status != "cancelled" && !it.issuedAt.isBefore(from) && it.issuedAt.isBefore(to) }
        .fold(BigDecimal.ZERO) { acc, invoice -> acc + invoice.totalQar }
    val labOrders =
      labOrderRepository
        .findAllForHospital(hospitalId)
        .count {
          val orderedAt = it.orderedAt
          orderedAt != null && !orderedAt.isBefore(from) && orderedAt.isBefore(to)
        }
    return mapOf(
      "ADMISSIONS" to BigDecimal.valueOf(admissions.toLong()),
      "REVENUE_QAR" to revenue.setScale(2, RoundingMode.HALF_UP),
      "LAB_ORDERS" to BigDecimal.valueOf(labOrders.toLong()),
    )
  }

  private fun clickhouseKpis(
    hospitalId: UUID,
    period: String,
  ): Map<String, BigDecimal> {
    connection().use { conn ->
      ensureSchema(conn)
      conn
        .prepareStatement(
          "SELECT metric, argMax(value, generated_at) AS v FROM squamed_bi.kpi_snapshot " +
            "WHERE hospital_id = ? AND period = ? GROUP BY metric",
        ).use { ps ->
          ps.setString(1, hospitalId.toString())
          ps.setString(2, period)
          ps.executeQuery().use { rs ->
            val out = mutableMapOf<String, BigDecimal>()
            while (rs.next()) {
              out[rs.getString(1)] = BigDecimal.valueOf(rs.getDouble(2)).setScale(2, RoundingMode.HALF_UP)
            }
            return out
          }
        }
    }
  }

  private fun connection(): Connection {
    Class.forName("com.clickhouse.jdbc.ClickHouseDriver")
    var last: Exception? = null
    repeat(10) {
      try {
        return DriverManager.getConnection(clickhouseUrl, "default", "")
      } catch (ex: Exception) {
        last = ex
        Thread.sleep(2000)
      }
    }
    throw IllegalStateException("BI_UNAVAILABLE", last)
  }

  private fun ensureSchema(conn: Connection) {
    conn.createStatement().use { st ->
      st.execute("CREATE DATABASE IF NOT EXISTS squamed_bi")
      st.execute(
        "CREATE TABLE IF NOT EXISTS squamed_bi.events (" +
          "event_id UUID, event_type String, hospital_id UUID, occurred_at DateTime64(3), payload String" +
          ") ENGINE = MergeTree ORDER BY (hospital_id, occurred_at)",
      )
      st.execute(
        "CREATE TABLE IF NOT EXISTS squamed_bi.kpi_snapshot (" +
          "hospital_id UUID, period String, metric String, value Float64, generated_at DateTime64(3)" +
          ") ENGINE = ReplacingMergeTree(generated_at) ORDER BY (hospital_id, period, metric)",
      )
    }
  }

  private fun periodRange(period: String): Pair<Instant, Instant> {
    val start = LocalDate.parse("$period-01")
    return start.atStartOfDay().toInstant(ZoneOffset.UTC) to start.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC)
  }

  private fun requirePeriod(period: String) {
    if (!PERIOD.matches(period)) throw IllegalArgumentException("BI_PERIOD_INVALID")
  }
}
