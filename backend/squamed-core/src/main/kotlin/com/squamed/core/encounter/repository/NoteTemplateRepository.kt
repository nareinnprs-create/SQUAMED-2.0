package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.NoteTemplate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NoteTemplateRepository : JpaRepository<NoteTemplate, UUID> {
  fun findByIsActiveTrueOrderBySpecialtyAsc(): List<NoteTemplate>
}
