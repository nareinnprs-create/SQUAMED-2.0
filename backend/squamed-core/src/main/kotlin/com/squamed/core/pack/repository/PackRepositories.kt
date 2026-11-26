package com.squamed.core.pack.repository

import com.squamed.core.identity.domain.Permission
import com.squamed.core.pack.domain.PackComponent
import com.squamed.core.pack.domain.PackInstall
import com.squamed.core.pack.domain.SpecialtyPack
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface SpecialtyPackRepository : JpaRepository<SpecialtyPack, UUID> {
  fun findByCode(code: String): Optional<SpecialtyPack>

  fun findAllByOrderByCodeAsc(): List<SpecialtyPack>
}

interface PackComponentRepository : JpaRepository<PackComponent, UUID> {
  fun findByPackIdOrderByComponentTypeAsc(packId: UUID): List<PackComponent>
}

interface PackInstallRepository : JpaRepository<PackInstall, UUID> {
  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<PackInstall>

  fun findByHospitalIdAndPackIdAndPackVersion(
    hospitalId: UUID,
    packId: UUID,
    packVersion: Int,
  ): Optional<PackInstall>
}

/** Permissions are addressed by their wire code (`domain:action:resource`). */
interface PackPermissionRepository : JpaRepository<Permission, UUID> {
  @Query("select p from Permission p where p.domain = :domain and p.action = :action and p.resource = :resource")
  fun findByCode(
    @Param("domain") domain: String,
    @Param("action") action: String,
    @Param("resource") resource: String?,
  ): Optional<Permission>
}
