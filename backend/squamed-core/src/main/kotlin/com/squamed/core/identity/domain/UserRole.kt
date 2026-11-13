package com.squamed.core.identity.domain

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_role", schema = "identity")
class UserRole(
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id", nullable = false)
  var role: Role,
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "department_id")
  var departmentId: UUID? = null,
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "scope", columnDefinition = "jsonb")
  var scope: JsonNode? = null,
  @Column(name = "valid_from")
  var validFrom: Instant? = null,
  @Column(name = "valid_until")
  var validUntil: Instant? = null,
) : BaseEntity() {
  fun isActiveAt(instant: Instant): Boolean {
    if (validFrom != null && instant.isBefore(validFrom)) return false
    if (validUntil != null && instant.isAfter(validUntil)) return false
    return true
  }
}
