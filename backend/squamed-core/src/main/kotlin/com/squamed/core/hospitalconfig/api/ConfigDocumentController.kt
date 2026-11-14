package com.squamed.core.hospitalconfig.api

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.api.ApiResponse
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
import com.squamed.core.identity.security.RequiresPermission
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// Nested under /hospitals/{hospitalId} so TenantInterceptor denies foreign-hospital access (00.04).
@RestController
@RequestMapping("/api/v1/hospital-config")
class ConfigDocumentController(
  private val configDocumentService: ConfigDocumentService,
) {
  @PutMapping("/hospitals/{hospitalId}/config/{type}")
  @RequiresPermission("hospital-config:write:hospital")
  @Transactional
  fun publishConfig(
    @PathVariable hospitalId: UUID,
    @PathVariable type: String,
    @RequestBody content: JsonNode,
  ): ApiResponse<ConfigDocumentDto> = ApiResponse.ok(ConfigDocumentDto.from(configDocumentService.publish(hospitalId, type, content)))

  @GetMapping("/hospitals/{hospitalId}/config/{type}")
  @RequiresPermission("hospital-config:read:hospital")
  @Transactional(readOnly = true)
  fun getLatestConfig(
    @PathVariable hospitalId: UUID,
    @PathVariable type: String,
  ): ApiResponse<ConfigDocumentDto> =
    ApiResponse.ok(
      ConfigDocumentDto.from(
        configDocumentService.latest(hospitalId, type) ?: throw IllegalArgumentException("CONFIG_DOCUMENT_NOT_FOUND"),
      ),
    )

  @GetMapping("/hospitals/{hospitalId}/config/{type}/versions")
  @RequiresPermission("hospital-config:read:hospital")
  @Transactional(readOnly = true)
  fun listConfigVersions(
    @PathVariable hospitalId: UUID,
    @PathVariable type: String,
  ): ApiResponse<List<ConfigDocumentDto>> = ApiResponse.ok(configDocumentService.versions(hospitalId, type).map { ConfigDocumentDto.from(it) })

  @GetMapping("/hospitals/{hospitalId}/config/{type}/versions/{version}")
  @RequiresPermission("hospital-config:read:hospital")
  @Transactional(readOnly = true)
  fun getConfigAtVersion(
    @PathVariable hospitalId: UUID,
    @PathVariable type: String,
    @PathVariable version: Int,
  ): ApiResponse<ConfigDocumentDto> =
    ApiResponse.ok(
      ConfigDocumentDto.from(
        configDocumentService.atVersion(hospitalId, type, version) ?: throw IllegalArgumentException("CONFIG_DOCUMENT_NOT_FOUND"),
      ),
    )
}
