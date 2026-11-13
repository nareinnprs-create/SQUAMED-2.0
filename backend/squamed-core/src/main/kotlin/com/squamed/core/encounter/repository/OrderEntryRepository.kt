package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.OrderEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderEntryRepository : JpaRepository<OrderEntry, UUID> {
  fun findByEncounterId(encounterId: UUID): List<OrderEntry>

  fun findByPatientId(patientId: UUID): List<OrderEntry>

  fun findByStatus(status: String): List<OrderEntry>
}
