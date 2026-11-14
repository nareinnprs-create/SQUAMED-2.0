package com.squamed.core.hospitalconfig.domain

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.domain.HospitalScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "config_document", schema = "hospital_config")
class ConfigDocument(
  hospitalId: UUID,
  @Column(name = "type", nullable = false, length = 50)
  var type: String,
  // Named documentVersion: BaseEntity.version is the optimistic-lock @Version column.
  @Column(name = "doc_version", nullable = false)
  var documentVersion: Int,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content", columnDefinition = "jsonb", nullable = false)
  var content: JsonNode,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
) : HospitalScopedEntity(hospitalId)
