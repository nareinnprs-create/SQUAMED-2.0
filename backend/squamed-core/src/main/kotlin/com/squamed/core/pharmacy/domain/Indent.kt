package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Store requisition: requested → received (adds stock via ledger) or cancelled. */
@Entity
@Table(name = "indent", schema = "pharmacy")
class Indent(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "drug_id")
  var drugId: UUID? = null,
  @Column(name = "drug_name", nullable = false, length = 255)
  var drugName: String,
  @Column(name = "quantity_requested", nullable = false)
  var quantityRequested: Int,
  @Column(name = "quantity_received", nullable = false)
  var quantityReceived: Int = 0,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "requested",
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "requested_by")
  var requestedBy: UUID? = null,
  @Column(name = "received_at")
  var receivedAt: Instant? = null,
) : BaseEntity()
