package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Append-only stock movement log; trigger enforces balance_after = pharmacy_stock.quantity_on_hand. */
@Entity
@Table(name = "stock_ledger", schema = "pharmacy")
class StockLedger(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "stock_id", nullable = false)
  var stockId: UUID,
  @Column(name = "movement", nullable = false, length = 20)
  var movement: String,
  @Column(name = "quantity_delta", nullable = false)
  var quantityDelta: Int,
  @Column(name = "balance_after", nullable = false)
  var balanceAfter: Int,
  @Column(name = "reference_type", length = 30)
  var referenceType: String? = null,
  @Column(name = "reference_id")
  var referenceId: UUID? = null,
  @Column(name = "actor_id")
  var actorId: UUID? = null,
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant? = null,
) : BaseEntity()
