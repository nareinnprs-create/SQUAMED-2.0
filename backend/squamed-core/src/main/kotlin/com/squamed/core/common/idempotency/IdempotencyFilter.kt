package com.squamed.core.common.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.common.api.GlobalExceptionHandler
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.security.MessageDigest
import java.time.Instant

private val log = LoggerFactory.getLogger(IdempotencyFilter::class.java)

/**
 * 00.02 contract foundation — Idempotency-Key middleware (gate 1 named content).
 *
 * Semantics: for POST/PUT/PATCH/DELETE with an `Idempotency-Key` header and a JSON body, the first
 * completed execution is stored and every retry with the same key+method+endpoint+payload replays
 * the stored response byte-for-byte; a different payload under the same key is a 409. The filter
 * runs after the security chain (SecurityProperties.DEFAULT_FILTER_ORDER + 1) so a replay can never
 * bypass authentication.
 *
 * shortcut: two concurrent duplicate requests may both execute (store-on-first-completion; the
 * unique constraint keeps one stored response, later retries replay it). Responses >1MB and
 * non-JSON requests bypass storage and re-execute on retry — upgrade both when a large-payload
 * endpoint needs dedupe.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
class IdempotencyFilter(
  private val repository: IdempotencyKeyRecordRepository,
  private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
  override fun shouldNotFilter(request: HttpServletRequest): Boolean {
    if (request.method !in WRITE_METHODS) return true
    val key = request.getHeader(HEADER) ?: return true
    if (key.isBlank() || key.length > MAX_KEY_LENGTH) return true
    if (request.contentLengthLong > MAX_CAPTURE_BYTES) return true // see shortcut note
    val contentType = request.contentType
    // Only JSON bodies (and bodyless DELETE) are in scope; uploads/multipart bypass replay.
    if (contentType != null && !contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) return true
    return false
  }

  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    val repeatable = RepeatableJsonRequest(request)
    val fingerprint = fingerprintOf(request, repeatable)
    val existing = repository.findByIdempotencyKeyAndMethodAndEndpoint(request.getHeader(HEADER), request.method, request.requestURI)

    if (existing != null) {
      if (existing.requestHash == fingerprint) {
        replay(existing, response)
      } else {
        conflict(response)
      }
      return
    }

    val capturing = ContentCachingResponseWrapper(response)
    try {
      filterChain.doFilter(repeatable, capturing)
    } finally {
      store(request, fingerprint, capturing)
      capturing.copyBodyToResponse()
    }
  }

  private fun store(
    request: HttpServletRequest,
    fingerprint: String,
    capturing: ContentCachingResponseWrapper,
  ) {
    if (capturing.status >= 500) return // 5xx are transient; the retry must re-execute
    if (capturing.contentAsByteArray.size > MAX_CAPTURE_BYTES) return // see shortcut note
    try {
      repository.save(
        IdempotencyKeyRecord(
          idempotencyKey = request.getHeader(HEADER),
          method = request.method,
          endpoint = request.requestURI,
          requestHash = fingerprint,
          responseStatus = capturing.status,
          responseBody = String(capturing.contentAsByteArray, Charsets.UTF_8),
          contentType = capturing.contentType,
        ),
      )
    } catch (e: DataIntegrityViolationException) {
      // Concurrent duplicate: the first-completed execution owns the key. Leave this response
      // as-is; the client's next retry replays the stored winner (or 409s on payload mismatch).
      val winner = repository.findByIdempotencyKeyAndMethodAndEndpoint(request.getHeader(HEADER), request.method, request.requestURI)
      if (winner != null && winner.requestHash != fingerprint) {
        log.warn("Idempotency-Key {} stored under a different payload (fingerprint {})", request.getHeader(HEADER), fingerprint)
      }
    }
  }

  private fun replay(
    record: IdempotencyKeyRecord,
    response: HttpServletResponse,
  ) {
    response.status = record.responseStatus ?: 200
    record.contentType?.let { response.contentType = it }
    record.responseBody?.let { response.outputStream.write(it.toByteArray(Charsets.UTF_8)) }
  }

  private fun conflict(response: HttpServletResponse) {
    val body =
      GlobalExceptionHandler.ErrorBody(
        error =
          GlobalExceptionHandler.ErrorBody.ErrorInfo(
            "IDEMPOTENCY_CONFLICT",
            "This Idempotency-Key was already used with a different request payload",
          ),
        timestamp = Instant.now().toString(),
      )
    response.status = 409
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.outputStream.write(objectMapper.writeValueAsBytes(body))
  }

  private fun fingerprintOf(
    request: HttpServletRequest,
    repeatable: RepeatableJsonRequest,
  ): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(request.method.toByteArray())
    digest.update(request.requestURI.toByteArray())
    digest.update((request.queryString ?: "").toByteArray())
    digest.update(repeatable.body)
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private class RepeatableJsonRequest(
    request: HttpServletRequest,
  ) : HttpServletRequestWrapper(request) {
    val body: ByteArray = request.inputStream.readBytes()

    override fun getInputStream(): ServletInputStream =
      object : ServletInputStream() {
        private var pos = 0

        override fun read(): Int = if (pos < body.size) body[pos++].toInt() and 0xFF else -1

        override fun isFinished(): Boolean = pos >= body.size

        override fun isReady(): Boolean = true

        override fun setReadListener(listener: ReadListener?) {}
      }
  }

  companion object {
    const val HEADER = "Idempotency-Key"
    private val WRITE_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    private const val MAX_KEY_LENGTH = 255
    private const val MAX_CAPTURE_BYTES = 1_000_000
  }
}
