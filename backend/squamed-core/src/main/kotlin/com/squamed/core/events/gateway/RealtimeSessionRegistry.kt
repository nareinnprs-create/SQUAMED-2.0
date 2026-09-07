package com.squamed.core.events.gateway

import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class RealtimeSessionRegistry {
  private val sessionsByUser = ConcurrentHashMap<UUID, CopyOnWriteArrayList<WebSocketSession>>()

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

  fun sessionsFor(userId: UUID): List<WebSocketSession> = sessionsByUser[userId] ?: emptyList()

  fun connectedCount(): Int = sessionsByUser.values.sumOf { it.size }
}
