package com.squamed.core.task.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** Chunk item 9 subject attachment: patient, encounter/invoice placeholder, department. */
@Entity
@Table(name = "task_link", schema = "tasks")
class TaskLink(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "task_id", nullable = false)
  var taskId: UUID,
  @Column(name = "subject_type", nullable = false, length = 30)
  var subjectType: String,
  @Column(name = "subject_id", nullable = false)
  var subjectId: UUID,
) : BaseEntity() {
  companion object {
    /** The subject types named by chunk item 9 (encounter/invoice are placeholders until those chunks land). */
    val SUBJECT_TYPES: Set<String> = setOf("patient", "encounter", "invoice", "department")
  }
}
