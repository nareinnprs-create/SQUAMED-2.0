package com.squamed.core.common.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class HospitalScopedEntity(
  @Column(name = "hospital_id", nullable = false)
  open var hospitalId: UUID,
) : BaseEntity()
