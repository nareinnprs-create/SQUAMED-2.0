package com.squamed.core.identity.security

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * Declares the permission required to invoke the annotated method.
 * Format: {domain}:{action}[:{resource}] e.g. patient:read, lab:validate:result
 */
@Target(FUNCTION, CLASS)
@Retention(RUNTIME)
annotation class RequiresPermission(
  val value: String,
)
