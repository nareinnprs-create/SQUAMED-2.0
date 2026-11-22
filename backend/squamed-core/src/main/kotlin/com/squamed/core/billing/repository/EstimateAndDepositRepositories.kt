package com.squamed.core.billing.repository

import com.squamed.core.billing.domain.Deposit
import com.squamed.core.billing.domain.Estimate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EstimateRepository : JpaRepository<Estimate, UUID> {
  @Query("select e from Estimate e where (:hid is null or e.hospitalId = :hid) and e.patientId = :patientId order by e.createdAt desc")
  fun findByPatient(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Estimate>

  @Query("select e from Estimate e where (:hid is null or e.hospitalId = :hid) order by e.createdAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Estimate>
}

interface DepositRepository : JpaRepository<Deposit, UUID> {
  @Query("select d from Deposit d where (:hid is null or d.hospitalId = :hid) and d.patientId = :patientId order by d.receivedAt desc")
  fun findByPatient(
    @Param("hid") hospitalId: UUID?,
    @Param("patientId") patientId: UUID,
  ): List<Deposit>

  @Query("select d from Deposit d where (:hid is null or d.hospitalId = :hid) order by d.receivedAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Deposit>
}
