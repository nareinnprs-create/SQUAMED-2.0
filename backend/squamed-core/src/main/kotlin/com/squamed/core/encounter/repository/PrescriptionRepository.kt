package com.squamed.core.encounter.repository

import com.squamed.core.encounter.domain.Prescription
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface PrescriptionRepository : JpaRepository<Prescription, UUID> {
  fun findByEncounterId(encounterId: UUID): List<Prescription>

  fun findByPatientId(patientId: UUID): List<Prescription>

  // 02.08: serialize concurrent dispenses against one eRx so the remaining-quantity check holds.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Prescription p where p.id = :id")
  fun findByIdForUpdate(
    @Param("id") id: UUID,
  ): Optional<Prescription>
}
