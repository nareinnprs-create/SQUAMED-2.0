package com.squamed.core.identity.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "permission", schema = "identity")
class Permission(
  @Column(name = "domain", nullable = false, length = 100)
  var domain: String,
  @Column(name = "action", nullable = false, length = 50)
  var action: String,
  @Column(name = "resource", length = 150)
  var resource: String? = null,
  @Column(name = "description")
  var description: String? = null,
) : BaseEntity() {
  val code: String
    get() = if (resource != null) "$domain:$action:$resource" else "$domain:$action"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Permission) return false
    return id != null && id == other.id
  }

  override fun hashCode(): Int = id?.hashCode() ?: 0
}
