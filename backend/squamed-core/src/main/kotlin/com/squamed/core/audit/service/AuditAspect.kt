package com.squamed.core.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.Locale
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Aspect
@Component
class AuditAspect(
  private val auditRecorder: AuditRecorder,
  private val objectMapper: ObjectMapper,
) {
  @Around(
    "execution(* com.squamed.core..*Controller.*(..)) && " +
      "!execution(* com.squamed.core.identity.api.AuthController.*(..))",
  )
  fun audit(joinPoint: ProceedingJoinPoint): Any? {
    val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
    val request = attributes?.request
    val response = attributes?.response

    // 00.10 is a write-path interceptor (ADR-15): reads (GET/HEAD) skip the audit path entirely.
    if (request?.method?.uppercase(Locale.ROOT) in setOf("GET", "HEAD")) return joinPoint.proceed()

    val controllerName = joinPoint.signature.declaringType.simpleName
    val methodName = joinPoint.signature.name

    val entityType =
      joinPoint.args
        .filterNotNull()
        .filterNot { it.javaClass.simpleName == "SquamedPrincipal" }
        .firstOrNull {
          it.javaClass.`package`
            ?.name
            ?.startsWith("com.squamed.core") == true
        }?.javaClass
        ?.simpleName
        ?: controllerName.removeSuffix("Controller").ifEmpty { controllerName }

    val entityId =
      joinPoint.args
        .filterIsInstance<UUID>()
        .firstOrNull()
        ?.toString()

    val requestPayload =
      serialize(
        joinPoint.args
          .filterNotNull()
          .filterNot { it.javaClass.simpleName == "SquamedPrincipal" }
          .firstOrNull {
            it.javaClass.`package`
              ?.name
              ?.startsWith("com.squamed.core") == true
          },
      )

    var result: Any? = null
    var responseStatus = response?.status
    try {
      result = joinPoint.proceed()
      responseStatus = response?.status
      return result
    } catch (e: Throwable) {
      val current = response?.status
      responseStatus = if (current != null && current >= 400) current else 500
      throw e
    } finally {
      val responsePayload = serialize(result)
      auditRecorder.record(
        action = methodName,
        entityType = entityType,
        entityId = entityId,
        ipAddress = request?.remoteAddr,
        userAgent = request?.getHeader("User-Agent"),
        method = request?.method,
        path = request?.requestURI,
        requestPayload = requestPayload,
        responseStatus = responseStatus,
        responsePayload = responsePayload,
      )
    }
  }

  private fun serialize(value: Any?): String? {
    if (value == null) return null
    return try {
      objectMapper.writeValueAsString(value).take(2000)
    } catch (e: Exception) {
      logger.debug { "Failed to serialize audit payload: ${e.message}" }
      null
    }
  }
}
