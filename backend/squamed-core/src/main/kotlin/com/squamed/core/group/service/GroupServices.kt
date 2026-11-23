package com.squamed.core.group.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.consent.repository.ConsentRepository
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.events.SchemaRegistry
import com.squamed.core.group.api.AccessView
import com.squamed.core.group.api.ConfigResolveView
import com.squamed.core.group.api.DirectoryEntryView
import com.squamed.core.group.api.GroupHospitalView
import com.squamed.core.group.api.GroupView
import com.squamed.core.group.api.ReferralView
import com.squamed.core.group.api.ShareCandidateView
import com.squamed.core.group.api.ShareView
import com.squamed.core.group.domain.CrossHospitalShare
import com.squamed.core.group.domain.GroupConfigDocument
import com.squamed.core.group.domain.GroupReferral
import com.squamed.core.group.domain.HospitalGroup
import com.squamed.core.group.repository.CrossHospitalShareRepository
import com.squamed.core.group.repository.GroupConfigDocumentRepository
import com.squamed.core.group.repository.GroupReferralRepository
import com.squamed.core.group.repository.HospitalGroupRepository
import com.squamed.core.hospitalconfig.repository.ConfigDocumentRepository
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.hospitalconfig.service.ConfigDocumentService
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.repository.UserRoleRepository
import com.squamed.core.patient.repository.PatientIdentifierRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class GroupContext(
  val group: HospitalGroup,
  val hospitalIds: List<UUID>,
)

@Service
class GroupService(
  private val groupRepository: HospitalGroupRepository,
  private val hospitalRepository: HospitalRepository,
  private val userRoleRepository: UserRoleRepository,
  private val userAccountRepository: UserAccountRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun create(
    code: String,
    name: String,
    actorId: UUID?,
  ): GroupView {
    if (code.isBlank() || name.isBlank()) throw IllegalArgumentException("GROUP_CODE_NAME_REQUIRED")
    if (groupRepository.findByCodeIgnoreCase(code.trim()) != null) throw IllegalArgumentException("GROUP_CODE_EXISTS")
    val saved =
      groupRepository.save(
        HospitalGroup(code = code.trim().uppercase(), name = name.trim()).also { it.createdBy = actorId },
      )
    return view(saved)
  }

  @Transactional
  fun attachHospital(
    groupId: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): GroupView {
    val group = groupRepository.findById(groupId).orElseThrow { IllegalArgumentException("GROUP_NOT_FOUND") }
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    if (hospital.groupId != null && hospital.groupId != groupId) throw IllegalArgumentException("HOSPITAL_IN_OTHER_GROUP")
    hospital.groupId = groupId
    hospital.updatedBy = actorId
    hospitalRepository.save(hospital)
    return view(group)
  }

  @Transactional(readOnly = true)
  fun groups(): List<GroupView> = groupRepository.findAllByOrderByCreatedAtAsc().map(::view)

  @Transactional(readOnly = true)
  fun group(id: UUID): GroupView = view(groupRepository.findById(id).orElseThrow { IllegalArgumentException("GROUP_NOT_FOUND") })

  @Transactional(readOnly = true)
  fun directory(groupId: UUID): List<DirectoryEntryView> {
    val hospitalIds = hospitalsOf(groupId).mapNotNull { it.id }
    val roles = hospitalIds.flatMap { userRoleRepository.findByHospitalId(it) }
    val accounts = userAccountRepository.findAllById(roles.map { it.userId }.distinct()).associateBy { it.id }
    return roles
      .groupBy { it.userId }
      .map { (userId, assignments) ->
        DirectoryEntryView(
          userId = userId,
          email = accounts[userId]?.email ?: "unknown",
          hospitalId = assignments.firstOrNull()?.hospitalId,
          roles = assignments.mapNotNull { it.role.name }.distinct().sorted(),
        )
      }.sortedBy { it.email }
  }

  @Transactional(readOnly = true)
  fun contextForHospital(hospitalId: UUID): GroupContext? {
    val hospital = hospitalRepository.findById(hospitalId).orElse(null) ?: return null
    val groupId = hospital.groupId ?: return null
    val group = groupRepository.findById(groupId).orElse(null) ?: return null
    return GroupContext(group, hospitalsOf(groupId).mapNotNull { it.id })
  }

  fun hospitalsOf(groupId: UUID): List<GroupHospitalView> =
    hospitalRepository.findAll().filter { it.groupId == groupId }.map { GroupHospitalView(it.id, it.name, it.code) }

  private fun view(group: HospitalGroup) =
    GroupView(
      id = group.id,
      code = group.code,
      name = group.name,
      status = group.status,
      hospitals = hospitalsOf(group.id ?: throw IllegalStateException("GROUP_ID_MISSING")),
    )
}

@Service
class GroupConfigService(
  private val groupRepository: HospitalGroupRepository,
  private val groupConfigRepository: GroupConfigDocumentRepository,
  private val configDocumentRepository: ConfigDocumentRepository,
  private val hospitalRepository: HospitalRepository,
  private val schemaRegistry: SchemaRegistry,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun publish(
    groupId: UUID,
    type: String,
    content: JsonNode,
    actorId: UUID?,
  ): ConfigResolveView {
    if (type !in ConfigDocumentService.TYPES) throw IllegalArgumentException("UNKNOWN_CONFIG_TYPE: $type")
    groupRepository.findById(groupId).orElseThrow { IllegalArgumentException("GROUP_NOT_FOUND") }
    val violations = schemaRegistry.validate("config/$type.schema.json", content)
    if (violations.isNotEmpty()) throw IllegalArgumentException("CONFIG_VALIDATION_FAILED: ${violations.joinToString("; ")}")
    val nextVersion =
      (groupConfigRepository.findFirstByGroupIdAndTypeOrderByDocumentVersionDesc(groupId, type)?.documentVersion ?: 0) + 1
    groupConfigRepository.findByGroupIdAndTypeAndStatus(groupId, type, "active").forEach { it.status = "superseded" }
    val saved =
      groupConfigRepository.save(
        GroupConfigDocument(groupId = groupId, type = type, documentVersion = nextVersion, content = content)
          .also { it.createdBy = actorId },
      )
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "config.changed",
        occurredAt = Instant.now(),
        source = "group-service",
        entityType = "group_config_document",
        entityId = saved.id,
        payload = mapOf("groupId" to groupId.toString(), "type" to type, "version" to nextVersion),
        actorType = "user",
        actorId = actorId,
      ),
    )
    return ConfigResolveView(type, "group", nextVersion, content)
  }

  @Transactional(readOnly = true)
  fun resolve(
    hospitalId: UUID,
    type: String,
  ): ConfigResolveView {
    if (type !in ConfigDocumentService.TYPES) throw IllegalArgumentException("UNKNOWN_CONFIG_TYPE: $type")
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    val own = configDocumentRepository.findFirstByHospitalIdAndTypeAndStatusOrderByDocumentVersionDesc(hospitalId, type, "active")
    if (own != null) return ConfigResolveView(type, "hospital", own.documentVersion, own.content)
    val groupId = hospital.groupId ?: throw IllegalArgumentException("CONFIG_NOT_FOUND")
    val inherited = groupConfigRepository.findFirstByGroupIdAndTypeOrderByDocumentVersionDesc(groupId, type)
    if (inherited == null || inherited.status != "active") throw IllegalArgumentException("CONFIG_NOT_FOUND")
    return ConfigResolveView(type, "group", inherited.documentVersion, inherited.content)
  }
}

@Service
class GroupShareService(
  private val groupRepository: HospitalGroupRepository,
  private val hospitalRepository: HospitalRepository,
  private val shareRepository: CrossHospitalShareRepository,
  private val consentRepository: ConsentRepository,
  private val patientRepository: PatientRecordRepository,
  private val identifierRepository: PatientIdentifierRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional(readOnly = true)
  fun candidates(
    patientId: UUID,
    toHospitalId: UUID,
  ): List<ShareCandidateView> {
    val patient = patientRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    val fromHospital = patient.hospitalId ?: throw IllegalArgumentException("PATIENT_HOSPITAL_REQUIRED")
    val group = groupFor(fromHospital)
    if (groupFor(toHospitalId).id != group.id) throw IllegalArgumentException("HOSPITAL_NOT_IN_GROUP")
    val matches = LinkedHashMap<UUID, ShareCandidateView>()

    fun consider(
      others: List<com.squamed.core.patient.domain.PatientRecord>,
      reason: String,
      score: Int,
    ) {
      others
        .filter { it.id != patientId && it.hospitalId != null && it.hospitalId != fromHospital }
        .filter { groupFor(it.hospitalId!!).id == group.id }
        .forEach { other ->
          val existing = matches[other.id!!]
          if (existing == null || existing.score < score) {
            matches[other.id!!] =
              ShareCandidateView(
                patientId = other.id,
                mrn = other.mrn,
                name = listOfNotNull(other.firstName, other.lastName).joinToString(" "),
                hospitalId = other.hospitalId,
                hospitalName = hospitalRepository.findById(other.hospitalId!!).orElse(null)?.name,
                matchReason = reason,
                score = score,
              )
          }
        }
    }

    patient.qid?.let { consider(patientRepository.findByQid(it), "QID", 100) }
    patient.phone?.let { consider(patientRepository.findByPhone(it), "PHONE", 80) }
    patient.email?.let { consider(patientRepository.findByEmailIgnoreCase(it), "EMAIL", 70) }
    identifierRepository
      .findByPatientId(patientId)
      .forEach { identifier ->
        consider(identifierRepository.findByIdValue(identifier.idValue).mapNotNull { patientRepository.findById(it.patientId).orElse(null) }, "IDENTIFIER", 90)
      }

    return matches.values.sortedByDescending { it.score }
  }

  @Transactional
  fun share(
    patientId: UUID,
    toHospitalId: UUID,
    consentId: UUID,
    actorId: UUID?,
  ): ShareView {
    val patient = patientRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    val fromHospital = patient.hospitalId ?: throw IllegalArgumentException("PATIENT_HOSPITAL_REQUIRED")
    if (fromHospital == toHospitalId) throw IllegalArgumentException("SHARE_SAME_HOSPITAL")
    val group = groupFor(fromHospital)
    if (groupFor(toHospitalId).id != group.id) throw IllegalArgumentException("HOSPITAL_NOT_IN_GROUP")
    val consent = consentRepository.findById(consentId).orElseThrow { IllegalArgumentException("CONSENT_NOT_FOUND") }
    if (consent.patientId != patientId) throw IllegalArgumentException("CONSENT_PATIENT_MISMATCH")
    if (consent.status != "granted") throw IllegalArgumentException("CONSENT_NOT_GRANTED")
    if (consent.purpose != "data_sharing") throw IllegalArgumentException("CONSENT_PURPOSE_INVALID")
    if (consent.expiresAt != null && consent.expiresAt!!.isBefore(Instant.now())) throw IllegalArgumentException("CONSENT_EXPIRED")
    val existing = shareRepository.findByGroupIdAndPatientIdAndToHospitalId(group.id!!, patientId, toHospitalId)
    if (existing?.status == "ACTIVE") throw IllegalStateException("SHARE_ALREADY_ACTIVE")
    val share =
      existing?.also {
        it.status = "ACTIVE"
        it.consentId = consentId
        it.revokedAt = null
        it.revokedBy = null
        it.updatedBy = actorId
      } ?: CrossHospitalShare(
        groupId = group.id!!,
        patientId = patientId,
        fromHospitalId = fromHospital,
        toHospitalId = toHospitalId,
        consentId = consentId,
      ).also { it.createdBy = actorId }
    val saved = shareRepository.save(share)
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "record.shared_consented",
        occurredAt = Instant.now(),
        source = "group-service",
        entityType = "cross_hospital_share",
        entityId = saved.id,
        hospitalId = fromHospital,
        payload = mapOf("patientId" to patientId.toString(), "toHospitalId" to toHospitalId.toString(), "consentId" to consentId.toString()),
        actorType = "user",
        actorId = actorId,
      ),
    )
    return ShareView.from(saved)
  }

  @Transactional
  fun revoke(
    shareId: UUID,
    actorId: UUID?,
  ): ShareView {
    val share = shareRepository.findById(shareId).orElseThrow { IllegalArgumentException("SHARE_NOT_FOUND") }
    if (share.status != "ACTIVE") throw IllegalStateException("SHARE_NOT_ACTIVE")
    share.status = "REVOKED"
    share.revokedAt = Instant.now()
    share.revokedBy = actorId
    share.updatedBy = actorId
    return ShareView.from(shareRepository.save(share))
  }

  @Transactional(readOnly = true)
  fun access(
    patientId: UUID,
    hospitalId: UUID,
  ): AccessView {
    val patient = patientRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    val fromHospital = patient.hospitalId ?: throw IllegalArgumentException("PATIENT_HOSPITAL_REQUIRED")
    val group = groupForAccess(fromHospital)
    if (groupForAccess(hospitalId).id != group.id) throw AccessDeniedException("GROUP_ACCESS_DENIED: hospital not in group")
    if (hospitalId == fromHospital) {
      return AccessView(patientId, hospitalId, true, null, patient.mrn, listOfNotNull(patient.firstName, patient.lastName).joinToString(" "))
    }
    val share =
      shareRepository.findByGroupIdAndPatientIdAndToHospitalId(group.id!!, patientId, hospitalId)
        ?: throw AccessDeniedException("GROUP_ACCESS_DENIED: no share")
    if (share.status != "ACTIVE") throw AccessDeniedException("GROUP_ACCESS_DENIED: share revoked")
    val consent =
      consentRepository.findById(share.consentId).orElse(null)
        ?: throw AccessDeniedException("GROUP_ACCESS_DENIED: consent missing")
    if (consent.status != "granted" || consent.purpose != "data_sharing") {
      throw AccessDeniedException("GROUP_ACCESS_DENIED: consent not granted")
    }
    if (consent.expiresAt != null && consent.expiresAt!!.isBefore(Instant.now())) {
      throw AccessDeniedException("GROUP_ACCESS_DENIED: consent expired")
    }
    return AccessView(
      patientId,
      hospitalId,
      true,
      share.id,
      patient.mrn,
      listOfNotNull(patient.firstName, patient.lastName).joinToString(" "),
    )
  }

  private fun groupFor(hospitalId: UUID): HospitalGroup {
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    val groupId = hospital.groupId ?: throw IllegalArgumentException("HOSPITAL_NOT_IN_GROUP")
    return groupRepository.findById(groupId).orElseThrow { IllegalArgumentException("GROUP_NOT_FOUND") }
  }

  // The access gate is a denial, not an argument error: outside hospitals are 403.
  private fun groupForAccess(hospitalId: UUID): HospitalGroup {
    val hospital =
      hospitalRepository.findById(hospitalId).orElse(null)
        ?: throw AccessDeniedException("GROUP_ACCESS_DENIED: hospital not found")
    val groupId = hospital.groupId ?: throw AccessDeniedException("GROUP_ACCESS_DENIED: hospital not in group")
    return groupRepository.findById(groupId).orElse(null)
      ?: throw AccessDeniedException("GROUP_ACCESS_DENIED: group not found")
  }
}

@Service
class GroupReferralService(
  private val referralRepository: GroupReferralRepository,
  private val hospitalRepository: HospitalRepository,
  private val patientRepository: PatientRecordRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  @Transactional
  fun create(
    patientId: UUID,
    toHospitalId: UUID,
    reason: String,
    notes: String?,
    actorId: UUID?,
    actorHospitalId: UUID?,
  ): ReferralView {
    if (reason.isBlank()) throw IllegalArgumentException("REFERRAL_REASON_REQUIRED")
    val patient = patientRepository.findById(patientId).orElseThrow { IllegalArgumentException("PATIENT_NOT_FOUND") }
    val fromHospital = patient.hospitalId ?: throw IllegalArgumentException("PATIENT_HOSPITAL_REQUIRED")
    if (fromHospital == toHospitalId) throw IllegalArgumentException("REFERRAL_SAME_HOSPITAL")
    if (actorHospitalId != null && actorHospitalId != fromHospital) throw AccessDeniedException("REFERRAL_HOSPITAL_MISMATCH")
    val from = hospitalRepository.findById(fromHospital).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    val to = hospitalRepository.findById(toHospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    if (from.groupId == null || from.groupId != to.groupId) throw IllegalArgumentException("HOSPITAL_NOT_IN_GROUP")
    val saved =
      referralRepository.save(
        GroupReferral(
          groupId = from.groupId!!,
          patientId = patientId,
          fromHospitalId = fromHospital,
          toHospitalId = toHospitalId,
          reason = reason.trim().take(400),
          notes = notes?.trim()?.take(600),
        ).also { it.createdBy = actorId },
      )
    publish(saved, "group.referral_created", actorId)
    return ReferralView.from(saved)
  }

  @Transactional
  fun decide(
    id: UUID,
    status: String,
    actorId: UUID?,
    actorHospitalId: UUID?,
  ): ReferralView {
    val referral = referralRepository.findById(id).orElseThrow { IllegalArgumentException("REFERRAL_NOT_FOUND") }
    if (actorHospitalId != null && actorHospitalId != referral.toHospitalId) throw AccessDeniedException("REFERRAL_HOSPITAL_MISMATCH")
    val next = status.trim().uppercase()
    val allowed =
      when (referral.status) {
        "PENDING" -> setOf("ACCEPTED", "CANCELLED")
        "ACCEPTED" -> setOf("COMPLETED", "CANCELLED")
        else -> emptySet()
      }
    if (next !in allowed) throw IllegalStateException("REFERRAL_TRANSITION_INVALID")
    referral.status = next
    referral.decidedBy = actorId
    referral.decidedAt = Instant.now()
    referral.updatedBy = actorId
    val saved = referralRepository.save(referral)
    if (next == "ACCEPTED") publish(saved, "group.referral_accepted", actorId)
    return ReferralView.from(saved)
  }

  @Transactional(readOnly = true)
  fun inbox(
    hospitalId: UUID,
    status: String?,
  ): List<ReferralView> =
    (
      if (status.isNullOrBlank()) {
        referralRepository.findByToHospitalIdOrderByCreatedAtDesc(hospitalId)
      } else {
        referralRepository.findByToHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, status.uppercase())
      }
    ).map(ReferralView::from)

  @Transactional(readOnly = true)
  fun sent(hospitalId: UUID): List<ReferralView> = referralRepository.findByFromHospitalIdOrderByCreatedAtDesc(hospitalId).map(ReferralView::from)

  private fun publish(
    referral: GroupReferral,
    eventType: String,
    actorId: UUID?,
  ) {
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        occurredAt = Instant.now(),
        source = "group-service",
        entityType = "group_referral",
        entityId = referral.id,
        hospitalId = referral.fromHospitalId,
        payload =
          mapOf(
            "patientId" to referral.patientId.toString(),
            "fromHospitalId" to referral.fromHospitalId.toString(),
            "toHospitalId" to referral.toHospitalId.toString(),
            "status" to referral.status,
          ),
        actorType = "user",
        actorId = actorId,
      ),
    )
  }
}
