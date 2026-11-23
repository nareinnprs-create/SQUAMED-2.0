package com.squamed.core.ipd.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 04.08 dietary-lite: one active diet order per admission; the kitchen manifest reads these.
@Entity
@Table(name = "diet_order", schema = "ipd")
class DietOrder(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "admission_id", nullable = false) var admissionId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "diet_type", nullable = false, length = 60) var dietType: String,
  @Column(name = "restrictions", length = 400) var restrictions: String? = null,
  @Column(name = "notes", length = 400) var notes: String? = null,
  @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
  @Column(name = "ordered_by") var orderedBy: UUID? = null,
  @Column(name = "ordered_at", nullable = false) var orderedAt: Instant = Instant.now(),
) : BaseEntity()
