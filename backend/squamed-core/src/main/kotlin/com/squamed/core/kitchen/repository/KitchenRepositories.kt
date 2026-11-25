package com.squamed.core.kitchen.repository

import com.squamed.core.kitchen.domain.KitchenProduction
import com.squamed.core.kitchen.domain.MenuItem
import com.squamed.core.kitchen.domain.TrayDelivery
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface MenuItemRepository : JpaRepository<MenuItem, UUID> {
  fun findByHospitalIdAndDietTypeAndMealAndName(
    hospitalId: UUID,
    dietType: String,
    meal: String,
    name: String,
  ): MenuItem?

  fun findByHospitalIdOrderByDietTypeAscMealAscNameAsc(hospitalId: UUID): List<MenuItem>
}

interface KitchenProductionRepository : JpaRepository<KitchenProduction, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): KitchenProduction?

  fun findByHospitalIdAndProductionDateAndMeal(
    hospitalId: UUID,
    productionDate: LocalDate,
    meal: String,
  ): List<KitchenProduction>

  fun findByHospitalIdAndProductionDateAndMealAndDietType(
    hospitalId: UUID,
    productionDate: LocalDate,
    meal: String,
    dietType: String,
  ): KitchenProduction?
}

interface TrayDeliveryRepository : JpaRepository<TrayDelivery, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): TrayDelivery?

  fun findByHospitalIdAndProductionDateAndMealOrderByCreatedAtAsc(
    hospitalId: UUID,
    productionDate: LocalDate,
    meal: String,
  ): List<TrayDelivery>

  fun findByDietOrderIdAndProductionDateAndMeal(
    dietOrderId: UUID,
    productionDate: LocalDate,
    meal: String,
  ): TrayDelivery?
}
