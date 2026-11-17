package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * 01.07: per-hospital announcement rule — repeat count (doubled for emergency priority)
 * and the ordered TTS language list; queue_type '*' is the fallback rule for any queue.
 */
@Entity
@Table(name = "announcement_rule", schema = "queue")
class AnnouncementRule(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "queue_type", nullable = false, length = 30)
  var queueType: String = "*",
  @Column(name = "repeat_count", nullable = false)
  var repeatCount: Int = 2,
  /** Comma-ordered language codes for the announcement text (e.g. "en,hi"). */
  @Column(name = "languages", nullable = false, length = 100)
  var languages: String = "en,hi",
  /** null = announce in every zone; otherwise the announcement is scoped to one zone. */
  @Column(name = "zone_id")
  var zoneId: UUID? = null,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
) : BaseEntity()
