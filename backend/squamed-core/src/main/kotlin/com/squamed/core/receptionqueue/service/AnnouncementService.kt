package com.squamed.core.receptionqueue.service

import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.receptionqueue.api.AnnouncementRuleDto
import com.squamed.core.receptionqueue.api.AnnouncementView
import com.squamed.core.receptionqueue.api.DisplayZoneDto
import com.squamed.core.receptionqueue.domain.AnnouncementRule
import com.squamed.core.receptionqueue.domain.DisplayZone
import com.squamed.core.receptionqueue.domain.QueueEntry
import com.squamed.core.receptionqueue.repository.AnnouncementRuleRepository
import com.squamed.core.receptionqueue.repository.DisplayZoneRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 01.07 voice announcements: a called token fans out as `announcement.enqueued` carrying the TTS
 * texts (per language), repeat count, zone and volume; the browser display page speaks it via
 * SpeechSynthesis. Repeat doubling for emergency priority and the '*' fallback rule implement the
 * contract's repeat & priority rules; zones map announcements to displays/counters.
 */
@Service
class AnnouncementService(
  private val zoneRepository: DisplayZoneRepository,
  private val ruleRepository: AnnouncementRuleRepository,
  private val eventPublisher: DomainEventPublisher,
) {
  companion object {
    /** TTS text per language; requested languages without a template fall back to the English text. */
    private val TEMPLATES =
      mapOf(
        "en" to "Token {token}, counter {counter}",
        "hi" to "टोकन {token}, काउंटर {counter}",
      )
    private const val DEFAULT_REPEAT_COUNT = 2
    private const val DEFAULT_VOLUME = 80
    private const val FALLBACK_RULE_QUEUE_TYPE = "*"
    private const val EMERGENCY_PRIORITY = "emergency"
  }

  @Transactional(readOnly = true)
  fun listZones(hospitalId: UUID?): List<DisplayZoneDto> = zoneRepository.findAllForHospital(hospitalId).map { DisplayZoneDto.from(it) }

  @Transactional
  fun createZone(
    name: String,
    servicePointId: String?,
    volume: Int?,
    hospitalId: UUID,
  ): DisplayZoneDto {
    if (name.isBlank()) throw IllegalArgumentException("ZONE_NAME_REQUIRED")
    val effectiveVolume = volume ?: DEFAULT_VOLUME
    if (effectiveVolume < 0 || effectiveVolume > 100) throw IllegalArgumentException("ZONE_VOLUME_INVALID")
    return DisplayZoneDto.from(
      zoneRepository.save(
        DisplayZone(
          hospitalId = hospitalId,
          name = name,
          servicePointId = servicePointId,
          volume = effectiveVolume,
        ),
      ),
    )
  }

  @Transactional(readOnly = true)
  fun listRules(hospitalId: UUID?): List<AnnouncementRuleDto> = ruleRepository.findAllForHospital(hospitalId).map { AnnouncementRuleDto.from(it) }

  @Transactional
  fun createRule(
    queueType: String?,
    repeatCount: Int?,
    languages: String?,
    zoneId: UUID?,
    hospitalId: UUID,
  ): AnnouncementRuleDto {
    val repeats = repeatCount ?: DEFAULT_REPEAT_COUNT
    if (repeats < 1 || repeats > 10) throw IllegalArgumentException("ANNOUNCEMENT_REPEAT_INVALID")
    // Rule zone must live in the same hospital; cross-tenant references are rejected (00.04 U10).
    zoneId?.let { zoneRepository.findByIdAndHospitalId(it, hospitalId).orElseThrow { IllegalArgumentException("ZONE_NOT_FOUND") } }
    return AnnouncementRuleDto.from(
      ruleRepository.save(
        AnnouncementRule(
          hospitalId = hospitalId,
          queueType = queueType ?: FALLBACK_RULE_QUEUE_TYPE,
          repeatCount = repeats,
          languages = languages ?: "en,hi",
          zoneId = zoneId,
        ),
      ),
    )
  }

  /** Hooked from QueueService.call()/callNext(): resolve rule + zone for the called entry and publish. */
  @Transactional
  fun onQueueCalled(entry: QueueEntry) {
    val hospitalId = entry.hospitalId ?: return
    val rule =
      ruleRepository.findEnabledForHospitalAndQueueType(hospitalId, entry.queueType).firstOrNull()
        ?: ruleRepository.findEnabledForHospitalAndQueueType(hospitalId, FALLBACK_RULE_QUEUE_TYPE).firstOrNull()
    val languages = (rule?.languages ?: "en,hi").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    var repeats = rule?.repeatCount ?: DEFAULT_REPEAT_COUNT
    if (entry.priority == EMERGENCY_PRIORITY) repeats = (repeats * 2).coerceAtMost(10)
    val counter = entry.servicePointName ?: entry.servicePointId ?: entry.queueType
    val zone = resolveZone(hospitalId, rule?.zoneId, entry.servicePointId)
    val texts =
      languages.associateWith { language ->
        (TEMPLATES[language] ?: TEMPLATES.getValue("en"))
          .replace("{token}", entry.tokenNumber)
          .replace("{counter}", counter)
      }
    publish(
      hospitalId = hospitalId,
      entityId = entry.id,
      tokenNumber = entry.tokenNumber,
      queueType = entry.queueType,
      counter = counter,
      priority = entry.priority,
      repeatCount = repeats,
      languages = languages,
      zone = zone,
      texts = texts,
    )
  }

  /** Manual enqueue (POST /announcements): caller-supplied texts echoed back with defaults filled. */
  @Transactional
  fun enqueue(
    hospitalId: UUID,
    texts: Map<String, String>,
    zoneId: UUID?,
    repeatCount: Int?,
  ): AnnouncementView {
    if (texts.isEmpty() || texts.values.any { it.isBlank() }) throw IllegalArgumentException("ANNOUNCEMENT_TEXT_REQUIRED")
    val repeats = repeatCount ?: DEFAULT_REPEAT_COUNT
    if (repeats < 1 || repeats > 10) throw IllegalArgumentException("ANNOUNCEMENT_REPEAT_INVALID")
    val zone =
      zoneId?.let {
        zoneRepository.findByIdAndHospitalId(it, hospitalId).orElseThrow { IllegalArgumentException("ZONE_NOT_FOUND") }
      }
    return publish(
      hospitalId = hospitalId,
      entityId = null,
      tokenNumber = null,
      queueType = null,
      counter = null,
      priority = null,
      repeatCount = repeats,
      languages = texts.keys.toList(),
      zone = zone,
      texts = texts,
    )
  }

  /** Rule zone wins, then the zone bound to the counter, else null (announce in every zone). */
  private fun resolveZone(
    hospitalId: UUID,
    ruleZoneId: UUID?,
    servicePointId: String?,
  ): DisplayZone? {
    ruleZoneId?.let { return zoneRepository.findByIdAndHospitalId(it, hospitalId).orElse(null) }
    if (servicePointId != null) {
      return zoneRepository.findEnabledForHospitalAndServicePoint(hospitalId, servicePointId).firstOrNull()
    }
    return null
  }

  private fun publish(
    hospitalId: UUID,
    entityId: UUID?,
    tokenNumber: String?,
    queueType: String?,
    counter: String?,
    priority: String?,
    repeatCount: Int,
    languages: List<String>,
    zone: DisplayZone?,
    texts: Map<String, String>,
  ): AnnouncementView {
    val payload =
      buildMap<String, Any?> {
        tokenNumber?.let { put("tokenNumber", it) }
        queueType?.let { put("queueType", it) }
        counter?.let { put("counter", it) }
        priority?.let { put("priority", it) }
        put("repeatCount", repeatCount)
        put("languages", languages)
        put("volume", zone?.volume ?: DEFAULT_VOLUME)
        put("texts", texts)
        zone?.id?.let { put("zoneId", it) }
        zone?.name?.let { put("zoneName", it) }
      }
    eventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "announcement.enqueued",
        occurredAt = Instant.now(),
        source = "announcement-service",
        entityType = "queue-entry",
        entityId = entityId,
        hospitalId = hospitalId,
        payload = payload,
      ),
    )
    return AnnouncementView(
      tokenNumber = tokenNumber,
      queueType = queueType,
      counter = counter,
      priority = priority,
      repeatCount = repeatCount,
      languages = languages,
      zoneId = zone?.id,
      zoneName = zone?.name,
      volume = zone?.volume ?: DEFAULT_VOLUME,
      texts = texts,
    )
  }
}
