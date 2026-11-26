package com.squamed.core.pack.domain

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** A versioned bundle of configuration; installing it never runs code (V108 `pack.specialty_pack`). */
@Entity
@Table(name = "specialty_pack", schema = "pack")
class SpecialtyPack(
  @Column(name = "code", nullable = false, length = 60) var code: String,
  @Column(name = "name", nullable = false, length = 150) var name: String,
  @Column(name = "specialty", nullable = false, length = 60) var specialty: String,
  @Column(name = "description", length = 500) var description: String? = null,
  @Column(name = "pack_version", nullable = false) var packVersion: Int = 1,
  @Column(name = "status", nullable = false, length = 20) var status: String = "DRAFT",
) : BaseEntity()

/** One installable piece: a CONFIG document (existing config type) or a ROLE grant set. */
@Entity
@Table(name = "pack_component", schema = "pack")
class PackComponent(
  @Column(name = "pack_id", nullable = false) var packId: UUID,
  @Column(name = "component_type", nullable = false, length = 20) var componentType: String,
  @Column(name = "component_key", nullable = false, length = 150) var componentKey: String,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  var payload: JsonNode,
) : BaseEntity()

/** Installation record; unique per (hospital, pack, version) so a re-install is idempotent. */
@Entity
@Table(name = "pack_install", schema = "pack")
class PackInstall(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "pack_id", nullable = false) var packId: UUID,
  @Column(name = "pack_version", nullable = false) var packVersion: Int,
  @Column(name = "status", nullable = false, length = 20) var status: String,
  @Column(name = "detail", length = 500) var detail: String? = null,
  @Column(name = "installed_at") var installedAt: Instant? = null,
) : BaseEntity()
