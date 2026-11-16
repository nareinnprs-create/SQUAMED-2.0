package com.squamed.core.receptionqueue.repository

import com.squamed.core.receptionqueue.domain.QueueEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface QueueEntryRepository : JpaRepository<QueueEntry, UUID> {
  // 00.04 tenant sweep: null-safe hospital scope — token sequences are per-hospital per-day.
  @Query(
    "select e from QueueEntry e where (:hid is null or e.hospitalId = :hid) and e.queueType = :queueType and e.queueDate = :queueDate order by e.tokenNumber desc limit 1",
  )
  fun findTopByQueueTypeAndQueueDateOrderByTokenNumberDesc(
    @Param("hid") hospitalId: UUID?,
    @Param("queueType") queueType: String,
    @Param("queueDate") queueDate: LocalDate,
  ): QueueEntry?

  // 00.04 tenant sweep: null-safe hospital scope (U10) — admin callers pass null.
  @Query(
    "select e from QueueEntry e where (:hid is null or e.hospitalId = :hid) and e.queueType = :queueType and e.status = :status and e.queueDate = :queueDate",
  )
  fun findByQueueTypeAndStatusAndQueueDate(
    @Param("hid") hospitalId: UUID?,
    @Param("queueType") queueType: String,
    @Param("status") status: String,
    @Param("queueDate") queueDate: LocalDate,
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

  @Query(
    "select e from QueueEntry e where (:hid is null or e.hospitalId = :hid) and e.queueType = :queueType and e.tokenNumber = :tokenNumber and e.queueDate = :queueDate",
  )
  fun findByQueueTypeAndTokenNumberAndQueueDate(
    @Param("hid") hospitalId: UUID?,
    @Param("queueType") queueType: String,
    @Param("tokenNumber") tokenNumber: String,
    @Param("queueDate") queueDate: LocalDate,
  ): QueueEntry?

  @Query("select e from QueueEntry e where (:hid is null or e.hospitalId = :hid) and e.queueType = :queueType and e.queueDate = :queueDate")
  fun findByQueueTypeAndQueueDate(
    @Param("hid") hospitalId: UUID?,
    @Param("queueType") queueType: String,
    @Param("queueDate") queueDate: LocalDate,
  ): List<QueueEntry>

  @Query(
    "select e from QueueEntry e where (:hid is null or e.hospitalId = :hid) and e.queueType = :queueType and e.status in :statuses and e.queueDate = :queueDate",
  )
  fun findByQueueTypeAndStatusInAndQueueDate(
    @Param("hid") hospitalId: UUID?,
    @Param("queueType") queueType: String,
    @Param("statuses") statuses: Collection<String>,
    @Param("queueDate") queueDate: LocalDate,
  ): List<QueueEntry>
}
