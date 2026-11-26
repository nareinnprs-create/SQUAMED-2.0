package com.squamed.core.bi.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "mart_sync", schema = "bi")
class MartSync(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "last_event_at") var lastEventAt: Instant? = null,
  @Column(name = "last_synced_at") var lastSyncedAt: Instant? = null,
  @Column(name = "events_synced", nullable = false) var eventsSynced: Long = 0,
  @Column(name = "status", nullable = false, length = 20) var status: String = "NEVER",
) : BaseEntity()
