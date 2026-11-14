package com.squamed.core.common.tenant

import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * ADR-13 stage 1: the tenant comes from the authenticated principal (JWT `hp` claim,
 * resolved at login from user_role assignments). Client-supplied hospital ids are only
 * honored for platform admins (principal.hospitalId == null).
 */
@Component
class TenantResolver(
  private val hospitalRepository: HospitalRepository,
) {
  /** Pin for reads and writes on tables that are already NOT NULL: hospital users are pinned, admins may choose. */
  fun pin(
    principal: SquamedPrincipal?,
    explicit: UUID?,
  ): UUID? = principal?.hospitalId ?: explicit

  /** Pin with primary-hospital fallback for writes on stage-1 NOT NULL columns. */
  fun pinOrDefault(
    principal: SquamedPrincipal?,
    explicit: UUID?,
  ): UUID = principal?.hospitalId ?: explicit ?: primaryHospitalId()

  @Transactional(readOnly = true)
  fun primaryHospitalId(): UUID =
    hospitalRepository.findFirstByOrderByCreatedAtAsc()?.id
      ?: throw IllegalStateException("NO_HOSPITAL_CONFIGURED")
}
