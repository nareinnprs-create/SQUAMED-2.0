package com.squamed.core.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.hibernate.type.Type
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.UUID

/**
 * Hibernate interceptor capturing before/after state of every entity flush into
 * [AuditStateCapture] (00.10 before/after capture). Values are filtered to resolved basics
 * (String/Number/Boolean/Enum/temporal/UUID) so lazy associations never force loading.
 * Registered through HibernatePropertiesCustomizer because Spring Boot does not pick up
 * Interceptor beans for both auto-configured SessionFactory paths.
 */
class AuditHibernateInterceptor(
  private val objectMapper: ObjectMapper,
) : org.hibernate.Interceptor {
  override fun onSave(
    entity: Any,
    id: Any,
    state: Array<Any?>,
    propertyNames: Array<String>,
    types: Array<Type>,
  ): Boolean {
    val after = stateJson(state, propertyNames, types)
    AuditStateCapture.push(entity.javaClass.simpleName, id.toString(), null, after)
    return false
  }

  override fun onFlushDirty(
    entity: Any,
    id: Any,
    currentState: Array<Any?>,
    previousState: Array<Any?>?,
    propertyNames: Array<String>,
    types: Array<Type>,
  ): Boolean {
    val before = previousState?.let { stateJson(it, propertyNames, types) }
    val after = stateJson(currentState, propertyNames, types)
    AuditStateCapture.push(entity.javaClass.simpleName, id.toString(), before, after)
    return false
  }

  private fun stateJson(
    state: Array<Any?>,
    propertyNames: Array<String>,
    types: Array<Type>,
  ): String? {
    val values = linkedMapOf<String, Any?>()
    for (i in propertyNames.indices) {
      val resolved = renderable(state[i]) ?: continue
      values[propertyNames[i]] = resolved
    }
    if (values.isEmpty()) return null
    return try {
      objectMapper.writeValueAsString(values)
    } catch (e: Exception) {
      null
    }
  }

  private fun renderable(value: Any?): Any? =
    when (value) {
      null -> null
      is String, is Boolean, is Int, is Long, is Double, is Float -> value
      is Number -> value
      is Enum<*> -> value.name
      is java.time.Instant, is java.time.LocalDate, is java.time.LocalDateTime -> value.toString()
      is UUID -> value.toString()
      is java.util.Date -> value.toInstant().toString()
      else -> null
    }
}

@Configuration
// 01.12 fail-closed audit: order 0 makes the transaction advisor outermost, so AuditAspect
// (LOWEST_PRECEDENCE) and its finally-record run INSIDE the tx — an audit failure rolls the
// clinical write back. Boot's own @EnableTransactionManagement auto-config backs off.
@EnableTransactionManagement(order = 0)
class AuditInterceptorConfig {
  @Bean
  fun auditHibernateInterceptor(objectMapper: ObjectMapper): AuditHibernateInterceptor = AuditHibernateInterceptor(objectMapper)

  @Bean
  fun auditInterceptorCustomizer(interceptor: AuditHibernateInterceptor): HibernatePropertiesCustomizer =
    HibernatePropertiesCustomizer { properties ->
      properties["hibernate.session_factory.interceptor"] = interceptor
    }
}
