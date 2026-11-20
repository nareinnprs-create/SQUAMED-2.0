package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// 02.08 reorder proposals: min-level alerts become human-approved PO drafts (procurement is chunk 6).
@Entity
@Table(name = "reorder_proposal", schema = "pharmacy")
class ReorderProposal(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "drug_id")
  var drugId: UUID? = null,
  @Column(name = "drug_name", nullable = false, length = 200)
  var drugName: String,
  @Column(name = "stock_id")
  var stockId: UUID? = null,
  @Column(name = "requested_quantity", nullable = false)
  var requestedQuantity: Int,
  @Column(name = "status", nullable = false, length = 10)
  var status: String = "PROPOSED",
  @Column(name = "note", length = 400)
  var note: String? = null,
  @Column(name = "proposed_by")
  var proposedBy: UUID? = null,
  @Column(name = "decided_by")
  var decidedBy: UUID? = null,
  @Column(name = "decided_at")
  var decidedAt: Instant? = null,
) : BaseEntity()
