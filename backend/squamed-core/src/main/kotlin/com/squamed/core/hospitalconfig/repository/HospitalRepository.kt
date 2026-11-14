package com.squamed.core.hospitalconfig.repository

import com.squamed.core.hospitalconfig.domain.Hospital
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HospitalRepository : JpaRepository<Hospital, UUID> {
  fun findFirstByOrderByCreatedAtAsc(): Hospital?
}
