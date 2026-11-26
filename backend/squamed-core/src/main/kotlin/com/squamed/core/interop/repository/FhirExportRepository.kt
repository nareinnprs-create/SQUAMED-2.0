package com.squamed.core.interop.repository

import com.squamed.core.interop.domain.FhirExport
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FhirExportRepository : JpaRepository<FhirExport, UUID> {
  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<FhirExport>

  fun findByHospitalIdAndPatientIdOrderByCreatedAtDesc(
    hospitalId: UUID,
    patientId: UUID,
  ): List<FhirExport>
}
