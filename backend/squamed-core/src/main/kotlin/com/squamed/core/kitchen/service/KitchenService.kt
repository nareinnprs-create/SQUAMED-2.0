package com.squamed.core.kitchen.service

import com.squamed.core.ipd.repository.DietOrderRepository
import com.squamed.core.kitchen.domain.KitchenProduction
import com.squamed.core.kitchen.domain.MenuItem
import com.squamed.core.kitchen.domain.TrayDelivery
import com.squamed.core.kitchen.repository.KitchenProductionRepository
import com.squamed.core.kitchen.repository.MenuItemRepository
import com.squamed.core.kitchen.repository.TrayDeliveryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class KitchenService(
  private val menuItemRepository: MenuItemRepository,
  private val productionRepository: KitchenProductionRepository,
  private val trayRepository: TrayDeliveryRepository,
  private val dietOrderRepository: DietOrderRepository,
) {
  private val meals = setOf("BREAKFAST", "LUNCH", "DINNER")

  @Transactional
  fun createMenuItem(
    hospitalId: UUID,
    dietType: String,
    meal: String,
    name: String,
    allergens: String?,
    actorId: UUID?,
  ): MenuItem {
    val normalizedMeal = meal.trim().uppercase()
    if (dietType.isBlank() || name.isBlank()) throw IllegalArgumentException("MENU_ITEM_FIELDS_REQUIRED")
    if (normalizedMeal !in meals) throw IllegalArgumentException("MENU_MEAL_INVALID")
    val diet = dietType.trim()
    if (menuItemRepository.findByHospitalIdAndDietTypeAndMealAndName(hospitalId, diet, normalizedMeal, name.trim()) != null) {
      throw IllegalArgumentException("MENU_ITEM_EXISTS")
    }
    return menuItemRepository.save(
      MenuItem(
        hospitalId = hospitalId,
        dietType = diet,
        meal = normalizedMeal,
        name = name.trim(),
        allergens = allergens?.trim(),
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional(readOnly = true)
  fun menuItems(
    hospitalId: UUID,
    meal: String?,
  ): List<MenuItem> {
    val rows = menuItemRepository.findByHospitalIdOrderByDietTypeAscMealAscNameAsc(hospitalId)
    return if (meal.isNullOrBlank()) rows else rows.filter { it.meal == meal.trim().uppercase() }
  }

  // Production counts derive from the active diet orders, then persist per diet type for kitchen status tracking.
  @Transactional
  fun generateProductionPlan(
    hospitalId: UUID,
    productionDate: LocalDate,
    meal: String,
    actorId: UUID?,
  ): List<KitchenProduction> {
    val normalizedMeal = meal.trim().uppercase()
    if (normalizedMeal !in meals) throw IllegalArgumentException("MENU_MEAL_INVALID")
    val counts = dietOrderRepository.findActiveForHospital(hospitalId).groupingBy { it.dietType.trim() }.eachCount()
    val existing = productionRepository.findByHospitalIdAndProductionDateAndMeal(hospitalId, productionDate, normalizedMeal)
    existing.filter { it.dietType !in counts.keys }.forEach {
      it.plannedCount = 0
      it.updatedBy = actorId
      productionRepository.save(it)
    }
    counts.forEach { (dietType, count) ->
      val row =
        productionRepository.findByHospitalIdAndProductionDateAndMealAndDietType(hospitalId, productionDate, normalizedMeal, dietType)
          ?: KitchenProduction(
            hospitalId = hospitalId,
            productionDate = productionDate,
            meal = normalizedMeal,
            dietType = dietType,
            plannedCount = count,
          ).also { it.createdBy = actorId }
      row.plannedCount = count
      row.updatedBy = actorId
      productionRepository.save(row)
    }
    return productionRepository.findByHospitalIdAndProductionDateAndMeal(hospitalId, productionDate, normalizedMeal).sortedBy { it.dietType }
  }

  @Transactional(readOnly = true)
  fun productionPlan(
    hospitalId: UUID,
    productionDate: LocalDate,
    meal: String,
  ): List<KitchenProduction> = productionRepository.findByHospitalIdAndProductionDateAndMeal(hospitalId, productionDate, meal.trim().uppercase())

  @Transactional
  fun startProduction(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): KitchenProduction {
    val row = findProduction(id, hospitalId)
    if (row.status != "PLANNED") throw IllegalStateException("KITCHEN_PRODUCTION_NOT_PLANNED")
    row.status = "IN_PREP"
    row.startedAt = Instant.now()
    row.updatedBy = actorId
    return productionRepository.save(row)
  }

  @Transactional
  fun markProductionReady(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): KitchenProduction {
    val row = findProduction(id, hospitalId)
    if (row.status != "IN_PREP") throw IllegalStateException("KITCHEN_PRODUCTION_NOT_STARTED")
    row.status = "READY"
    row.readyAt = Instant.now()
    row.updatedBy = actorId
    return productionRepository.save(row)
  }

  @Transactional
  fun prepareTray(
    hospitalId: UUID,
    dietOrderId: UUID,
    productionDate: LocalDate,
    meal: String,
    actorId: UUID?,
  ): TrayDelivery {
    val normalizedMeal = meal.trim().uppercase()
    if (normalizedMeal !in meals) throw IllegalArgumentException("MENU_MEAL_INVALID")
    val order = dietOrderRepository.findById(dietOrderId).orElseThrow { IllegalArgumentException("TRAY_DIET_ORDER_NOT_FOUND") }
    if (order.hospitalId != hospitalId || !order.isActive) throw IllegalArgumentException("TRAY_DIET_ORDER_INACTIVE")
    if (trayRepository.findByDietOrderIdAndProductionDateAndMeal(dietOrderId, productionDate, normalizedMeal) != null) {
      throw IllegalStateException("TRAY_EXISTS")
    }
    return trayRepository.save(
      TrayDelivery(
        hospitalId = hospitalId,
        dietOrderId = dietOrderId,
        patientId = order.patientId,
        productionDate = productionDate,
        meal = normalizedMeal,
      ).also { it.createdBy = actorId },
    )
  }

  @Transactional
  fun deliverTray(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): TrayDelivery {
    val tray = trayRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("TRAY_NOT_FOUND")
    if (tray.status == "DELIVERED") throw IllegalStateException("TRAY_ALREADY_DELIVERED")
    tray.status = "DELIVERED"
    tray.deliveredAt = Instant.now()
    tray.deliveredBy = actorId
    tray.updatedBy = actorId
    return trayRepository.save(tray)
  }

  @Transactional(readOnly = true)
  fun trays(
    hospitalId: UUID,
    productionDate: LocalDate,
    meal: String,
  ): List<TrayDelivery> = trayRepository.findByHospitalIdAndProductionDateAndMealOrderByCreatedAtAsc(hospitalId, productionDate, meal.trim().uppercase())

  private fun findProduction(
    id: UUID,
    hospitalId: UUID,
  ): KitchenProduction = productionRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("KITCHEN_PRODUCTION_NOT_FOUND")
}
