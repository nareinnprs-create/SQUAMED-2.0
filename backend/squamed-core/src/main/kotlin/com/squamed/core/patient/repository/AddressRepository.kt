package com.squamed.core.patient.repository

import com.squamed.core.patient.domain.Address
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AddressRepository : JpaRepository<Address, UUID> {
  fun findByPatientId(patientId: UUID): List<Address>
}
