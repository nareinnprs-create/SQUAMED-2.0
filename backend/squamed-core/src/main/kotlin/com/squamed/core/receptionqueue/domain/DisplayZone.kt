package com.squamed.core.receptionqueue.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** 01.07: an audio/display zone — announcements are mapped to zones per display/counter. */
@Entity
@Table(name = "display_zone", schema = "queue")
class DisplayZone(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "name", nullable = false, length = 150)
  var name: String,
  @Column(name = "service_point_id", length = 50)
  var servicePointId: String? = null,
  /** TTS volume 0-100 for this zone; the browser page sets utterance volume from it. */
  @Column(name = "volume", nullable = false)
  var volume: Int = 80,
  @Column(name = "enabled", nullable = false)
  var enabled: Boolean = true,
) : BaseEntity()
