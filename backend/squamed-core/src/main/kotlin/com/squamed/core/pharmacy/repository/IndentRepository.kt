package com.squamed.core.pharmacy.repository

import com.squamed.core.pharmacy.domain.Indent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface IndentRepository : JpaRepository<Indent, UUID> {
  @Query("select i from Indent i where (:hid is null or i.hospitalId = :hid) order by i.createdAt desc")
  fun findAllForHospital(
    @Param("hid") hospitalId: UUID?,
  ): List<Indent>
}
