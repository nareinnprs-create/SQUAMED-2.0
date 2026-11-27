package com.squamed.core.twin.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "capacity_snapshot", schema = "twin")
class CapacitySnapshot(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "generated_at", nullable = false) var generatedAt: Instant = Instant.now(),
  @Column(name = "beds_total", nullable = false) var bedsTotal: Int = 0,
  @Column(name = "beds_available", nullable = false) var bedsAvailable: Int = 0,
  @Column(name = "beds_occupied", nullable = false) var bedsOccupied: Int = 0,
  @Column(name = "ot_rooms_total", nullable = false) var otRoomsTotal: Int = 0,
  @Column(name = "ot_rooms_free", nullable = false) var otRoomsFree: Int = 0,
  @Column(name = "admissions_active", nullable = false) var admissionsActive: Int = 0,
  @Column(name = "expected_discharges_today", nullable = false) var expectedDischargesToday: Int = 0,
  @Column(name = "diagnostics_pending", nullable = false) var diagnosticsPending: Int = 0,
  @Column(name = "equipment_down", nullable = false) var equipmentDown: Int = 0,
  @Column(name = "open_work_orders", nullable = false) var openWorkOrders: Int = 0,
  @Column(name = "staff_on_duty", nullable = false) var staffOnDuty: Int = 0,
  @Column(name = "forecast_admissions_next_24h", nullable = false) var forecastAdmissionsNext24h: BigDecimal = BigDecimal.ZERO,
) : BaseEntity()
