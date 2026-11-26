package com.squamed.core.common.api

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

private val logger = LoggerFactory.getLogger("GlobalExceptionHandler")

@RestControllerAdvice
class GlobalExceptionHandler {
  data class ErrorBody(
    val error: ErrorInfo,
    val timestamp: String,
  ) {
    data class ErrorInfo(
      val code: String,
      val message: String,
      val details: List<Map<String, String>> = emptyList(),
    )
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorBody> {
    val details =
      ex.bindingResult.fieldErrors.map { e: FieldError ->
        mapOf("field" to e.field, "message" to (e.defaultMessage ?: "invalid"))
      }
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo("VALIDATION_ERROR", "Invalid request parameters", details),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.badRequest().body(body)
  }

  @ExceptionHandler(ConstraintViolationException::class)
  fun handleConstraint(ex: ConstraintViolationException): ResponseEntity<ErrorBody> {
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo("VALIDATION_ERROR", ex.message ?: "Constraint violation"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.badRequest().body(body)
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorBody> {
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo(ex.message ?: "BAD_REQUEST", ex.message ?: "Invalid input"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.badRequest().body(body)
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorBody> {
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo("MALFORMED_BODY", "Request body is missing or malformed"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.badRequest().body(body)
  }

  @ExceptionHandler(IllegalStateException::class)
  fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorBody> {
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo(ex.message ?: "CONFLICT", ex.message ?: "Conflict"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
  }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorBody> {
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo("ACCESS_DENIED", ex.message ?: "You do not have permission to perform this action"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body)
  }

  // An unmatched path is a client error, not a server fault: without this the catch-all below
  // turns NoResourceFoundException into a 500 for any typo'd /api/v1/... URL.
  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ErrorBody> {
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo("NOT_FOUND", "No resource at ${ex.resourcePath}"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
  }

  @ExceptionHandler(Exception::class)
  fun handleGeneric(ex: Exception): ResponseEntity<ErrorBody> {
    logger.error("Unexpected error", ex)
    val body =
      ErrorBody(
        error = ErrorBody.ErrorInfo("INTERNAL_ERROR", "An unexpected error occurred"),
        timestamp = Instant.now().toString(),
      )
    return ResponseEntity.internalServerError().body(body)
  }
}
