package com.squamed.core.downtime.api

import com.squamed.core.downtime.domain.DowntimeLog
import com.squamed.core.receptionqueue.api.QueueEntryView
import com.squamed.core.receptionqueue.api.VisitDto
import java.time.Instant
import java.util.UUID

data class DowntimeOpenDto(
  val hospitalId: UUID? = null,
  val reason: String? = null,
)

data class DowntimeTokenReentryDto(
  val queueType: String? = null,
  val tokenNumber: String? = null,
  val patientId: UUID? = null,
  val patientName: String? = null,
  val priority: String? = null,
  val servicePointId: String? = null,
  val departmentId: UUID? = null,
)

data class DowntimeReentryDto(
  val hospitalId: UUID? = null,
  val tokens: List<DowntimeTokenReentryDto>? = null,
  val visitIds: List<UUID>? = null,
)

data class DowntimeLogView(
  val id: UUID?,
  val hospitalId: UUID?,
  val reason: String,
  val startedAt: Instant,
  val endedAt: Instant?,
  val open: Boolean,
) {
  companion object {
    fun from(entity: DowntimeLog) =
      DowntimeLogView(
        id = entity.id,
        hospitalId = entity.hospitalId,
        reason = entity.reason,
        startedAt = entity.startedAt,
        endedAt = entity.endedAt,
        open = entity.endedAt == null,
      )
  }
}

data class DowntimeReentryResult(
  val tokens: List<QueueEntryView>,
  val visits: List<VisitDto>,
)
