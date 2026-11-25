package com.squamed.core.laboratory.repository

import com.squamed.core.laboratory.domain.MicroAst
import com.squamed.core.laboratory.domain.MicroCulture
import com.squamed.core.laboratory.domain.PathSlide
import com.squamed.core.laboratory.domain.PathSpecimen
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MicroCultureRepository : JpaRepository<MicroCulture, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): MicroCulture?

  fun findByLabOrderId(labOrderId: UUID): MicroCulture?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<MicroCulture>

  fun findByHospitalIdAndStatusOrderByCreatedAtDesc(
    hospitalId: UUID,
    status: String,
  ): List<MicroCulture>

  fun findByHospitalIdAndOrganismCodeAndStatusAndFinalizedAtBetween(
    hospitalId: UUID,
    organismCode: String,
    status: String,
    from: Instant,
    to: Instant,
  ): List<MicroCulture>
}

interface MicroAstRepository : JpaRepository<MicroAst, UUID> {
  fun findByCultureIdOrderByAntibioticNameAsc(cultureId: UUID): List<MicroAst>

  fun findByCultureIdInOrderByAntibioticNameAsc(cultureIds: Collection<UUID>): List<MicroAst>

  fun findByCultureIdAndAntibioticCode(
    cultureId: UUID,
    antibioticCode: String,
  ): MicroAst?

  fun countByCultureId(cultureId: UUID): Long
}

interface PathSpecimenRepository : JpaRepository<PathSpecimen, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): PathSpecimen?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<PathSpecimen>

  fun findTopByOrderByAccessionNoDesc(): PathSpecimen?
}

interface PathSlideRepository : JpaRepository<PathSlide, UUID> {
  fun findByIdAndSpecimenId(
    id: UUID,
    specimenId: UUID,
  ): PathSlide?

  fun findBySpecimenIdOrderBySlideNoAsc(specimenId: UUID): List<PathSlide>

  fun countBySpecimenId(specimenId: UUID): Long
}
