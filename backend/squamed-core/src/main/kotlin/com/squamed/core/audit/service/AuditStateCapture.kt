package com.squamed.core.audit.service

import java.util.UUID

/**
 * Thread-local before/after capture buffer drained by [AuditRecorder] when it writes the audit
 * row for the current request (00.10). The Hibernate interceptor pushes entries at flush time;
 * everything left after the drain (state changes with no matching audit row) is discarded.
 */
object AuditStateCapture {
  data class Entry(
    val entityType: String,
    val entityId: String,
    val before: String?,
    val after: String?,
  )

  private val buffer = ThreadLocal.withInitial { mutableListOf<Entry>() }

  fun push(
    entityType: String,
    entityId: String,
    before: String?,
    after: String?,
  ) {
    buffer.get().add(Entry(entityType, entityId, before, after))
  }

  /** Removes and returns entries matching the audit row: exact type+id, else id-only fallback. */
  fun drain(
    entityType: String,
    entityId: String?,
  ): List<Entry> {
    val all = buffer.get().toList()
    buffer.get().clear()
    val exact = all.filter { it.entityType == entityType && (entityId == null || it.entityId == entityId) }
    if (exact.isNotEmpty() || entityId == null) return exact
    // Aspect types are DTOs (PatientUpdateDto), Hibernate pushes entities (PatientRecord) — the
    // path id equals the entity PK for updates; outbox/audit rows never share it.
    return all.filter { it.entityId == entityId }
  }

  fun clear() {
    buffer.get().clear()
  }
}
