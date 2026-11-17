package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.PatientDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatientDocumentRepository : JpaRepository<PatientDocument, UUID> {
  fun findByPatientIdOrderByCreatedAtDesc(patientId: UUID): List<PatientDocument>
}
