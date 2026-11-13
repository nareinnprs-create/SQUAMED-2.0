package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.QueueEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface QueueEntryRepository : JpaRepository<QueueEntry, UUID> {
  fun findTopByQueueTypeAndQueueDateOrderByTokenNumberDesc(
    queueType: String,
    queueDate: LocalDate,
  ): QueueEntry?

  fun findByQueueTypeAndStatusAndQueueDate(
    queueType: String,
    status: String,
    queueDate: LocalDate,
  ): List<QueueEntry>

  fun findByPatientId(patientId: UUID): List<QueueEntry>

  fun countByQueueTypeAndStatusAndQueueDate(
    queueType: String,
    status: String,
    queueDate: LocalDate,
  ): Long

  fun findByServicePointIdAndStatusIn(
    servicePointId: String,
    statuses: Collection<String>,
  ): List<QueueEntry>

  fun findByQueueTypeAndTokenNumberAndQueueDate(
    queueType: String,
    tokenNumber: String,
    queueDate: LocalDate,
  ): QueueEntry?

  fun findByQueueTypeAndQueueDate(
    queueType: String,
    queueDate: LocalDate,
  ): List<QueueEntry>

  fun findByQueueTypeAndStatusInAndQueueDate(
    queueType: String,
    statuses: Collection<String>,
    queueDate: LocalDate,
  ): List<QueueEntry>
}
