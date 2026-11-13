package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "patient_note", schema = "patient")
class PatientNote(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "author_id")
  var authorId: UUID? = null,
  @Column(name = "title", length = 200)
  var title: String? = null,
  @Column(name = "body")
  var body: String? = null,
  @Column(name = "is_flagged", nullable = false)
  var isFlagged: Boolean = false,
) : BaseEntity()
