package com.squamed.core.communication.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "channel", schema = "communication")
class Channel(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "name", nullable = false, length = 160)
  var name: String,
  @Column(name = "type", nullable = false, length = 30)
  var type: String = "direct",
  @Column(name = "description")
  var description: String? = null,
  @Column(name = "owner_id")
  var ownerId: UUID? = null,
  @Column(name = "patient_id")
  var patientId: UUID? = null,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "is_archived", nullable = false)
  var isArchived: Boolean = false,
) : BaseEntity()
