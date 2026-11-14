package com.squamed.core.receptionqueue.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.receptionqueue.api.BoardEntry
import com.squamed.core.receptionqueue.api.QueueEntryView
import com.squamed.core.receptionqueue.api.QueueStatsView
import com.squamed.core.receptionqueue.api.ServicePointDto
import com.squamed.core.receptionqueue.domain.QueueEntry
import com.squamed.core.receptionqueue.domain.ServicePoint
import com.squamed.core.receptionqueue.repository.QueueEntryRepository
import com.squamed.core.receptionqueue.repository.ServicePointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class QueueService(
  private val queueEntryRepository: QueueEntryRepository,
  private val servicePointRepository: ServicePointRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun addToQueue(
    queueType: String,
    patientId: UUID,
    patientName: String?,
    visitId: UUID?,
    priority: String?,
    servicePointId: String?,
    departmentId: UUID?,
    hospitalId: UUID,
  ): QueueEntryView {
    val today = LocalDate.now()
    val entry =
      queueEntryRepository.save(
        QueueEntry(
          hospitalId = hospitalId,
          queueType = queueType,
          departmentId = departmentId,
          servicePointId = servicePointId,
          visitId = visitId,
          patientId = patientId,
          patientName = patientName,
          tokenNumber = nextToken(queueType, today),
          priority = priority ?: "routine",
          queueDate = today,
        ),
      )
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun call(
    queueType: String,
    tokenNumber: String,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber)
    entry.status = "called"
    entry.calledAt = Instant.now()
    queueEntryRepository.save(entry)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "queue.called",
        occurredAt = Instant.now(),
        source = "queue-service",
        entityType = "queue-entry",
        entityId = entry.id,
        payload =
          mapOf(
            "tokenNumber" to entry.tokenNumber,
            "queueType" to entry.queueType,
          ),
      ),
    )
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun start(
    queueType: String,
    tokenNumber: String,
    servedBy: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber)
    entry.status = "in-progress"
    entry.startedAt = Instant.now()
    entry.servedBy = servedBy ?: entry.servedBy
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun complete(
    queueType: String,
    tokenNumber: String,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber)
    entry.status = "completed"
    entry.completedAt = Instant.now()
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun skip(
    queueType: String,
    tokenNumber: String,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber)
    entry.status = "skipped"
    entry.skippedAt = Instant.now()
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun postpone(
    queueType: String,
    tokenNumber: String,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber)
    entry.status = "postponed"
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun noShow(
    queueType: String,
    tokenNumber: String,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber)
    entry.status = "no-show"
    entry.noShowAt = Instant.now()
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional(readOnly = true)
  fun board(queueType: String): List<BoardEntry> {
    val today = LocalDate.now()
    val statuses = listOf("waiting", "called", "in-progress", "postponed")
    return queueEntryRepository
      .findByQueueTypeAndStatusInAndQueueDate(queueType, statuses, today)
      .sortedWith(compareBy({ statusRank(it.status) }, { priorityRank(it.priority) }, { it.createdAt ?: Instant.EPOCH }))
      .map { BoardEntry.from(it) }
  }

  @Transactional
  fun callNext(queueType: String): BoardEntry {
    val today = LocalDate.now()
    val next =
      queueEntryRepository
        .findByQueueTypeAndStatusAndQueueDate(queueType, "waiting", today)
        .sortedWith(compareBy({ priorityRank(it.priority) }, { it.createdAt ?: Instant.EPOCH }))
        .firstOrNull() ?: throw IllegalStateException("QUEUE_EMPTY")
    next.status = "called"
    next.calledAt = Instant.now()
    queueEntryRepository.save(next)
    return BoardEntry.from(next)
  }

  @Transactional(readOnly = true)
  fun statistics(queueType: String): QueueStatsView {
    val today = LocalDate.now()
    val entries = queueEntryRepository.findByQueueTypeAndQueueDate(queueType, today)
    val waiting = entries.count { it.status == "waiting" }
    val inService = entries.count { it.status == "in-progress" }
    val completed = entries.count { it.status == "completed" }
    val waitTimes =
      entries.mapNotNull { entry ->
        entry.createdAt?.let { started -> Duration.between(started, entry.completedAt ?: Instant.now()).toMinutes().coerceAtLeast(0) }
      }
    val avgWaitMinutes = if (waitTimes.isEmpty()) 0L else waitTimes.sum() / waitTimes.size
    return QueueStatsView(
      queueType = queueType,
      waiting = waiting,
      inService = inService,
      completed = completed,
      avgWaitMinutes = avgWaitMinutes,
    )
  }

  @Transactional(readOnly = true)
  fun getEntry(
    queueType: String,
    tokenNumber: String,
  ): QueueEntryView = QueueEntryView.from(findTodayEntry(queueType, tokenNumber))

  @Transactional(readOnly = true)
  fun listServicePoints(queueType: String?): List<ServicePointDto> {
    val points = if (queueType != null) servicePointRepository.findByQueueType(queueType) else servicePointRepository.findAll()
    return points.map { ServicePointDto.from(it) }
  }

  @Transactional
  fun createServicePoint(
    name: String,
    code: String?,
    queueType: String,
    counterNumber: String?,
    status: String?,
    hospitalId: UUID,
  ): ServicePointDto {
    val point =
      servicePointRepository.save(
        ServicePoint(
          hospitalId = hospitalId,
          name = name,
          code = code,
          queueType = queueType,
          counterNumber = counterNumber,
          status = status ?: "open",
        ),
      )
    return ServicePointDto.from(point)
  }

  private fun nextToken(
    queueType: String,
    queueDate: LocalDate,
  ): String {
    val latest = queueEntryRepository.findTopByQueueTypeAndQueueDateOrderByTokenNumberDesc(queueType, queueDate)
    val sequence = latest?.tokenNumber?.substringAfter('-')?.toIntOrNull() ?: 0
    val prefix = queueType.take(1).uppercase()
    return String.format("%s-%02d", prefix, sequence + 1)
  }

  private fun findTodayEntry(
    queueType: String,
    tokenNumber: String,
  ): QueueEntry =
    queueEntryRepository.findByQueueTypeAndTokenNumberAndQueueDate(queueType, tokenNumber, LocalDate.now())
      ?: throw IllegalArgumentException("QUEUE_ENTRY_NOT_FOUND")

  private fun statusRank(status: String): Int =
    when (status) {
      "waiting" -> 0
      "called" -> 1
      "in-progress" -> 2
      else -> 3
    }

  private fun priorityRank(priority: String): Int =
    when (priority) {
      "emergency" -> 0
      "urgent" -> 1
      "routine" -> 2
      else -> 3
    }
}
