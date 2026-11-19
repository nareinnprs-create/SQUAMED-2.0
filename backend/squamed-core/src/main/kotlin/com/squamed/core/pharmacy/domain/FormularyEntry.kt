package com.squamed.core.pharmacy.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/** Per-hospital approved-drug list; drugId must reference an active master_data.drug row. */
@Entity
@Table(name = "formulary_entry", schema = "pharmacy")
class FormularyEntry(
  @Column(name = "hospital_id", nullable = false)
  var hospitalId: UUID,
  @Column(name = "drug_id", nullable = false)
  var drugId: UUID,
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean = true,
  @Column(name = "notes", length = 400)
  var notes: String? = null,
) : BaseEntity()
