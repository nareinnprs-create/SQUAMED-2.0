package com.squamed.core.events.gateway

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class RealtimeSessionRegistry(
  private val meterRegistry: MeterRegistry,
) {
  private val sessionsByUser = ConcurrentHashMap<UUID, CopyOnWriteArrayList<WebSocketSession>>()

  init {
    meterRegistry.gauge("squamed.ws.sessions", this) { it.connectedCount().toDouble() }
  }

  fun add(
    userId: UUID,
    session: WebSocketSession,
  ) {
    sessionsByUser.computeIfAbsent(userId) { CopyOnWriteArrayList() }.add(session)
  }

  fun remove(session: WebSocketSession) {
    val owner = sessionsByUser.entries.firstOrNull { it.value.contains(session) }?.key ?: return
    sessionsByUser[owner]?.remove(session)
    sessionsByUser[owner]?.takeIf { it.isEmpty() }?.let { sessionsByUser.remove(owner) }
  }

  /**
   * `hospitalId` narrows to the sessions opened with that hospital's token (session attribute set
   * by the handler from the JWT `hp` claim); null returns all of the user's sessions.
   */
  fun sessionsFor(
    userId: UUID,
    hospitalId: UUID? = null,
  ): List<WebSocketSession> {
    val sessions = sessionsByUser[userId] ?: emptyList()
    if (hospitalId == null) return sessions.toList()
    return sessions.filter { it.attributes[HOSPITAL_ATTR] == hospitalId }
  }

  fun allSessions(hospitalId: UUID? = null): List<WebSocketSession> {
    val all = sessionsByUser.values.flatten()
    if (hospitalId == null) return all.toList()
    return all.filter { it.attributes[HOSPITAL_ATTR] == hospitalId }
  }

  fun connectedCount(): Int = sessionsByUser.values.sumOf { it.size }

  companion object {
    const val HOSPITAL_ATTR = "hospitalId"
  }
}
