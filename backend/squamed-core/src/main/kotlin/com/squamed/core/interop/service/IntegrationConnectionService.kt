package com.squamed.core.interop.service

import com.squamed.core.interop.domain.IntegrationConnection
import com.squamed.core.interop.hl7.Hl7Service
import com.squamed.core.interop.repository.IntegrationConnectionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.util.UUID

@Service
class IntegrationConnectionService(
  private val repository: IntegrationConnectionRepository,
  private val hl7Service: Hl7Service,
) {
  private val logger = KotlinLogging.logger {}

  @Transactional
  fun create(
    name: String,
    connectionType: String,
    endpoint: String?,
    username: String?,
    password: String?,
  ): IntegrationConnection =
    repository.save(
      IntegrationConnection(
        name = name,
        connectionType = connectionType,
        endpoint = endpoint,
        username = username,
        password = password,
      ),
    )

  @Transactional
  fun update(
    id: UUID,
    name: String?,
    connectionType: String?,
    endpoint: String?,
    username: String?,
    password: String?,
    enabled: Boolean?,
  ): IntegrationConnection {
    val conn = find(id)
    name?.let { conn.name = it }
    connectionType?.let { conn.connectionType = it }
    endpoint?.let { conn.endpoint = it }
    username?.let { conn.username = it }
    if (password != null) conn.password = password
    enabled?.let { conn.enabled = it }
    return repository.save(conn)
  }

  @Transactional
  fun delete(id: UUID) {
    repository.deleteById(id)
  }

  @Transactional(readOnly = true)
  fun list(): List<IntegrationConnection> = repository.findAllByOrderByCreatedAtAsc()

  @Transactional(readOnly = true)
  fun find(id: UUID): IntegrationConnection = repository.findById(id).orElseThrow { IllegalArgumentException("INTEGRATION_CONNECTION_NOT_FOUND") }

  @Transactional(readOnly = true)
  fun health(): Map<String, Any?> {
    val mllpHealth = hl7Service.health()
    val connections =
      repository.findAll().map { conn ->
        mapOf(
          "id" to conn.id,
          "name" to conn.name,
          "connectionType" to conn.connectionType,
          "enabled" to conn.enabled,
          "status" to probeConnection(conn),
        )
      }
    return mapOf(
      "mllp" to mllpHealth,
      "connections" to connections,
    )
  }

  private fun probeConnection(conn: IntegrationConnection): String {
    if (!conn.enabled) return "disabled"
    val endpoint = conn.endpoint ?: return "misconfigured"
    return try {
      when (conn.connectionType.uppercase()) {
        "MLLP", "HL7" -> {
          val (host, port) = parseEndpoint(endpoint)
          val ok =
            Socket().use { s ->
              s.connect(InetSocketAddress(host, port), 2000)
              true
            }
          markSuccess(conn)
          if (ok) "healthy" else "unreachable"
        }

        else -> {
          markSuccess(conn)
          "healthy"
        }
      }
    } catch (e: Exception) {
      markFailure(conn, e.message)
      "unreachable"
    }
  }

  private fun parseEndpoint(endpoint: String): Pair<String, Int> {
    val trimmed = endpoint.removePrefix("mllp://").removePrefix("tcp://")
    val idx = trimmed.lastIndexOf(':')
    val host = if (idx > 0) trimmed.substring(0, idx) else trimmed
    val port = if (idx > 0) trimmed.substring(idx + 1).toIntOrNull() ?: 2575 else 2575
    return host to port
  }

  private fun markSuccess(conn: IntegrationConnection) {
    conn.lastHealthCheckAt = Instant.now()
    conn.lastSuccessAt = Instant.now()
    conn.lastError = null
    repository.save(conn)
  }

  private fun markFailure(
    conn: IntegrationConnection,
    error: String?,
  ) {
    conn.lastHealthCheckAt = Instant.now()
    conn.lastError = error?.take(1000)
    conn.errorCount = conn.errorCount + 1
    repository.save(conn)
  }
}
