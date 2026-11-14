package com.squamed.core.common.config

import com.squamed.core.common.tenant.TenantInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
  @Value("\${squamed.cors.allowed-origins}") private val allowedOrigins: String,
  private val tenantInterceptor: TenantInterceptor,
) : WebMvcConfigurer {
  override fun addCorsMappings(registry: CorsRegistry) {
    registry
      .addMapping("/api/**")
      .allowedOrigins(*allowedOrigins.split(",").toTypedArray())
      .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
      .allowedHeaders("*")
      .allowCredentials(true)
      .maxAge(3600)
  }

  override fun addInterceptors(registry: InterceptorRegistry) {
    registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/v1/hospital-config/hospitals/**")
  }
}
