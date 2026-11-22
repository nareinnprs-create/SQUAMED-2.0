package com.squamed.core.billing.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "charge", schema = "billing")
class Charge(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "visit_id")
  var visitId: UUID? = null,
  @Column(name = "encounter_id")
  var encounterId: UUID? = null,
  @Column(name = "patient_id", nullable = false)
  var patientId: UUID,
  @Column(name = "charge_code", length = 50)
  var chargeCode: String? = null,
  @Column(name = "charge_name", nullable = false, length = 255)
  var chargeName: String,
  @Column(name = "category", length = 100)
  var category: String? = null,
  @Column(name = "quantity", nullable = false)
  var quantity: Int = 1,
  @Column(name = "unit_price_qar", nullable = false, precision = 12, scale = 2)
  var unitPriceQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "total_qar", nullable = false, precision = 12, scale = 2)
  var totalQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "posted",
  @Column(name = "insurance_claimable", nullable = false)
  var insuranceClaimable: Boolean = true,
  @Column(name = "posted_by")
  var postedBy: UUID? = null,
  @Column(name = "posted_at", nullable = false)
  var postedAt: Instant = Instant.now(),
  @Column(name = "reference_type", length = 30)
  var referenceType: String? = null,
  @Column(name = "reference_id")
  var referenceId: UUID? = null,
  // 03.01: event-driven ledger row (idempotency key) vs manual entry (reason mandatory).
  @Column(name = "source_event_id")
  var sourceEventId: UUID? = null,
  @Column(name = "entry_mode", nullable = false, length = 10)
  var entryMode: String = "MANUAL",
  @Column(name = "entry_reason", length = 400)
  var entryReason: String? = null,
  // 03.02: the package version pinned at capture time (never re-resolved at bill time).
  @Column(name = "package_version_id")
  var packageVersionId: UUID? = null,
) : BaseEntity()
