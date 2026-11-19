package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.ControlledRegisterEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ControlledRegisterRepository : JpaRepository<ControlledRegisterEntry, UUID> {
  @Query("select c from ControlledRegisterEntry c where (:hid is null or c.hospitalId = :hid) order by c.occurredAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<ControlledRegisterEntry>
}
