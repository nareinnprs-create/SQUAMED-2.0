package com.squamed.core.cx.repository

import com.squamed.core.cx.domain.ContactInteraction
import com.squamed.core.cx.domain.IntlCase
import com.squamed.core.cx.domain.PatientFeedback
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContactInteractionRepository : JpaRepository<ContactInteraction, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): ContactInteraction?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<ContactInteraction>
}

interface IntlCaseRepository : JpaRepository<IntlCase, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): IntlCase?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<IntlCase>
}

interface PatientFeedbackRepository : JpaRepository<PatientFeedback, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): PatientFeedback?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<PatientFeedback>
}
