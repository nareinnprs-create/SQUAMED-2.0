package com.squamed.core.him.repository

import com.squamed.core.him.domain.CoderQuery
import com.squamed.core.him.domain.CodingAssignment
import com.squamed.core.him.domain.IntegrityFinding
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CodingAssignmentRepository : JpaRepository<CodingAssignment, UUID> {
  fun findByEncounterId(encounterId: UUID): CodingAssignment?

  @Query("select c from CodingAssignment c where (:hid is null or c.hospitalId = :hid) order by c.codedAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<CodingAssignment>
}

interface CoderQueryRepository : JpaRepository<CoderQuery, UUID> {
  @Query("select q from CoderQuery q where (:hid is null or q.hospitalId = :hid) and (:status is null or q.status = :status) order by q.raisedAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String?,
  ): List<CoderQuery>
}

interface IntegrityFindingRepository : JpaRepository<IntegrityFinding, UUID> {
  fun findByDedupeKey(dedupeKey: String): IntegrityFinding?

  @Query("select f from IntegrityFinding f where (:hid is null or f.hospitalId = :hid) and (:status is null or f.status = :status) order by f.detectedAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
    @Param("status") status: String?,
  ): List<IntegrityFinding>
}
