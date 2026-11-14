package com.squamed.core.task.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "task", schema = "tasks")
class Task(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "title", nullable = false, length = 255)
  var title: String,
  @Column(name = "description")
  var description: String? = null,
  @Column(name = "assignee_id")
  var assigneeId: UUID? = null,
  @Column(name = "assigner_id")
  var assignerId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "pending",
  @Column(name = "priority", nullable = false, length = 20)
  var priority: String = "normal",
  @Column(name = "due_at")
  var dueAt: Instant? = null,
  @Column(name = "related_type", length = 50)
  var relatedType: String? = null,
  @Column(name = "related_id")
  var relatedId: UUID? = null,
  @Column(name = "completed_at")
  var completedAt: Instant? = null,
  @Column(name = "escalated_at")
  var escalatedAt: Instant? = null,
  @Column(name = "escalation_count", nullable = false)
  var escalationCount: Int = 0,
) : BaseEntity()
