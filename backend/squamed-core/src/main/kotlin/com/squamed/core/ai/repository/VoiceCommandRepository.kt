package com.squamed.core.ai.repository

import com.squamed.core.ai.domain.VoiceCommand
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VoiceCommandRepository : JpaRepository<VoiceCommand, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): VoiceCommand?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<VoiceCommand>
}
