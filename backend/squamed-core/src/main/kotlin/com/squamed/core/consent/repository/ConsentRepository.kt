package com.squamed.core.consent.repository

import com.squamed.core.consent.domain.Consent
import com.squamed.core.consent.domain.ConsentChannelOptIn
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConsentRepository : JpaRepository<Consent, UUID> {
  fun findByPatientId(patientId: UUID): List<Consent>

  /** Tenant-pinned patient history (stage-1: explicit JPQL so a null hospital leaks nothing). */
  @org.springframework.data.jpa.repository.Query(
    "select c from Consent c where c.patientId = :patientId " +
      "and (:hospitalId is null or c.hospitalId = :hospitalId) order by c.createdAt desc",
  )
  fun findByPatient(
    @org.springframework.data.repository.query.Param("patientId") patientId: UUID,
    @org.springframework.data.repository.query.Param("hospitalId") hospitalId: UUID?,
  ): List<Consent>
}

interface ConsentChannelOptInRepository : JpaRepository<ConsentChannelOptIn, UUID> {
  fun findByConsentId(consentId: UUID): List<ConsentChannelOptIn>
}
