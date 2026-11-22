package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.BillingPackage
import com.squamed.core.billing.domain.PackageVersion
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BillingPackageRepository : JpaRepository<BillingPackage, UUID> {
  fun findByCode(code: String): BillingPackage?

  fun findAllByIsActiveTrue(): List<BillingPackage>
}

interface PackageVersionRepository : JpaRepository<PackageVersion, UUID> {
  fun findByPackageIdOrderByPackageVersionDesc(packageId: UUID): List<PackageVersion>
}
