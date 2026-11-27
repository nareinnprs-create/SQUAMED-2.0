package com.squamed.core.twin.service

import com.squamed.core.hospitalconfig.repository.BedRepository
import com.squamed.core.ipd.repository.AdmissionRepository
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.ops.repository.AssetRepository
import com.squamed.core.ops.repository.WorkOrderRepository
import com.squamed.core.radiology.repository.RadOrderRepository
import com.squamed.core.surgery.repository.OtRoomRepository
import com.squamed.core.twin.domain.CapacitySnapshot
import com.squamed.core.twin.repository.CapacitySnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

// 07.03 digital twin: materialized operational views over governed state. The 06.06 ClickHouse marts
// are not available yet, so the twin reads operational tables directly (deviation recorded).
@Service
class DigitalTwinService(
  private val snapshotRepository: CapacitySnapshotRepository,
  private val bedRepository: BedRepository,
  private val otRoomRepository: OtRoomRepository,
  private val admissionRepository: AdmissionRepository,
  private val labOrderRepository: LabOrderRepository,
  private val radOrderRepository: RadOrderRepository,
  private val assetRepository: AssetRepository,
  private val workOrderRepository: WorkOrderRepository,
  private val outboxRepository: com.squamed.core.events.EventOutboxRepository,
) {
  companion object {
    private const val DIAGNOSTICS_BACKLOG_LIMIT = 100
    private const val EQUIPMENT_DOWN_LIMIT = 5
  }

  @Transactional
  fun generateSnapshot(
    hospitalId: UUID,
    actorId: UUID?,
  ): CapacitySnapshot {
    val beds = bedRepository.findByHospitalId(hospitalId)
    val otRooms = otRoomRepository.findByHospitalId(hospitalId)
    val admissions = admissionRepository.findAllByHospital(hospitalId)
    val today = LocalDate.now()
    val labPending =
      labOrderRepository
        .findByStatusIn(listOf("ordered", "collected", "in_progress"))
        .count { it.hospitalId == hospitalId }
    val radPending =
      radOrderRepository
        .findAllForHospital(hospitalId, null)
        .count { it.status in setOf("ordered", "scheduled", "in_progress") }
    val snapshot =
      CapacitySnapshot(
        hospitalId = hospitalId,
        generatedAt = Instant.now(),
        bedsTotal = beds.size,
        bedsAvailable = beds.count { it.status == "available" },
        bedsOccupied = beds.count { it.status == "occupied" },
        otRoomsTotal = otRooms.size,
        otRoomsFree = otRooms.count { it.status == "operational" },
        admissionsActive = admissions.count { it.status == "admitted" },
        expectedDischargesToday =
          admissions.count {
            it.status == "admitted" &&
              it.expectedDischargeAt?.atZone(ZoneOffset.UTC)?.toLocalDate() == today
          },
        diagnosticsPending = labPending + radPending,
        equipmentDown = assetRepository.findByHospitalIdOrderByCodeAsc(hospitalId).count { it.status == "DOWN" },
        openWorkOrders = workOrderRepository.findByHospitalIdAndStatusOrderByCreatedAtDesc(hospitalId, "OPEN").size,
      ).also { it.createdBy = actorId }
    return snapshotRepository.save(snapshot)
  }

  @Transactional
  fun capacity(
    hospitalId: UUID,
    additionalAdmissions: Int,
    actorId: UUID?,
  ): Map<String, Any?> {
    if (additionalAdmissions < 0) throw IllegalArgumentException("TWIN_ADMISSIONS_INVALID")
    val snapshot = generateSnapshot(hospitalId, actorId)
    val bedHeadroom = snapshot.bedsAvailable - additionalAdmissions
    val evidence =
      listOf(
        mapOf(
          "factor" to "beds",
          "value" to snapshot.bedsAvailable,
          "required" to additionalAdmissions,
          "pass" to (bedHeadroom >= 0),
        ),
        mapOf(
          "factor" to "diagnosticsBacklog",
          "value" to snapshot.diagnosticsPending,
          "threshold" to DIAGNOSTICS_BACKLOG_LIMIT,
          "pass" to (snapshot.diagnosticsPending <= DIAGNOSTICS_BACKLOG_LIMIT),
        ),
        mapOf(
          "factor" to "equipmentDown",
          "value" to snapshot.equipmentDown,
          "threshold" to EQUIPMENT_DOWN_LIMIT,
          "pass" to (snapshot.equipmentDown <= EQUIPMENT_DOWN_LIMIT),
        ),
      )
    val canAccept = evidence.all { it["pass"] == true }
    return mapOf(
      "snapshot" to snapshot,
      "additionalAdmissions" to additionalAdmissions,
      "bedHeadroom" to bedHeadroom,
      "canAccept" to canAccept,
      "evidence" to evidence,
      "answer" to
        if (canAccept) {
          "Yes: $additionalAdmissions additional admission(s) can be accepted now."
        } else {
          "No: capacity constraints block $additionalAdmissions additional admission(s)."
        },
    )
  }

  @Transactional(readOnly = true)
  fun latest(hospitalId: UUID): CapacitySnapshot? = snapshotRepository.findTopByHospitalIdOrderByGeneratedAtDesc(hospitalId)

  /**
   * 07.03 event replay: rebuild the admitted-patient/bed state from the stored event stream for the
   * window and compare it with live counts. Transfers and bed assignments carry patientId since
   * 2026-11-27, so the replayed bed is the patient's current bed rather than their first one.
   */
  @Transactional(readOnly = true)
  fun replay(
    hospitalId: UUID,
    days: Int,
  ): Map<String, Any?> {
    require(days in 1..90) { "TWIN_REPLAY_WINDOW_INVALID" }
    val from = Instant.now().minusSeconds(days.toLong() * 86_400)
    val events = outboxRepository.findForHospitalSince(hospitalId, from)
    val bedByPatient = mutableMapOf<UUID, UUID>()
    val perDay = sortedMapOf<String, Int>()
    var applied = 0
    var ignored = 0
    events.forEach { event ->
      perDay.merge(
        event.occurredAt
          .atZone(ZoneOffset.UTC)
          .toLocalDate()
          .toString(),
        1,
        Int::plus,
      )
      val payload = event.payload
      val patientId =
        payload
          .get("patientId")
          ?.asText()
          ?.takeIf { it != "null" }
          ?.let(UUID::fromString)
      when (event.eventType) {
        "admission.admitted", "admission.bed_assigned" -> {
          val bedId =
            payload
              .get("bedId")
              ?.asText()
              ?.takeIf { it != "null" }
              ?.let(UUID::fromString)
          if (patientId != null && bedId != null) {
            bedByPatient[patientId] = bedId
            applied++
          } else {
            ignored++
          }
        }

        "admission.transferred" -> {
          val toBedId =
            payload
              .get("toBedId")
              ?.asText()
              ?.takeIf { it != "null" }
              ?.let(UUID::fromString)
          if (patientId != null && toBedId != null) {
            bedByPatient[patientId] = toBedId
            applied++
          } else {
            ignored++
          }
        }

        "admission.discharged" -> {
          if (patientId != null) {
            bedByPatient.remove(patientId)
            applied++
          } else {
            ignored++
          }
        }

        else -> {
          ignored++
        }
      }
    }
    val liveAdmissions = admissionRepository.findAllByHospital(hospitalId).count { it.status == "admitted" }
    val liveBedsOccupied = bedRepository.findByHospitalId(hospitalId).count { it.status == "occupied" }
    val mismatches =
      buildList {
        if (bedByPatient.size != liveAdmissions) add("admissionsActive replayed=${bedByPatient.size} live=$liveAdmissions")
        if (bedByPatient.values.toSet().size != liveBedsOccupied) add("bedsOccupied replayed=${bedByPatient.values.toSet().size} live=$liveBedsOccupied")
      }
    return mapOf(
      "windowDays" to days,
      "eventsScanned" to events.size,
      "eventsApplied" to applied,
      "eventsIgnored" to ignored,
      "perDay" to perDay,
      "replayed" to mapOf("admissionsActive" to bedByPatient.size, "bedsOccupied" to bedByPatient.values.toSet().size),
      "live" to mapOf("admissionsActive" to liveAdmissions, "bedsOccupied" to liveBedsOccupied),
      "mismatches" to mismatches,
      "reconciled" to mismatches.isEmpty(),
    )
  }

  // Compares the last materialized view with live operational counts; mismatches mean the twin drifted.
  @Transactional
  fun reconcile(
    hospitalId: UUID,
    actorId: UUID?,
  ): Map<String, Any?> {
    val previous = latest(hospitalId) ?: generateSnapshot(hospitalId, actorId)
    val live = generateSnapshot(hospitalId, actorId)
    val mismatches =
      buildList {
        if (previous.bedsAvailable != live.bedsAvailable) add("bedsAvailable ${previous.bedsAvailable} -> ${live.bedsAvailable}")
        if (previous.bedsOccupied != live.bedsOccupied) add("bedsOccupied ${previous.bedsOccupied} -> ${live.bedsOccupied}")
        if (previous.admissionsActive != live.admissionsActive) add("admissionsActive ${previous.admissionsActive} -> ${live.admissionsActive}")
        if (previous.diagnosticsPending != live.diagnosticsPending) add("diagnosticsPending ${previous.diagnosticsPending} -> ${live.diagnosticsPending}")
      }
    return mapOf(
      "previousGeneratedAt" to previous.generatedAt,
      "liveGeneratedAt" to live.generatedAt,
      "mismatches" to mismatches,
      "reconciled" to mismatches.isEmpty(),
    )
  }
}
