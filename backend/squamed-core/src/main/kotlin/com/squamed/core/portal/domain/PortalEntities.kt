package com.squamed.core.portal.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "patient_account", schema = "portal")
class PortalAccount(
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "abha_number", length = 40)
  var abhaNumber: String? = null,
  @Column(name = "abha_address", length = 120)
  var abhaAddress: String? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "ACTIVE",
) : BaseEntity()

@Entity
@Table(name = "push_subscription", schema = "portal")
class PushSubscription(
  @Column(name = "user_id", nullable = false)
  var userId: UUID,
  @Column(name = "endpoint", nullable = false, length = 600)
  var endpoint: String,
  @Column(name = "p256dh", nullable = false, length = 300)
  var p256dh: String,
  @Column(name = "auth", nullable = false, length = 200)
  var auth: String,
) : BaseEntity()
