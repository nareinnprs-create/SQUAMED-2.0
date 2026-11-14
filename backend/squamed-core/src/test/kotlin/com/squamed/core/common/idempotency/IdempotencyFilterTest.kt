package com.squamed.core.common.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class IdempotencyFilterTest {
  // relaxed mockk can't fabricate the generic save(S): S return — answer with the argument itself.
  private val repository =
    mockk<IdempotencyKeyRecordRepository>(relaxed = true) {
      every { save(any<IdempotencyKeyRecord>()) } answers { firstArg<IdempotencyKeyRecord>() }
    }
  private val objectMapper = ObjectMapper()
  private val filter = IdempotencyFilter(repository, objectMapper)

  private fun jsonRequest(
    method: String,
    uri: String = "/api/v1/patients",
    key: String? = "test-key",
    body: String? = """{"firstName":"A"}""",
  ): MockHttpServletRequest =
    MockHttpServletRequest(method, uri).apply {
      key?.let { addHeader(IdempotencyFilter.HEADER, it) }
      if (body != null) {
        contentType = "application/json"
        setContent(body.toByteArray())
      }
    }

  private fun writingChain(
    status: Int = 201,
    body: String = """{"data":{"id":1}}""",
  ): MockFilterChain =
    MockFilterChain(
      object : HttpServlet() {
        override fun service(
          req: HttpServletRequest,
          res: HttpServletResponse,
        ) {
          res.status = status
          res.contentType = "application/json"
          res.writer.write(body)
        }
      },
    )

  @Test
  fun `request without Idempotency-Key passes straight through`() {
    val request = jsonRequest("POST", key = null)
    val response = MockHttpServletResponse()
    val chain = writingChain()

    filter.doFilter(request, response, chain)

    assertEquals(HttpStatus.CREATED.value(), response.status)
    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `first execution invokes chain and stores the response`() {
    val request = jsonRequest("POST")
    val response = MockHttpServletResponse()
    val chain = writingChain(status = 201, body = """{"data":{"id":42}}""")
    every { repository.findByIdempotencyKeyAndMethodAndEndpoint(any(), any(), any()) } returns null
    val saved = slot<IdempotencyKeyRecord>()

    filter.doFilter(request, response, chain)

    verify(exactly = 1) { repository.save(capture(saved)) }
    assertEquals("test-key", saved.captured.idempotencyKey)
    assertEquals("POST", saved.captured.method)
    assertEquals("/api/v1/patients", saved.captured.endpoint)
    assertEquals(201, saved.captured.responseStatus)
    assertEquals("""{"data":{"id":42}}""", saved.captured.responseBody)
    assertEquals(201, response.status)
  }

  @Test
  fun `same key and payload replays the stored response without invoking the chain`() {
    // Capture the fingerprint the filter computed on a miss, then serve that record on the retry.
    every { repository.findByIdempotencyKeyAndMethodAndEndpoint(any(), any(), any()) } returns null
    filter.doFilter(jsonRequest("POST"), MockHttpServletResponse(), writingChain(body = """{"data":{"id":42}}"""))
    val saved = slot<IdempotencyKeyRecord>()
    verify(exactly = 1) { repository.save(capture(saved)) }

    every { repository.findByIdempotencyKeyAndMethodAndEndpoint(any(), any(), any()) } returns saved.captured
    val chain = MockFilterChain()
    val replayResponse = MockHttpServletResponse()

    filter.doFilter(jsonRequest("POST"), replayResponse, chain)

    assertTrue(chain.request == null, "replay must not reach the controller")
    assertEquals(201, replayResponse.status)
    assertEquals("""{"data":{"id":42}}""", replayResponse.contentAsString)
  }

  @Test
  fun `same key with different payload is a conflict`() {
    val stored =
      IdempotencyKeyRecord(
        idempotencyKey = "test-key",
        method = "POST",
        endpoint = "/api/v1/patients",
        requestHash = "different-fingerprint",
        responseStatus = 201,
        responseBody = """{"data":{"id":7}}""",
      )
    every { repository.findByIdempotencyKeyAndMethodAndEndpoint(any(), any(), any()) } returns stored
    val request = jsonRequest("POST", body = """{"firstName":"B"}""")
    val response = MockHttpServletResponse()
    val chain = writingChain()

    filter.doFilter(request, response, chain)

    assertEquals(HttpStatus.CONFLICT.value(), response.status)
    val node = ObjectMapper().readTree(response.contentAsString)
    assertEquals("IDEMPOTENCY_CONFLICT", node["error"]["code"].asText())
    assertTrue(chain.request == null, "conflict must not reach the controller")
  }

  @Test
  fun `server error responses are not stored`() {
    val request = jsonRequest("POST")
    val response = MockHttpServletResponse()
    val chain = writingChain(status = 500, body = """{"error":{"code":"x"}}""")
    every { repository.findByIdempotencyKeyAndMethodAndEndpoint(any(), any(), any()) } returns null

    filter.doFilter(request, response, chain)

    verify(exactly = 0) { repository.save(any()) }
  }

  @Test
  fun `different idempotency keys execute independently`() {
    every { repository.findByIdempotencyKeyAndMethodAndEndpoint(any(), any(), any()) } returns null

    filter.doFilter(jsonRequest("POST", key = "key-1"), MockHttpServletResponse(), writingChain())
    filter.doFilter(jsonRequest("POST", key = "key-2"), MockHttpServletResponse(), writingChain())

    val saved = mutableListOf<IdempotencyKeyRecord>()
    verify(exactly = 2) { repository.save(capture(saved)) }
    assertEquals(setOf("key-1", "key-2"), saved.map { it.idempotencyKey }.toSet())
  }
}
