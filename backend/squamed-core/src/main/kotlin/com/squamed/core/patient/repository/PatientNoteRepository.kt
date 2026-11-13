package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientNote
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatientNoteRepository : JpaRepository<PatientNote, UUID> {
  fun findByPatientId(patientId: UUID): List<PatientNote>
}
