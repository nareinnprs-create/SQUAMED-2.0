package com.squamed.core.kitchen.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// 07.09 full food service: nutrition-linked menu catalogue.
@Entity
@Table(name = "menu_item", schema = "ipd")
class MenuItem(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "diet_type", nullable = false, length = 60) var dietType: String,
  @Column(name = "meal", nullable = false, length = 20) var meal: String,
  @Column(name = "name", nullable = false, length = 120) var name: String,
  @Column(name = "allergens", length = 400) var allergens: String? = null,
  @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
) : BaseEntity()

// 07.09 kitchen production plan, one row per diet type for a date+meal.
@Entity
@Table(name = "kitchen_production", schema = "ipd")
class KitchenProduction(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "production_date", nullable = false) var productionDate: LocalDate,
  @Column(name = "meal", nullable = false, length = 20) var meal: String,
  @Column(name = "diet_type", nullable = false, length = 60) var dietType: String,
  @Column(name = "planned_count", nullable = false) var plannedCount: Int,
  @Column(name = "status", nullable = false, length = 20) var status: String = "PLANNED",
  @Column(name = "started_at") var startedAt: Instant? = null,
  @Column(name = "ready_at") var readyAt: Instant? = null,
) : BaseEntity()

// 07.09 tray delivery tracking per active diet order.
@Entity
@Table(name = "tray_delivery", schema = "ipd")
class TrayDelivery(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "diet_order_id", nullable = false) var dietOrderId: UUID,
  @Column(name = "patient_id", nullable = false) var patientId: UUID,
  @Column(name = "production_date", nullable = false) var productionDate: LocalDate,
  @Column(name = "meal", nullable = false, length = 20) var meal: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "PREPARED",
  @Column(name = "prepared_at", nullable = false) var preparedAt: Instant = Instant.now(),
  @Column(name = "delivered_at") var deliveredAt: Instant? = null,
  @Column(name = "delivered_by") var deliveredBy: UUID? = null,
) : BaseEntity()
