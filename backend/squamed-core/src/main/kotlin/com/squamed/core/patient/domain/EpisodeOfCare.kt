package com.squamed.core.patient.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "episode_of_care", schema = "patient")
class EpisodeOfCare(
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "period_type", nullable = false, length = 50)
  var periodType: String,
  @Column(name = "start_date")
  var startDate: LocalDate? = null,
  @Column(name = "end_date")
  var endDate: LocalDate? = null,
  @Column(name = "reason", length = 255)
  var reason: String? = null,
  @Column(name = "care_team", length = 255)
  var careTeam: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "active",
) : BaseEntity()
