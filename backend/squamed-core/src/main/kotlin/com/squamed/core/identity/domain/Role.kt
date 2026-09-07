package com.squamed.core.identity.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "role", schema = "identity")
class Role(
  @Column(name = "name", nullable = false, unique = true, length = 100)
  var name: String,
  @Column(name = "display_name", nullable = false, length = 150)
  var displayName: String,
  @Column(name = "description")
  var description: String? = null,
  @Column(name = "is_system", nullable = false)
  var isSystem: Boolean = false,
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
    name = "role_permission",
    schema = "identity",
    joinColumns = [JoinColumn(name = "role_id")],
    inverseJoinColumns = [JoinColumn(name = "permission_id")],
  )
  var permissions: MutableSet<Permission> = mutableSetOf(),
) : BaseEntity()
