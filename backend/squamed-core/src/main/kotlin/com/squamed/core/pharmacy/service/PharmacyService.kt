package com.squamed.core.pharmacy.service

import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.pharmacy.api.DispensationView
import com.squamed.core.pharmacy.api.DispenseRequestDto
import com.squamed.core.pharmacy.api.PharmacyStockView
import com.squamed.core.pharmacy.api.StockRequestDto
import com.squamed.core.pharmacy.api.StockUpdateDto
import com.squamed.core.pharmacy.domain.Dispensation
import com.squamed.core.pharmacy.domain.PharmacyStock
import com.squamed.core.pharmacy.repository.DispensationRepository
import com.squamed.core.pharmacy.repository.PharmacyStockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class PharmacyService(
  private val pharmacyStockRepository: PharmacyStockRepository,
  private val dispensationRepository: DispensationRepository,
  private val eventPublisher: DomainEventPublisher,
  private val tenantResolver: TenantResolver,
) {
  @Transactional
  fun addStock(dto: StockRequestDto): PharmacyStockView {
    val hospitalId = dto.hospitalId ?: tenantResolver.primaryHospitalId()
    val drugId = dto.drugId
    val existing =
      if (drugId != null) {
        pharmacyStockRepository.findByDrugId(drugId).firstOrNull {
          it.hospitalId == hospitalId && it.batchNumber == dto.batchNumber
        }
      } else {
        null
      }
    if (existing != null) {
      existing.quantityOnHand += dto.quantityOnHand ?: 0
      return PharmacyStockView.from(pharmacyStockRepository.save(existing))
    }
    val stock =
      pharmacyStockRepository.save(
        PharmacyStock(
          hospitalId = hospitalId,
          drugId = drugId,
          drugCode = dto.drugCode,
          drugName = dto.drugName,
          batchNumber = dto.batchNumber,
          expiryDate = dto.expiryDate,
          quantityOnHand = dto.quantityOnHand ?: 0,
          reorderLevel = dto.reorderLevel ?: 10,
          unitPrice = dto.unitPrice ?: BigDecimal.ZERO,
          location = dto.location,
          isActive = dto.isActive ?: true,
        ),
      )
    return PharmacyStockView.from(stock)
  }

  @Transactional(readOnly = true)
  fun listStock(
    search: String? = null,
    activeOnly: Boolean? = null,
  ): List<PharmacyStockView> {
    val stocks =
      when {
        search != null && activeOnly == true -> {
          pharmacyStockRepository.findByDrugNameContainingIgnoreCase(search).filter { it.isActive }
        }

        activeOnly == true -> {
          pharmacyStockRepository.findByIsActiveTrue()
        }

        search != null -> {
          pharmacyStockRepository.findByDrugNameContainingIgnoreCase(search)
        }

        else -> {
          pharmacyStockRepository.findAll()
        }
      }
    return stocks.sortedBy { it.drugName }.map { PharmacyStockView.from(it) }
  }

  @Transactional
  fun updateStock(
    stockId: UUID,
    dto: StockUpdateDto,
  ): PharmacyStockView {
    val stock =
      pharmacyStockRepository
        .findById(stockId)
        .orElseThrow { IllegalArgumentException("STOCK_NOT_FOUND") }
    stock.drugId = dto.drugId ?: stock.drugId
    stock.drugCode = dto.drugCode ?: stock.drugCode
    stock.drugName = dto.drugName ?: stock.drugName
    stock.batchNumber = dto.batchNumber ?: stock.batchNumber
    stock.expiryDate = dto.expiryDate ?: stock.expiryDate
    val quantity = dto.quantityOnHand
    if (quantity != null) {
      stock.quantityOnHand = quantity
    }
    stock.reorderLevel = dto.reorderLevel ?: stock.reorderLevel
    stock.unitPrice = dto.unitPrice ?: stock.unitPrice
    stock.location = dto.location ?: stock.location
    stock.isActive = dto.isActive ?: stock.isActive
    return PharmacyStockView.from(pharmacyStockRepository.save(stock))
  }

  @Transactional
  fun dispense(
    dto: DispenseRequestDto,
    dispensedBy: UUID,
    hospitalId: UUID? = null,
  ): DispensationView {
    val unitPrice = dto.unitPrice ?: BigDecimal.ZERO
    val drugId = dto.drugId
    val stockId = dto.stockId
    if (stockId != null || drugId != null) {
      val stock =
        if (stockId != null) {
          pharmacyStockRepository.findById(stockId).orElseThrow { IllegalArgumentException("STOCK_NOT_FOUND") }
        } else {
          pharmacyStockRepository.findByDrugId(drugId!!).firstOrNull()
            ?: throw IllegalArgumentException("STOCK_NOT_FOUND")
        }
      if (stock.quantityOnHand < dto.quantity) {
        throw IllegalArgumentException("INSUFFICIENT_STOCK")
      }
      stock.quantityOnHand -= dto.quantity
      pharmacyStockRepository.save(stock)
    }
    val dispensation =
      dispensationRepository.save(
        Dispensation(
          hospitalId = hospitalId,
          prescriptionId = dto.prescriptionId,
          patientId = dto.patientId,
          encounterId = dto.encounterId,
          drugId = drugId,
          drugName = dto.drugName,
          quantity = dto.quantity,
          status = "dispensed",
          dispensedBy = dispensedBy,
          dispensedAt = Instant.now(),
          unitPrice = unitPrice,
          totalPrice = unitPrice.multiply(BigDecimal.valueOf(dto.quantity.toLong())),
          notes = dto.notes,
        ),
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "prescription.dispensed",
        occurredAt = Instant.now(),
        source = "pharmacy-service",
        entityType = "dispensation",
        entityId = dispensation.id,
        hospitalId = hospitalId,
        payload = mapOf("prescriptionId" to dto.prescriptionId),
      ),
    )
    return DispensationView.from(dispensation)
  }

  @Transactional
  fun verifyDispensation(
    dispId: UUID,
    verifierId: UUID,
  ): DispensationView {
    val dispensation =
      dispensationRepository
        .findById(dispId)
        .orElseThrow { IllegalArgumentException("DISPENSATION_NOT_FOUND") }
    if (dispensation.status != "dispensed") {
      throw IllegalArgumentException("DISPENSATION_NOT_DISPENSED")
    }
    dispensation.status = "verified"
    dispensation.verifiedBy = verifierId
    dispensation.verifiedAt = Instant.now()
    return DispensationView.from(dispensationRepository.save(dispensation))
  }

  @Transactional
  fun returnOrCancel(
    dispId: UUID,
    reason: String? = null,
  ): DispensationView {
    val dispensation =
      dispensationRepository
        .findById(dispId)
        .orElseThrow { IllegalArgumentException("DISPENSATION_NOT_FOUND") }
    val restock: Boolean
    when (dispensation.status) {
      "pending" -> {
        dispensation.status = "cancelled"
        restock = false
      }

      "dispensed", "verified" -> {
        dispensation.status = "returned"
        restock = true
      }

      else -> {
        throw IllegalArgumentException("DISPENSATION_NOT_RETURNABLE")
      }
    }
    reason?.let {
      dispensation.notes = listOfNotNull(dispensation.notes, it).joinToString(" | ")
    }
    if (restock) {
      val drugId = dispensation.drugId
      if (drugId != null) {
        val stock =
          pharmacyStockRepository.findByDrugId(drugId).firstOrNull()
            ?: throw IllegalArgumentException("STOCK_NOT_FOUND")
        stock.quantityOnHand += dispensation.quantity
        pharmacyStockRepository.save(stock)
      }
    }
    return DispensationView.from(dispensationRepository.save(dispensation))
  }

  @Transactional(readOnly = true)
  fun listDispensations(
    patientId: UUID? = null,
    status: String? = null,
  ): List<DispensationView> {
    val dispensations =
      when {
        patientId != null && status != null -> {
          dispensationRepository.findByPatientId(patientId).filter { it.status == status }
        }

        patientId != null -> {
          dispensationRepository.findByPatientId(patientId)
        }

        status != null -> {
          dispensationRepository.findByStatus(status)
        }

        else -> {
          dispensationRepository.findAll()
        }
      }
    return dispensations
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { DispensationView.from(it) }
  }
}
