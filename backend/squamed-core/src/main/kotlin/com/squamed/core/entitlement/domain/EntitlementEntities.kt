package com.squamed.core.entitlement.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

// 01.10: a plan is a named feature set; features are a comma list so the check is a set lookup.
@Entity
@Table(name = "plan", schema = "entitlement")
class EntitlementPlan(
  @Column(name = "code", nullable = false, length = 40)
  var code: String,
  @Column(name = "name", nullable = false, length = 120)
  var name: String,
  @Column(name = "description", length = 300)
  var description: String? = null,
  @Column(name = "features", nullable = false, columnDefinition = "TEXT")
  var features: String,
  @Column(name = "active", nullable = false)
  var active: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "subscription", schema = "entitlement")
class EntitlementSubscription(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "plan_id", nullable = false)
  var planId: UUID,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "ACTIVE",
  @Column(name = "valid_from", nullable = false)
  var validFrom: LocalDate = LocalDate.now(),
  @Column(name = "valid_to")
  var validTo: LocalDate? = null,
) : BaseEntity()
