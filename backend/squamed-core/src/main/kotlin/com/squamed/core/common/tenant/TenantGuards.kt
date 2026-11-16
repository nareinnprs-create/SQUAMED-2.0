package com.squamed.core.common.tenant

import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.security.access.AccessDeniedException
import java.util.UUID

/**
 * ADR-13 stage-1 read guard (00.04 tenant sweep): hospital-bound users may only touch
 * rows of their own hospital; rows with a NULL hospital_id are not provably theirs and
 * are denied. Platform admins (principal.hospitalId == null) are intentionally unbounded
 * — same semantics as TenantResolver.pin(). Error code matches TenantInterceptor.
 */
fun requireSameHospital(
  principal: SquamedPrincipal?,
  entityHospitalId: UUID?,
) {
  requireSameHospital(principal?.hospitalId, entityHospitalId)
}

fun requireSameHospital(
  boundHospitalId: UUID?,
  entityHospitalId: UUID?,
) {
  if (boundHospitalId != null && entityHospitalId != boundHospitalId) {
    throw AccessDeniedException("TENANT_FORBIDDEN")
  }
}
