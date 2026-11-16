package com.squamed.core.scheduling.repository

import com.squamed.core.scheduling.domain.SessionSlot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

interface SessionSlotRepository : JpaRepository<SessionSlot, UUID> {
  fun findByHospitalIdAndDoctorIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
    hospitalId: UUID,
    doctorId: UUID,
    fromDate: LocalDate,
    toDate: LocalDate,
  ): List<SessionSlot>

  fun findByHospitalIdAndDoctorIdAndSlotDateAndStartTime(
    hospitalId: UUID,
    doctorId: UUID,
    slotDate: LocalDate,
    startTime: LocalTime,
  ): SessionSlot?

  // Pessimistic re-read for booking: serialize concurrent slot consumption on the row.
  @Query("select s from SessionSlot s where s.id = :id")
  fun findByIdForBooking(
    @Param("id") id: UUID,
  ): SessionSlot?
}
