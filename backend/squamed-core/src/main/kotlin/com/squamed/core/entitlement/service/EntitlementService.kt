package com.squamed.core.entitlement.service

import com.squamed.core.entitlement.domain.EntitlementPlan
import com.squamed.core.entitlement.domain.EntitlementSubscription
import com.squamed.core.entitlement.repository.EntitlementPlanRepository
import com.squamed.core.entitlement.repository.EntitlementSubscriptionRepository
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

data class PlanView(
  val code: String,
  val name: String,
  val description: String?,
  val features: List<String>,
  val active: Boolean,
)

data class EntitlementView(
  val hospitalId: UUID,
  val managed: Boolean,
  val planCode: String?,
  val status: String,
  val validFrom: LocalDate?,
  val validTo: LocalDate?,
  val features: List<String>,
)

data class BrandView(
  val hospitalName: String,
  val legalName: String?,
  val address: String?,
  val phone: String?,
  val email: String?,
  val licenseNumber: String?,
  val timezone: String,
  val currency: String,
)

// 01.10: module gates read the plan, not a per-module flag. A hospital with no subscription is
// UNMANAGED and keeps every module - the gate only starts refusing once someone subscribes it,
// so rolling this out cannot switch off a running hospital.
@Service
class EntitlementService(
  private val planRepository: EntitlementPlanRepository,
  private val subscriptionRepository: EntitlementSubscriptionRepository,
  private val hospitalRepository: HospitalRepository,
) {
  fun plans(): List<PlanView> = planRepository.findByActiveTrueOrderByCodeAsc().map(::view)

  fun current(hospitalId: UUID): EntitlementView {
    val subscription =
      subscriptionRepository.findFirstByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, "ACTIVE")
        ?: return EntitlementView(hospitalId, false, null, "UNMANAGED", null, null, emptyList())
    val plan =
      planRepository.findById(subscription.planId).orElse(null)
        ?: return EntitlementView(hospitalId, true, null, "UNKNOWN_PLAN", subscription.validFrom, subscription.validTo, emptyList())
    val expired = subscription.validTo?.isBefore(LocalDate.now()) == true
    return EntitlementView(
      hospitalId = hospitalId,
      managed = true,
      planCode = plan.code,
      status = if (expired) "EXPIRED" else "ACTIVE",
      validFrom = subscription.validFrom,
      validTo = subscription.validTo,
      features = if (expired) emptyList() else featuresOf(plan),
    )
  }

  @Transactional
  fun subscribe(
    hospitalId: UUID,
    planCode: String,
    validTo: LocalDate?,
    actorId: UUID?,
  ): EntitlementView {
    val plan = planRepository.findByCodeIgnoreCase(planCode) ?: throw IllegalArgumentException("ENTITLEMENT_PLAN_NOT_FOUND")
    require(plan.active) { "ENTITLEMENT_PLAN_INACTIVE" }
    // One ACTIVE row per hospital: supersede rather than stack, so `current` cannot pick a stale plan.
    subscriptionRepository.findByHospitalIdAndStatus(hospitalId, "ACTIVE").forEach {
      it.status = "SUPERSEDED"
      it.updatedBy = actorId
      subscriptionRepository.save(it)
    }
    subscriptionRepository.save(
      EntitlementSubscription(
        hospitalId = hospitalId,
        planId = plan.id!!,
        validTo = validTo,
      ).also { it.createdBy = actorId },
    )
    return current(hospitalId)
  }

  fun requireFeature(
    hospitalId: UUID,
    feature: String,
  ) {
    val entitlement = current(hospitalId)
    if (!entitlement.managed) return
    if (feature !in entitlement.features) {
      throw AccessDeniedException("ENTITLEMENT_REQUIRED: $feature")
    }
  }

  fun brand(hospitalId: UUID): BrandView {
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    val address =
      listOfNotNull(hospital.addressLine1, hospital.addressLine2, hospital.city, hospital.postalCode)
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .ifBlank { null }
    return BrandView(
      hospitalName = hospital.name,
      legalName = hospital.legalName,
      address = address,
      phone = hospital.phone,
      email = hospital.email,
      licenseNumber = hospital.licenseNumber,
      timezone = hospital.timezone,
      currency = hospital.currency,
    )
  }

  private fun featuresOf(plan: EntitlementPlan): List<String> =
    plan.features
      .split(',')
      .map { it.trim() }
      .filter { it.isNotEmpty() }

  private fun view(plan: EntitlementPlan): PlanView =
    PlanView(
      code = plan.code,
      name = plan.name,
      description = plan.description,
      features = featuresOf(plan),
      active = plan.active,
    )
}
