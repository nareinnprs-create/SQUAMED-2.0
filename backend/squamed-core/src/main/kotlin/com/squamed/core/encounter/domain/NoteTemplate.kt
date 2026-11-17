package com.squamed.core.encounter.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** 01.04 SOAP note template (global catalog, hospital_id NULL; per-hospital rows may shadow). */
@Entity
@Table(name = "note_template", schema = "clinical")
class NoteTemplate(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "specialty", nullable = false, length = 80)
  var specialty: String,
  @Column(name = "name", nullable = false, length = 120)
  var name: String,
  @Column(name = "note_type", nullable = false, length = 30)
  var noteType: String = "progress",
  @Column(name = "subjective_template", columnDefinition = "text")
  var subjectiveTemplate: String? = null,
  @Column(name = "objective_template", columnDefinition = "text")
  var objectiveTemplate: String? = null,
  @Column(name = "assessment_template", columnDefinition = "text")
  var assessmentTemplate: String? = null,
  @Column(name = "plan_template", columnDefinition = "text")
  var planTemplate: String? = null,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
) : BaseEntity()
