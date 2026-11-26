package com.squamed.core.migration.service

import com.squamed.core.patient.api.PatientCreateDto
import com.squamed.core.patient.service.PatientService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Each migrated row commits or rolls back on its own, so one bad row cannot poison the batch.
@Service
class MigrationRowImporter(
  private val patientService: PatientService,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun importPatient(dto: PatientCreateDto): UUID = checkNotNull(patientService.create(dto).id)
}
