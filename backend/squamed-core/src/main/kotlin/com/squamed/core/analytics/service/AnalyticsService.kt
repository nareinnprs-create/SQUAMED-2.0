package com.squamed.core.analytics.service

import com.squamed.core.analytics.api.AppointmentStats
import com.squamed.core.analytics.api.BillingStats
import com.squamed.core.analytics.api.EncounterStats
import com.squamed.core.analytics.api.LabStats
import com.squamed.core.analytics.api.NameCount
import com.squamed.core.analytics.api.OverviewStats
import com.squamed.core.analytics.api.PatientsStats
import com.squamed.core.analytics.api.QueueStats
import com.squamed.core.analytics.api.TimePoint
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.HashMap
import java.util.Locale

@Service
@Transactional(readOnly = true)
class AnalyticsService {
  @PersistenceContext
  private lateinit var entityManager: EntityManager

  private val utc = ZoneOffset.UTC

  fun overview(): OverviewStats {
    val todayStart = LocalDate.now(utc).atStartOfDay().toInstant(utc)
    val tomorrowStart =
      LocalDate
        .now(utc)
        .plusDays(1)
        .atStartOfDay()
        .toInstant(utc)

    val totalPatients = count("select count(p) from PatientRecord p where p.recordsStatus != 'duplicate'")
    val patientsToday =
      count(
        "select count(p) from PatientRecord p where p.createdAt >= :from and p.createdAt < :to and p.recordsStatus != 'duplicate'",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val activeEncounters = count("select count(e) from Encounter e where e.status = 'in-progress'")
    val encountersToday =
      count(
        "select count(e) from Encounter e where e.startedAt >= :from and e.startedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val completedEncountersToday =
      count(
        "select count(e) from Encounter e where e.endedAt >= :from and e.endedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val appointmentsToday =
      count(
        "select count(a) from Appointment a where a.scheduledStart >= :from and a.scheduledStart < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val pendingAppointments = count("select count(a) from Appointment a where a.status in ('scheduled', 'confirmed', 'checked_in')")
    val queueWaiting = count("select count(q) from QueueEntry q where q.status in ('waiting', 'called')")
    val avgWait =
      nullableDouble(
        "select avg(q.estimatedWaitMinutes) from QueueEntry q where q.queueDate = :qd and q.estimatedWaitMinutes is not null",
        "qd" to LocalDate.now(utc),
      )
    val labOrdersToday =
      count(
        "select count(l) from LabOrder l where l.orderedAt >= :from and l.orderedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val labPending = count("select count(l) from LabOrder l where l.status in ('ordered', 'collected', 'in_progress')")
    val invoicesToday =
      count(
        "select count(i) from Invoice i where i.issuedAt >= :from and i.issuedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val revenueToday =
      sumDouble(
        "select coalesce(sum(i.amountPaidQar), 0) from Invoice i where i.status = 'paid' and i.issuedAt >= :from and i.issuedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val amountDue =
      sumDouble(
        "select coalesce(sum(i.amountDueQar), 0) from Invoice i where i.status in ('issued', 'partially_paid')",
      )
    val dispensationsToday =
      count(
        "select count(d) from Dispensation d where d.dispensedAt >= :from and d.dispensedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )

    return OverviewStats(
      totalPatients = totalPatients,
      patientsToday = patientsToday,
      activeEncounters = activeEncounters,
      encountersToday = encountersToday,
      completedEncountersToday = completedEncountersToday,
      appointmentsToday = appointmentsToday,
      pendingAppointments = pendingAppointments,
      queueWaiting = queueWaiting,
      avgWaitMinutes = avgWait,
      labOrdersToday = labOrdersToday,
      labPending = labPending,
      invoicesToday = invoicesToday,
      revenueTodayQar = revenueToday,
      amountDueQar = amountDue,
      dispensationsToday = dispensationsToday,
    )
  }

  fun patients(): PatientsStats {
    val total = count("select count(p) from PatientRecord p where p.recordsStatus != 'duplicate'")
    return PatientsStats(
      total = total,
      byGender = groupBy("select p.gender, count(p) from PatientRecord p where p.recordsStatus != 'duplicate' group by p.gender"),
      byBloodGroup =
        groupBy(
          "select p.bloodGroup, count(p) from PatientRecord p where p.recordsStatus != 'duplicate' and p.bloodGroup is not null group by p.bloodGroup",
        ),
      registrationsByMonth = monthly(),
      byNationality =
        groupBy(
          "select p.nationality, count(p) from PatientRecord p where p.recordsStatus != 'duplicate' and p.nationality is not null group by p.nationality",
        ),
    )
  }

  fun encounters(): EncounterStats {
    val todayStart = LocalDate.now(utc).atStartOfDay().toInstant(utc)
    val tomorrowStart =
      LocalDate
        .now(utc)
        .plusDays(1)
        .atStartOfDay()
        .toInstant(utc)
    val total = count("select count(e) from Encounter e")
    val today =
      count(
        "select count(e) from Encounter e where e.startedAt >= :from and e.startedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )

    @Suppress("UNCHECKED_CAST")
    val completed =
      entityManager
        .createQuery(
          "select e.startedAt, e.endedAt from Encounter e where e.endedAt is not null and e.startedAt is not null and e.status != 'in-progress'",
        ).resultList as List<Array<Any>>
    val avgDurationMinutes =
      if (completed.isEmpty()) {
        null
      } else {
        val totalMinutes =
          completed.sumOf { row ->
            val start = row[0] as Instant
            val end = row[1] as Instant
            Duration.between(start, end).toSeconds().coerceAtLeast(0) / 60.0
          }
        totalMinutes / completed.size
      }

    return EncounterStats(
      total = total,
      today = today,
      byStatus = groupBy("select e.status, count(e) from Encounter e group by e.status"),
      byType = groupBy("select e.encounterType, count(e) from Encounter e group by e.encounterType"),
      avgDurationMinutes = avgDurationMinutes,
    )
  }

  fun appointments(): AppointmentStats {
    val todayStart = LocalDate.now(utc).atStartOfDay().toInstant(utc)
    val tomorrowStart =
      LocalDate
        .now(utc)
        .plusDays(1)
        .atStartOfDay()
        .toInstant(utc)
    val total = count("select count(a) from Appointment a")
    val today =
      count(
        "select count(a) from Appointment a where a.scheduledStart >= :from and a.scheduledStart < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    return AppointmentStats(
      total = total,
      today = today,
      byStatus = groupBy("select a.status, count(a) from Appointment a group by a.status"),
      byType = groupBy("select a.appointmentType, count(a) from Appointment a group by a.appointmentType"),
    )
  }

  fun queue(): QueueStats {
    val today = LocalDate.now(utc)
    val waiting = count("select count(q) from QueueEntry q where q.status in ('waiting', 'called')")
    val calledToday =
      count(
        "select count(q) from QueueEntry q where q.queueDate = :qd and q.calledAt is not null",
        "qd" to today,
      )
    val completedToday =
      count(
        "select count(q) from QueueEntry q where q.queueDate = :qd and q.completedAt is not null",
        "qd" to today,
      )
    val avgWait =
      nullableDouble(
        "select avg(q.estimatedWaitMinutes) from QueueEntry q where q.queueDate = :qd and q.estimatedWaitMinutes is not null",
        "qd" to today,
      )
    return QueueStats(
      waiting = waiting,
      calledToday = calledToday,
      completedToday = completedToday,
      avgWaitMinutes = avgWait,
      byServicePoint =
        groupBy(
          "select q.servicePointName, count(q) from QueueEntry q where q.queueDate = :qd group by q.servicePointName",
          "qd" to today,
        ),
      byQueueType =
        groupBy(
          "select q.queueType, count(q) from QueueEntry q where q.queueDate = :qd group by q.queueType",
          "qd" to today,
        ),
    )
  }

  fun lab(): LabStats {
    val todayStart = LocalDate.now(utc).atStartOfDay().toInstant(utc)
    val tomorrowStart =
      LocalDate
        .now(utc)
        .plusDays(1)
        .atStartOfDay()
        .toInstant(utc)
    val total = count("select count(l) from LabOrder l")
    val today =
      count(
        "select count(l) from LabOrder l where l.orderedAt >= :from and l.orderedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    val releasedToday =
      count(
        "select count(l) from LabOrder l where l.releasedAt >= :from and l.releasedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )
    return LabStats(
      total = total,
      today = today,
      byStatus = groupBy("select l.status, count(l) from LabOrder l group by l.status"),
      byResultStatus =
        groupBy(
          "select l.resultStatus, count(l) from LabOrder l where l.resultStatus is not null group by l.resultStatus",
        ),
      releasedToday = releasedToday,
    )
  }

  fun billing(): BillingStats {
    val todayStart = LocalDate.now(utc).atStartOfDay().toInstant(utc)
    val tomorrowStart =
      LocalDate
        .now(utc)
        .plusDays(1)
        .atStartOfDay()
        .toInstant(utc)
    val weekStart =
      LocalDate
        .now(utc)
        .minusDays(6)
        .atStartOfDay()
        .toInstant(utc)
    val monthStart =
      LocalDate
        .now(utc)
        .withDayOfMonth(1)
        .atStartOfDay()
        .toInstant(utc)

    fun revenue(from: Instant): Double =
      sumDouble(
        "select coalesce(sum(i.amountPaidQar), 0) from Invoice i where i.status = 'paid' and i.issuedAt >= :from",
        "from" to from,
      )

    val outstanding =
      sumDouble(
        "select coalesce(sum(i.amountDueQar), 0) from Invoice i where i.status in ('issued', 'partially_paid')",
      )
    val chargesToday =
      count(
        "select count(c) from Charge c where c.postedAt >= :from and c.postedAt < :to",
        "from" to todayStart,
        "to" to tomorrowStart,
      )

    return BillingStats(
      revenueTodayQar = revenue(todayStart),
      revenueThisWeekQar = revenue(weekStart),
      revenueThisMonthQar = revenue(monthStart),
      outstandingQar = outstanding,
      byStatus = groupBy("select i.status, count(i) from Invoice i group by i.status"),
      chargesPostedToday = chargesToday,
      chargesByCategory = groupBy("select c.category, count(c) from Charge c where c.category is not null group by c.category"),
    )
  }

  private fun count(
    jpql: String,
    vararg params: Pair<String, Any?>,
  ): Long {
    val q = entityManager.createQuery(jpql, Long::class.java)
    params.forEach { (k, v) -> if (v != null) q.setParameter(k, v) }
    return q.singleResult
  }

  private fun nullableDouble(
    jpql: String,
    vararg params: Pair<String, Any?>,
  ): Double? {
    val q = entityManager.createQuery(jpql)
    params.forEach { (k, v) -> if (v != null) q.setParameter(k, v) }
    val r = q.singleResult ?: return null
    return (r as Number).toDouble()
  }

  private fun sumDouble(
    jpql: String,
    vararg params: Pair<String, Any?>,
  ): Double {
    val q = entityManager.createQuery(jpql)
    params.forEach { (k, v) -> if (v != null) q.setParameter(k, v) }
    val r = q.singleResult ?: return 0.0
    return (r as Number).toDouble()
  }

  @Suppress("UNCHECKED_CAST")
  private fun groupBy(
    jpql: String,
    vararg params: Pair<String, Any?>,
  ): List<NameCount> {
    val q = entityManager.createQuery(jpql)
    params.forEach { (k, v) -> if (v != null) q.setParameter(k, v) }
    val rows = q.resultList as List<Array<Any>>
    return rows
      .mapNotNull { row ->
        val name = (row[0] ?: return@mapNotNull null).toString()
        val count = (row[1] as Number).toLong()
        NameCount(name, count)
      }.sortedByDescending { it.count }
  }

  @Suppress("UNCHECKED_CAST")
  private fun monthly(): List<TimePoint> {
    val rows =
      entityManager
        .createQuery(
          "select p.createdAt from PatientRecord p where p.recordsStatus != 'duplicate'",
        ).resultList as List<Instant>
    val buckets = HashMap<String, Long>()
    val now = LocalDate.now(utc)
    for (i in 5 downTo 0) {
      val month = now.minusMonths(i.toLong())
      buckets[month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.year % 100] = 0
    }
    rows.forEach { t ->
      val at = (t ?: Instant.now()).atZone(utc).toLocalDate()
      if (at.isAfter(now.minusMonths(6))) {
        val key = at.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + at.year % 100
        buckets[key] = (buckets[key] ?: 0) + 1
      }
    }
    return buckets.entries.map { TimePoint(it.key, it.value) }
  }
}
