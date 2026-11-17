package com.squamed.core.receptionqueue.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
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
  private val configDocumentService: ConfigDocumentService,
  private val eventPublisher: DomainEventPublisher,
  private val announcementService: AnnouncementService,
) {
  companion object {
    /** 01.02 built-in priority order; a hospital queue-config `priorityClasses` array overrides it (array order = rank). */
    private val DEFAULT_PRIORITY_ORDER = listOf("emergency", "urgent", "vip", "assisted", "appointment-order", "routine")
    private const val DEFAULT_AVG_CONSULT_MINUTES = 5
  }

  /** Queue config item for one queue type (00.05 doc); null = the hospital has not published a queue config. */
  private fun queueConfig(
    hospitalId: UUID?,
    queueType: String,
  ): JsonNode? {
    val hid = hospitalId ?: return null
    val content: JsonNode = configDocumentService.latest(hid, "queue")?.content ?: return null
    return content.path("queues").firstOrNull { it.path("code").asText() == queueType }
  }

  /** Config-driven rank map (array order = priority); unknown classes rank last. */
  private fun priorityRanks(
    hospitalId: UUID?,
    queueType: String,
  ): Map<String, Int> {
    val classes = queueConfig(hospitalId, queueType)?.path("priorityClasses")
    val order = if (classes != null && classes.isArray && classes.size() > 0) classes.map { it.asText() } else DEFAULT_PRIORITY_ORDER
    return order.withIndex().associate { (idx, cls) -> cls to idx }
  }

  private fun avgConsultMinutes(
    hospitalId: UUID?,
    queueType: String,
  ): Int =
    queueConfig(hospitalId, queueType)
      ?.path("avgConsultMinutes")
      ?.takeIf { it.isNumber }
      ?.asInt() ?: DEFAULT_AVG_CONSULT_MINUTES

  /** 01.02 estimated wait: waiting-ahead count × avg consult minutes (per-queue config, fallback 5). */
  private fun estimateWaitMinutes(
    hospitalId: UUID?,
    queueType: String,
    queueDate: LocalDate,
  ): Int =
    queueEntryRepository.findByQueueTypeAndStatusAndQueueDate(hospitalId, queueType, "waiting", queueDate).size *
      avgConsultMinutes(hospitalId, queueType)

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
    // 01.07: stamp the human-readable counter name so queue.called/announcements show "Counter 1", not the raw id.
    val servicePointName = servicePointId?.let { servicePointRepository.findFirstByHospitalIdAndCode(hospitalId, it)?.name }
    val entry =
      queueEntryRepository.save(
        QueueEntry(
          hospitalId = hospitalId,
          queueType = queueType,
          departmentId = departmentId,
          servicePointId = servicePointId,
          servicePointName = servicePointName,
          visitId = visitId,
          patientId = patientId,
          patientName = patientName,
          tokenNumber = nextToken(hospitalId, queueType, today),
          priority = priority ?: "routine",
          estimatedWaitMinutes = estimateWaitMinutes(hospitalId, queueType, today),
          queueDate = today,
        ),
      )
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun call(
    queueType: String,
    tokenNumber: String,
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
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
        hospitalId = entry.hospitalId,
        payload =
          mapOf(
            "tokenNumber" to entry.tokenNumber,
            "queueType" to entry.queueType,
            "servicePointName" to entry.servicePointName,
            "servicePointId" to entry.servicePointId,
            "priority" to entry.priority,
          ),
      ),
    )
    // 01.07: announcement fans out after queue.called so WS ordering is display event → speech.
    announcementService.onQueueCalled(entry)
    return QueueEntryView.from(entry)
  }

  /** 01.02 transfer: move a waiting entry to another queue; it gets a fresh token from the target sequence. */
  @Transactional
  fun transfer(
    queueType: String,
    tokenNumber: String,
    targetQueueType: String,
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
    if (entry.status != "waiting") throw IllegalStateException("QUEUE_ENTRY_NOT_WAITING")
    // Compute the fresh token/estimate BEFORE mutating queueType: Hibernate auto-flush would
    // otherwise make the entry itself match the target-queue token query (sequence off by one).
    val freshToken = nextToken(hospitalId, targetQueueType, LocalDate.now())
    val estimatedWait = estimateWaitMinutes(hospitalId, targetQueueType, LocalDate.now())
    entry.queueType = targetQueueType
    entry.tokenNumber = freshToken
    entry.estimatedWaitMinutes = estimatedWait
    queueEntryRepository.save(entry)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "queue.transferred",
        occurredAt = Instant.now(),
        source = "queue-service",
        entityType = "queue-entry",
        entityId = entry.id,
        hospitalId = entry.hospitalId,
        payload =
          mapOf(
            "fromQueueType" to queueType,
            "toQueueType" to targetQueueType,
            "tokenNumber" to entry.tokenNumber,
          ),
      ),
    )
    return QueueEntryView.from(entry)
  }

  /** 01.02 emergency bump: raise the entry to emergency priority; displays get queue.bumped over WS (<2 s). */
  @Transactional
  fun bump(
    queueType: String,
    tokenNumber: String,
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
    entry.priority = "emergency"
    queueEntryRepository.save(entry)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "queue.bumped",
        occurredAt = Instant.now(),
        source = "queue-service",
        entityType = "queue-entry",
        entityId = entry.id,
        hospitalId = entry.hospitalId,
        payload =
          mapOf(
            "tokenNumber" to entry.tokenNumber,
            "queueType" to entry.queueType,
            "priority" to entry.priority,
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
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
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
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
    entry.status = "completed"
    entry.completedAt = Instant.now()
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun skip(
    queueType: String,
    tokenNumber: String,
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
    entry.status = "skipped"
    entry.skippedAt = Instant.now()
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun postpone(
    queueType: String,
    tokenNumber: String,
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
    entry.status = "postponed"
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional
  fun noShow(
    queueType: String,
    tokenNumber: String,
    hospitalId: UUID?,
  ): QueueEntryView {
    val entry = findTodayEntry(queueType, tokenNumber, hospitalId)
    entry.status = "no-show"
    entry.noShowAt = Instant.now()
    queueEntryRepository.save(entry)
    return QueueEntryView.from(entry)
  }

  @Transactional(readOnly = true)
  fun board(
    queueType: String,
    hospitalId: UUID?,
  ): List<BoardEntry> {
    val today = LocalDate.now()
    val statuses = listOf("waiting", "called", "in-progress", "postponed")
    val ranks = priorityRanks(hospitalId, queueType)
    return queueEntryRepository
      .findByQueueTypeAndStatusInAndQueueDate(hospitalId, queueType, statuses, today)
      .sortedWith(compareBy({ statusRank(it.status) }, { ranks[it.priority] ?: ranks.size }, { it.createdAt ?: Instant.EPOCH }))
      .map { BoardEntry.from(it) }
  }

  @Transactional
  fun callNext(
    queueType: String,
    hospitalId: UUID?,
  ): BoardEntry {
    val today = LocalDate.now()
    // 00.04 tenant sweep (U10): calling next must pull from the caller's own hospital queue.
    val ranks = priorityRanks(hospitalId, queueType)
    val next =
      queueEntryRepository
        .findByQueueTypeAndStatusAndQueueDate(hospitalId, queueType, "waiting", today)
        .sortedWith(compareBy({ ranks[it.priority] ?: ranks.size }, { it.createdAt ?: Instant.EPOCH }))
        .firstOrNull() ?: throw IllegalStateException("QUEUE_EMPTY")
    next.status = "called"
    next.calledAt = Instant.now()
    queueEntryRepository.save(next)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "queue.called",
        occurredAt = Instant.now(),
        source = "queue-service",
        entityType = "queue-entry",
        entityId = next.id,
        hospitalId = next.hospitalId,
        payload =
          mapOf(
            "tokenNumber" to next.tokenNumber,
            "queueType" to next.queueType,
            "servicePointName" to next.servicePointName,
            "servicePointId" to next.servicePointId,
            "priority" to next.priority,
          ),
      ),
    )
    // 01.07: announcement fans out after queue.called so WS ordering is display event → speech.
    announcementService.onQueueCalled(next)
    return BoardEntry.from(next)
  }

  @Transactional(readOnly = true)
  fun statistics(
    queueType: String,
    hospitalId: UUID?,
  ): QueueStatsView {
    val today = LocalDate.now()
    val entries = queueEntryRepository.findByQueueTypeAndQueueDate(hospitalId, queueType, today)
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
    hospitalId: UUID?,
  ): QueueEntryView = QueueEntryView.from(findTodayEntry(queueType, tokenNumber, hospitalId))

  @Transactional(readOnly = true)
  fun listServicePoints(
    queueType: String?,
    hospitalId: UUID?,
  ): List<ServicePointDto> {
    val points = if (queueType != null) servicePointRepository.findByQueueType(hospitalId, queueType) else servicePointRepository.findAllForHospital(hospitalId)
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
    hospitalId: UUID?,
    queueType: String,
    queueDate: LocalDate,
  ): String {
    val latest = queueEntryRepository.findTopByQueueTypeAndQueueDateOrderByTokenNumberDesc(hospitalId, queueType, queueDate)
    val sequence = latest?.tokenNumber?.substringAfter('-')?.toIntOrNull() ?: 0
    val prefix = queueType.take(1).uppercase()
    return String.format("%s-%02d", prefix, sequence + 1)
  }

  private fun findTodayEntry(
    queueType: String,
    tokenNumber: String,
    hospitalId: UUID?,
  ): QueueEntry =
    queueEntryRepository.findByQueueTypeAndTokenNumberAndQueueDate(hospitalId, queueType, tokenNumber, LocalDate.now())
      ?: throw IllegalArgumentException("QUEUE_ENTRY_NOT_FOUND")

  private fun statusRank(status: String): Int =
    when (status) {
      "waiting" -> 0
      "called" -> 1
      "in-progress" -> 2
      else -> 3
    }
}
