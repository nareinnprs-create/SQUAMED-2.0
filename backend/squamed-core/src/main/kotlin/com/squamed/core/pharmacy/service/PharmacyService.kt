package com.squamed.core.pharmacy.service

import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.common.tenant.requireSameHospital
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.masterdata.domain.Drug
import com.squamed.core.masterdata.repository.DrugRepository
import com.squamed.core.pharmacy.api.ControlledRegisterView
import com.squamed.core.pharmacy.api.DispensationView
import com.squamed.core.pharmacy.api.DispenseRequestDto
import com.squamed.core.pharmacy.api.FormularyEntryView
import com.squamed.core.pharmacy.api.FormularyRequestDto
import com.squamed.core.pharmacy.api.IndentReceiveRequestDto
import com.squamed.core.pharmacy.api.IndentRequestDto
import com.squamed.core.pharmacy.api.IndentView
import com.squamed.core.pharmacy.api.PharmacyStockView
import com.squamed.core.pharmacy.api.StockLedgerView
import com.squamed.core.pharmacy.api.StockRequestDto
import com.squamed.core.pharmacy.api.StockUpdateDto
import com.squamed.core.pharmacy.api.WastageRequestDto
import com.squamed.core.pharmacy.domain.ControlledRegisterEntry
import com.squamed.core.pharmacy.domain.Dispensation
import com.squamed.core.pharmacy.domain.FormularyEntry
import com.squamed.core.pharmacy.domain.Indent
import com.squamed.core.pharmacy.domain.PharmacyStock
import com.squamed.core.pharmacy.domain.StockLedger
import com.squamed.core.pharmacy.repository.ControlledRegisterRepository
import com.squamed.core.pharmacy.repository.DispensationRepository
import com.squamed.core.pharmacy.repository.FormularyEntryRepository
import com.squamed.core.pharmacy.repository.IndentRepository
import com.squamed.core.pharmacy.repository.PharmacyStockRepository
import com.squamed.core.pharmacy.repository.StockLedgerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class PharmacyService(
  private val pharmacyStockRepository: PharmacyStockRepository,
  private val dispensationRepository: DispensationRepository,
  private val stockLedgerRepository: StockLedgerRepository,
  private val formularyEntryRepository: FormularyEntryRepository,
  private val indentRepository: IndentRepository,
  private val controlledRegisterRepository: ControlledRegisterRepository,
  private val drugRepository: DrugRepository,
  private val eventPublisher: DomainEventPublisher,
  private val tenantResolver: TenantResolver,
) {
  // 02.06: near-expiry BLOCK window for dispensing (days).
  private val nearExpiryDays = 30L

  // 02.06: drug-master activation gate — stock/indent/dispense may only reference active drugs.
  private fun requireActiveDrug(drugId: UUID): Drug {
    val drug = drugRepository.findById(drugId).orElseThrow { IllegalArgumentException("DRUG_NOT_FOUND") }
    if (!drug.isActive) {
      throw IllegalArgumentException("DRUG_INACTIVE")
    }
    return drug
  }

  // 02.06: every quantity mutation appends a ledger row; the DB trigger enforces
  // balance_after == pharmacy_stock.quantity_on_hand, so callers must saveAndFlush the
  // stock row before calling this.
  private fun recordLedger(
    stock: PharmacyStock,
    movement: String,
    delta: Int,
    referenceType: String?,
    referenceId: UUID?,
    note: String?,
    actorId: UUID?,
  ) {
    stockLedgerRepository.save(
      StockLedger(
        hospitalId = stock.hospitalId,
        stockId = stock.id ?: throw IllegalArgumentException("STOCK_NOT_PERSISTED"),
        movement = movement,
        quantityDelta = delta,
        balanceAfter = stock.quantityOnHand,
        referenceType = referenceType,
        referenceId = referenceId,
        actorId = actorId,
        note = note,
        occurredAt = Instant.now(),
      ),
    )
  }

  @Transactional
  fun addStock(
    dto: StockRequestDto,
    principal: SquamedPrincipal,
  ): PharmacyStockView {
    // 00.04 tenant sweep (D3): hospital-bound users are pinned, not free to pick dto.hospitalId.
    val hospitalId = tenantResolver.pinOrDefault(principal, dto.hospitalId)
    val drugId = dto.drugId
    if (drugId != null) {
      requireActiveDrug(drugId) // 02.06 drug-master activation gate
    }
    val existing =
      if (drugId != null) {
        pharmacyStockRepository.findByDrugId(drugId).firstOrNull {
          it.hospitalId == hospitalId && it.batchNumber == dto.batchNumber
        }
      } else {
        null
      }
    if (existing != null) {
      val delta = dto.quantityOnHand ?: 0
      existing.quantityOnHand += delta
      val saved = pharmacyStockRepository.saveAndFlush(existing)
      recordLedger(saved, "INTAKE", delta, "stock", null, null, principal.userId)
      return PharmacyStockView.from(saved)
    }
    val stock =
      pharmacyStockRepository.saveAndFlush(
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
    recordLedger(stock, "INTAKE", stock.quantityOnHand, "stock", null, null, principal.userId)
    return PharmacyStockView.from(stock)
  }

  @Transactional(readOnly = true)
  fun listStock(
    search: String? = null,
    activeOnly: Boolean? = null,
    hospitalId: UUID? = null,
  ): List<PharmacyStockView> {
    val stocks =
      when {
        search != null && activeOnly == true -> {
          pharmacyStockRepository.findByDrugNameContainingIgnoreCase(hospitalId, search).filter { it.isActive }
        }

        activeOnly == true -> {
          pharmacyStockRepository.findByIsActiveTrue(hospitalId)
        }

        search != null -> {
          pharmacyStockRepository.findByDrugNameContainingIgnoreCase(hospitalId, search)
        }

        else -> {
          pharmacyStockRepository.findAllForHospital(hospitalId)
        }
      }
    return stocks.sortedBy { it.drugName }.map { PharmacyStockView.from(it) }
  }

  @Transactional
  fun updateStock(
    stockId: UUID,
    dto: StockUpdateDto,
    hospitalId: UUID?,
  ): PharmacyStockView {
    val stock =
      pharmacyStockRepository
        .findById(stockId)
        .orElseThrow { IllegalArgumentException("STOCK_NOT_FOUND") }
    requireSameHospital(hospitalId, stock.hospitalId) // 00.04 tenant sweep (D8)
    if (dto.drugId != null) {
      requireActiveDrug(dto.drugId) // 02.06 drug-master activation gate
    }
    val oldQuantity = stock.quantityOnHand
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
    val saved = pharmacyStockRepository.saveAndFlush(stock)
    if (quantity != null && quantity != oldQuantity) {
      recordLedger(saved, "ADJUST", quantity - oldQuantity, "stock", stockId, null, null)
    }
    return PharmacyStockView.from(saved)
  }

  // 02.06 FEFO: earliest-expiry active batch of this drug at this hospital that can cover
  // the quantity; null-expiry batches sort last.
  private fun pickFefoStock(
    drugId: UUID,
    hospitalId: UUID?,
    quantity: Int,
  ): PharmacyStock =
    pharmacyStockRepository
      .findByDrugId(drugId)
      .filter { it.isActive && (hospitalId == null || it.hospitalId == hospitalId) && it.quantityOnHand >= quantity }
      .minWithOrNull(compareBy({ it.expiryDate == null }, { it.expiryDate }))
      ?: throw IllegalArgumentException("INSUFFICIENT_STOCK")

  // 02.06: dispensing a near-expiry (or expired) batch is blocked outright.
  private fun requireNotNearExpiry(stock: PharmacyStock) {
    val expiry = stock.expiryDate ?: return
    if (!expiry.isAfter(LocalDate.now().plusDays(nearExpiryDays))) {
      throw IllegalArgumentException("NEAR_EXPIRY_BLOCK")
    }
  }

  @Transactional
  fun dispense(
    dto: DispenseRequestDto,
    dispensedBy: UUID,
    hospitalId: UUID? = null,
  ): DispensationView {
    // 02.06 audited substitution: reason is mandatory whenever a substitution is recorded.
    if ((dto.substitutedDrugId != null || dto.substitutedDrugName != null) && dto.substitutionReason.isNullOrBlank()) {
      throw IllegalArgumentException("SUBSTITUTION_REASON_REQUIRED")
    }
    val drug = dto.drugId?.let { requireActiveDrug(it) } // 02.06 drug-master activation gate
    // 02.06 / CS-04 pharmacist leg: controlled drugs need a second authorized user.
    if (drug?.isControlled == true && dto.secondAuthorizerId == null) {
      throw IllegalArgumentException("SECOND_AUTHORIZER_REQUIRED")
    }
    val stock =
      if (dto.stockId != null) {
        val s =
          pharmacyStockRepository
            .findById(dto.stockId)
            .orElseThrow { IllegalArgumentException("STOCK_NOT_FOUND") }
        requireSameHospital(hospitalId, s.hospitalId) // 00.04 tenant sweep (D8)
        s
      } else if (drug != null) {
        pickFefoStock(drug.id!!, hospitalId, dto.quantity)
      } else {
        null
      }
    stock?.let {
      requireNotNearExpiry(it)
      if (it.quantityOnHand < dto.quantity) {
        throw IllegalArgumentException("INSUFFICIENT_STOCK")
      }
    }
    val dispensation =
      dispensationRepository.save(
        Dispensation(
          hospitalId = hospitalId,
          prescriptionId = dto.prescriptionId,
          patientId = dto.patientId,
          encounterId = dto.encounterId,
          drugId = dto.drugId,
          drugName = dto.drugName,
          quantity = dto.quantity,
          status = "dispensed",
          dispensedBy = dispensedBy,
          dispensedAt = Instant.now(),
          unitPrice = dto.unitPrice ?: BigDecimal.ZERO,
          totalPrice = (dto.unitPrice ?: BigDecimal.ZERO).multiply(BigDecimal.valueOf(dto.quantity.toLong())),
          notes = dto.notes,
          stockId = stock?.id,
          batchNumber = stock?.batchNumber, // 02.06 batch/expiry traceability on dispense
          substitutedDrugId = dto.substitutedDrugId,
          substitutedDrugName = dto.substitutedDrugName,
          substitutionReason = dto.substitutionReason,
        ),
      )
    stock?.let {
      it.quantityOnHand -= dto.quantity
      val saved = pharmacyStockRepository.saveAndFlush(it)
      recordLedger(saved, "DISPENSE", -dto.quantity, "dispensation", dispensation.id, dto.notes, dispensedBy)
    }
    if (drug?.isControlled == true) {
      controlledRegisterRepository.save(
        ControlledRegisterEntry(
          hospitalId = hospitalId ?: throw IllegalArgumentException("HOSPITAL_REQUIRED"),
          dispensationId = dispensation.id,
          drugId = drug.id,
          drugName = dto.drugName,
          batchNumber = stock?.batchNumber,
          quantity = dto.quantity,
          dispensedBy = dispensedBy,
          secondAuthorizerId = dto.secondAuthorizerId!!,
          occurredAt = Instant.now(),
        ),
      )
    }
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
    hospitalId: UUID?,
  ): DispensationView {
    val dispensation =
      dispensationRepository
        .findById(dispId)
        .orElseThrow { IllegalArgumentException("DISPENSATION_NOT_FOUND") }
    requireSameHospital(hospitalId, dispensation.hospitalId) // 00.04 tenant sweep (D8)
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
    hospitalId: UUID?,
  ): DispensationView {
    val dispensation =
      dispensationRepository
        .findById(dispId)
        .orElseThrow { IllegalArgumentException("DISPENSATION_NOT_FOUND") }
    requireSameHospital(hospitalId, dispensation.hospitalId) // 00.04 tenant sweep (D8)
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
      // 02.06: restock the dispensed batch (stock_id) when known, not an arbitrary tenant row.
      val restockStockId = dispensation.stockId
      val stock =
        if (restockStockId != null) {
          pharmacyStockRepository
            .findById(restockStockId)
            .orElseThrow { IllegalArgumentException("STOCK_NOT_FOUND") }
        } else {
          val drugId = dispensation.drugId
          if (drugId != null) {
            pharmacyStockRepository.findByDrugId(drugId).firstOrNull { it.hospitalId == dispensation.hospitalId }
              ?: throw IllegalArgumentException("STOCK_NOT_FOUND")
          } else {
            null
          }
        }
      if (stock != null) {
        stock.quantityOnHand += dispensation.quantity
        val saved = pharmacyStockRepository.saveAndFlush(stock)
        recordLedger(saved, "RESTOCK", dispensation.quantity, "dispensation", dispensation.id, reason, null)
      }
    }
    return DispensationView.from(dispensationRepository.save(dispensation))
  }

  @Transactional(readOnly = true)
  fun listDispensations(
    patientId: UUID? = null,
    status: String? = null,
    hospitalId: UUID? = null,
  ): List<DispensationView> {
    val dispensations =
      when {
        patientId != null && status != null -> {
          dispensationRepository.findByPatientId(hospitalId, patientId).filter { it.status == status }
        }

        patientId != null -> {
          dispensationRepository.findByPatientId(hospitalId, patientId)
        }

        status != null -> {
          dispensationRepository.findByStatus(hospitalId, status)
        }

        else -> {
          dispensationRepository.findAllForHospital(hospitalId)
        }
      }
    return dispensations
      .sortedByDescending { it.createdAt ?: Instant.EPOCH }
      .map { DispensationView.from(it) }
  }

  // 02.06 formulary: per-hospital approved-drug list backed by master_data.drug.
  @Transactional(readOnly = true)
  fun listFormulary(hospitalId: UUID?): List<FormularyEntryView> {
    val entries = formularyEntryRepository.findAllActiveForHospital(hospitalId)
    val drugs = drugRepository.findAllById(entries.map { it.drugId }).associateBy { it.id }
    return entries
      .sortedBy { drugs[it.drugId]?.name ?: "" }
      .map { FormularyEntryView.from(it, drugs[it.drugId]) }
  }

  @Transactional
  fun addToFormulary(
    dto: FormularyRequestDto,
    principal: SquamedPrincipal,
  ): FormularyEntryView {
    val drug = requireActiveDrug(dto.drugId) // 02.06 drug-master activation gate
    val hospitalId = tenantResolver.pinOrDefault(principal, null)
    if (formularyEntryRepository.findByHospitalIdAndDrugId(hospitalId, dto.drugId) != null) {
      throw IllegalArgumentException("FORMULARY_ENTRY_EXISTS")
    }
    val entry = formularyEntryRepository.save(FormularyEntry(hospitalId = hospitalId, drugId = dto.drugId, notes = dto.notes))
    return FormularyEntryView.from(entry, drug)
  }

  @Transactional
  fun removeFromFormulary(
    id: UUID,
    hospitalId: UUID?,
  ): FormularyEntryView {
    val entry = formularyEntryRepository.findById(id).orElseThrow { IllegalArgumentException("FORMULARY_ENTRY_NOT_FOUND") }
    requireSameHospital(hospitalId, entry.hospitalId) // 00.04 tenant sweep (D8)
    formularyEntryRepository.delete(entry)
    return FormularyEntryView.from(entry, null)
  }

  // 02.06 indents: store requisitions; receive adds stock through the ledger.
  @Transactional(readOnly = true)
  fun listIndents(hospitalId: UUID?): List<IndentView> = indentRepository.findAllForHospital(hospitalId).map { IndentView.from(it) }

  @Transactional
  fun createIndent(
    dto: IndentRequestDto,
    principal: SquamedPrincipal,
  ): IndentView {
    val hospitalId = tenantResolver.pinOrDefault(principal, null)
    val drug = dto.drugId?.let { requireActiveDrug(it) } // 02.06 drug-master activation gate
    val drugName = drug?.name ?: dto.drugName ?: throw IllegalArgumentException("DRUG_NAME_REQUIRED")
    val indent =
      indentRepository.save(
        Indent(
          hospitalId = hospitalId,
          drugId = dto.drugId,
          drugName = drugName,
          quantityRequested = dto.quantityRequested,
          note = dto.note,
          requestedBy = principal.userId,
        ),
      )
    return IndentView.from(indent)
  }

  @Transactional
  fun receiveIndent(
    id: UUID,
    dto: IndentReceiveRequestDto,
    hospitalId: UUID?,
    principal: SquamedPrincipal,
  ): IndentView {
    val indent = indentRepository.findById(id).orElseThrow { IllegalArgumentException("INDENT_NOT_FOUND") }
    requireSameHospital(hospitalId, indent.hospitalId) // 00.04 tenant sweep (D8)
    if (indent.status != "requested") {
      throw IllegalArgumentException("INDENT_NOT_RECEIVABLE")
    }
    if (dto.quantityReceived <= 0) {
      throw IllegalArgumentException("QUANTITY_POSITIVE_REQUIRED")
    }
    var stock =
      indent.drugId?.let { drugId ->
        pharmacyStockRepository.findByDrugId(drugId).firstOrNull { it.hospitalId == indent.hospitalId }
      }
    if (stock == null) {
      stock = PharmacyStock(hospitalId = indent.hospitalId, drugId = indent.drugId, drugName = indent.drugName)
    }
    stock.quantityOnHand += dto.quantityReceived
    val saved = pharmacyStockRepository.saveAndFlush(stock)
    recordLedger(saved, "INTAKE", dto.quantityReceived, "indent", indent.id, indent.note, principal.userId)
    indent.quantityReceived = dto.quantityReceived
    indent.status = "received"
    indent.receivedAt = Instant.now()
    return IndentView.from(indentRepository.save(indent))
  }

  // 02.06 stock ledger: append-only movement history, trigger-enforced balance.
  @Transactional(readOnly = true)
  fun listLedger(
    stockId: UUID?,
    hospitalId: UUID?,
  ): List<StockLedgerView> =
    if (stockId != null) {
      stockLedgerRepository.findByStockIdOrderByOccurredAtDesc(stockId).map { StockLedgerView.from(it) }
    } else {
      stockLedgerRepository.findAllForHospital(hospitalId).map { StockLedgerView.from(it) }
    }

  // 02.06 wastage: shrink stock with a reason; lands in the ledger as WASTAGE.
  @Transactional
  fun recordWastage(
    stockId: UUID,
    dto: WastageRequestDto,
    hospitalId: UUID?,
    principal: SquamedPrincipal,
  ): PharmacyStockView {
    val stock =
      pharmacyStockRepository
        .findById(stockId)
        .orElseThrow { IllegalArgumentException("STOCK_NOT_FOUND") }
    requireSameHospital(hospitalId, stock.hospitalId) // 00.04 tenant sweep (D8)
    if (dto.quantity <= 0) {
      throw IllegalArgumentException("QUANTITY_POSITIVE_REQUIRED")
    }
    if (stock.quantityOnHand < dto.quantity) {
      throw IllegalArgumentException("INSUFFICIENT_STOCK")
    }
    stock.quantityOnHand -= dto.quantity
    val saved = pharmacyStockRepository.saveAndFlush(stock)
    recordLedger(saved, "WASTAGE", -dto.quantity, "stock", stockId, dto.reason, principal.userId)
    return PharmacyStockView.from(saved)
  }

  // 02.06 min-level alerts: active stock at or below reorder level, evaluated on read.
  @Transactional(readOnly = true)
  fun listAlerts(hospitalId: UUID?): List<PharmacyStockView> =
    pharmacyStockRepository
      .findAllForHospital(hospitalId)
      .filter { it.isActive && it.quantityOnHand <= it.reorderLevel }
      .sortedBy { it.drugName }
      .map { PharmacyStockView.from(it) }

  // 02.06 controlled register: CS-04 pharmacist leg (second authorized user recorded per dispense).
  @Transactional(readOnly = true)
  fun listControlledRegister(hospitalId: UUID?): List<ControlledRegisterView> =
    controlledRegisterRepository.findAllForHospital(hospitalId).map { ControlledRegisterView.from(it) }
}
