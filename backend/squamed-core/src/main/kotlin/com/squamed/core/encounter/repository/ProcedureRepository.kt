package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.ProcedureEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProcedureRepository : JpaRepository<ProcedureEntry, UUID> {
  fun findByEncounterIdOrderByCreatedAtDesc(encounterId: UUID): List<ProcedureEntry>
}
