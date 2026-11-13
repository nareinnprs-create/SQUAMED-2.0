package com.squamed.core.common.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.UuidGenerator
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
  @Id
  @GeneratedValue
  @UuidGenerator
  @Column(name = "id", updatable = false, nullable = false)
  open var id: UUID? = null,
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  open var createdAt: Instant? = null,
  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  open var updatedAt: Instant? = null,
  @CreatedBy
  @Column(name = "created_by", updatable = false)
  open var createdBy: UUID? = null,
  @LastModifiedBy
  @Column(name = "updated_by")
  open var updatedBy: UUID? = null,
  @Version
  @Column(name = "version", nullable = false)
  open var version: Long = 0,
)
