package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.ClinicalNote
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ClinicalNoteRepository : JpaRepository<ClinicalNote, UUID> {
  fun findByEncounterId(encounterId: UUID): List<ClinicalNote>

  fun findByEncounterIdAndIsSigned(
    encounterId: UUID,
    isSigned: Boolean,
  ): List<ClinicalNote>
}
