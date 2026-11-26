package com.squamed.core.pack.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
import com.squamed.core.identity.domain.Role
import com.squamed.core.identity.repository.RoleRepository
import com.squamed.core.pack.domain.PackComponent
import com.squamed.core.pack.domain.PackInstall
import com.squamed.core.pack.domain.SpecialtyPack
import com.squamed.core.pack.repository.PackComponentRepository
import com.squamed.core.pack.repository.PackInstallRepository
import com.squamed.core.pack.repository.PackPermissionRepository
import com.squamed.core.pack.repository.SpecialtyPackRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class PackComponentInput(
  val componentType: String,
  val componentKey: String,
  val payload: JsonNode,
)

/**
 * 07.10 specialty packs: a pack is configuration, not code. Installing one publishes CONFIG documents
 * through the normal validated path and ensures ROLE grants; the contents still need clinical review.
 */
@Service
class SpecialtyPackService(
  private val packRepository: SpecialtyPackRepository,
  private val componentRepository: PackComponentRepository,
  private val installRepository: PackInstallRepository,
  private val permissionRepository: PackPermissionRepository,
  private val roleRepository: RoleRepository,
  private val configDocumentService: ConfigDocumentService,
) {
  companion object {
    val COMPONENT_TYPES = setOf("CONFIG", "ROLE")
  }

  @Transactional
  fun create(
    code: String,
    name: String,
    specialty: String,
    description: String?,
    components: List<PackComponentInput>,
    actorId: UUID?,
  ): Map<String, Any?> {
    require(code.isNotBlank() && name.isNotBlank() && specialty.isNotBlank()) { "PACK_FIELDS_REQUIRED" }
    require(components.isNotEmpty()) { "PACK_COMPONENTS_REQUIRED" }
    require(packRepository.findByCode(code.trim().uppercase()).isEmpty) { "PACK_CODE_EXISTS" }
    components.forEach { validateComponent(it) }
    val pack =
      packRepository
        .save(SpecialtyPack(code.trim().uppercase(), name, specialty, description, status = "PUBLISHED"))
        .also {
          it.createdBy = actorId
          it.updatedBy = actorId
        }
    components.forEach {
      componentRepository.save(PackComponent(pack.id!!, it.componentType, it.componentKey, it.payload)).also { c ->
        c.createdBy = actorId
      }
    }
    return view(pack, components.size)
  }

  @Transactional(readOnly = true)
  fun packs(): List<Map<String, Any?>> =
    packRepository.findAllByOrderByCodeAsc().map { view(it, componentRepository.findByPackIdOrderByComponentTypeAsc(it.id!!).size) }

  @Transactional
  fun install(
    hospitalId: UUID,
    code: String,
    actorId: UUID?,
  ): Map<String, Any?> {
    val started = System.currentTimeMillis()
    val pack = packRepository.findByCode(code.trim().uppercase()).orElseThrow { IllegalArgumentException("PACK_NOT_FOUND") }
    require(pack.status == "PUBLISHED") { "PACK_NOT_PUBLISHED" }
    installRepository.findByHospitalIdAndPackIdAndPackVersion(hospitalId, pack.id!!, pack.packVersion).orElse(null)?.let {
      return mapOf(
        "pack" to pack.code,
        "version" to pack.packVersion,
        "status" to "ALREADY_INSTALLED",
        "installedAt" to it.installedAt,
        "elapsedMs" to 0,
      )
    }
    val components = componentRepository.findByPackIdOrderByComponentTypeAsc(pack.id!!)
    val applied = mutableListOf<String>()
    components.forEach { component ->
      when (component.componentType) {
        "CONFIG" -> {
          configDocumentService.publish(hospitalId, component.componentKey, component.payload)
          applied.add("CONFIG:${component.componentKey}")
        }

        "ROLE" -> {
          ensureRole(component.componentKey, component.payload, actorId)
          applied.add("ROLE:${component.componentKey}")
        }

        else -> {
          throw IllegalArgumentException("PACK_COMPONENT_INVALID")
        }
      }
    }
    val install =
      installRepository
        .save(
          PackInstall(
            hospitalId = hospitalId,
            packId = pack.id!!,
            packVersion = pack.packVersion,
            status = "INSTALLED",
            detail = applied.joinToString(",").take(500),
            installedAt = Instant.now(),
          ),
        ).also { it.createdBy = actorId }
    return mapOf(
      "pack" to pack.code,
      "version" to pack.packVersion,
      "status" to install.status,
      "components" to applied,
      "installedAt" to install.installedAt,
      "elapsedMs" to (System.currentTimeMillis() - started),
    )
  }

  @Transactional(readOnly = true)
  fun installs(hospitalId: UUID): List<PackInstall> = installRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  private fun validateComponent(component: PackComponentInput) {
    require(component.componentType in COMPONENT_TYPES) { "PACK_COMPONENT_TYPE_INVALID: ${component.componentType}" }
    require(component.componentKey.isNotBlank()) { "PACK_COMPONENT_KEY_REQUIRED" }
    when (component.componentType) {
      "CONFIG" -> {
        require(component.componentKey in ConfigDocumentService.TYPES) { "UNKNOWN_CONFIG_TYPE: ${component.componentKey}" }
      }

      "ROLE" -> {
        val permissions = component.payload.get("permissions")
        require(permissions != null && permissions.isArray && permissions.size() > 0) { "PACK_ROLE_PERMISSIONS_REQUIRED" }
        permissions.forEach { node ->
          val (domain, action, resource) = splitCode(node.asText())
          require(permissionRepository.findByCode(domain, action, resource).isPresent) { "PACK_PERMISSION_UNKNOWN: ${node.asText()}" }
        }
      }
    }
  }

  private fun ensureRole(
    roleName: String,
    payload: JsonNode,
    actorId: UUID?,
  ) {
    val role =
      roleRepository.findByName(roleName).orElseGet {
        roleRepository
          .save(
            Role(
              name = roleName,
              displayName = payload.get("displayName")?.asText() ?: roleName,
              description = payload.get("description")?.asText(),
              isSystem = false,
            ),
          ).also { it.createdBy = actorId }
      }
    payload.get("permissions").forEach { node ->
      val (domain, action, resource) = splitCode(node.asText())
      val permission =
        permissionRepository
          .findByCode(
            domain,
            action,
            resource,
          ).orElseThrow { IllegalArgumentException("PACK_PERMISSION_UNKNOWN: ${node.asText()}") }
      role.permissions.add(permission)
    }
    roleRepository.save(role)
  }

  private fun splitCode(code: String): Triple<String, String, String?> {
    val parts = code.split(":")
    require(parts.size >= 2) { "PACK_PERMISSION_CODE_INVALID: $code" }
    return Triple(parts[0], parts[1], parts.getOrNull(2))
  }

  private fun view(
    pack: SpecialtyPack,
    componentCount: Int,
  ): Map<String, Any?> =
    mapOf(
      "id" to pack.id,
      "code" to pack.code,
      "name" to pack.name,
      "specialty" to pack.specialty,
      "description" to pack.description,
      "version" to pack.packVersion,
      "status" to pack.status,
      "components" to componentCount,
    )
}
