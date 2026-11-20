package com.squamed.core.billing.service

import com.squamed.core.billing.domain.Charge
import com.squamed.core.billing.repository.ChargeRepository
import com.squamed.core.billing.repository.TariffItemRepository
import com.squamed.core.events.DomainEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

// 03.01 charge capture: a performed billable event becomes one ledger row, priced from the
// active tariff row matching the performed code (rate cards arrive with 03.02).
@Service
class ChargeCaptureService(
  private val chargeRepository: ChargeRepository,
  private val tariffItemRepository: TariffItemRepository,
) {
  @Transactional
  fun capture(event: DomainEvent): Charge? {
    if (event.eventType != "billing.chargeable_performed") return null
    val eventId = event.eventId
    // Idempotency: a replayed event never double-charges.
    chargeRepository.findBySourceEventId(eventId)?.let { return it }
    val patientId =
      event.payload["patientId"]?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return null.also { logger.warn { "chargeable event ${event.eventId} has no patientId" } }
    val category = event.payload["chargeCategory"]?.toString() ?: "SERVICE"
    val tests = (event.payload["tests"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    val code = tests.firstOrNull()?.get("testCode")?.toString() ?: category
    val name =
      tests
        .mapNotNull { it["testName"]?.toString() }
        .joinToString(", ")
        .ifBlank { code }
    val quantity = tests.sumOf { (it["quantity"] as? Number)?.toInt() ?: 1 }.coerceAtLeast(1)
    val price = tariffPrice(event.hospitalId, code)
    val charge =
      chargeRepository.save(
        Charge(
          hospitalId = event.hospitalId,
          patientId = patientId,
          encounterId = null,
          chargeCode = code,
          chargeName = name,
          category = category,
          quantity = quantity,
          unitPriceQar = price,
          totalQar = price.multiply(BigDecimal.valueOf(quantity.toLong())),
          status = "posted",
          insuranceClaimable = true,
          postedAt = Instant.now(),
          referenceType = event.entityType,
          referenceId = event.entityId,
          sourceEventId = eventId,
          entryMode = "EVENT",
        ),
      )
    return charge
  }

  private fun tariffPrice(
    hospitalId: UUID?,
    code: String,
  ): BigDecimal =
    tariffItemRepository
      .findByCode(hospitalId, code)
      .firstOrNull { it.isActive }
      ?.priceQar
      ?: BigDecimal.ZERO
}
