package com.squamed.core.interop.fhir

import com.squamed.core.encounter.domain.Diagnosis
import com.squamed.core.encounter.domain.Encounter
import com.squamed.core.encounter.domain.OrderEntry
import com.squamed.core.encounter.domain.Prescription
import com.squamed.core.encounter.domain.VitalSign
import com.squamed.core.encounter.repository.DiagnosisRepository
import com.squamed.core.encounter.repository.EncounterRepository
import com.squamed.core.encounter.repository.OrderEntryRepository
import com.squamed.core.encounter.repository.PrescriptionRepository
import com.squamed.core.encounter.repository.VitalSignRepository
import com.squamed.core.hospitalconfig.domain.Hospital
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.identity.domain.UserAccount
import com.squamed.core.identity.repository.UserAccountRepository
import com.squamed.core.identity.repository.UserProfileRepository
import com.squamed.core.laboratory.domain.LabOrder
import com.squamed.core.laboratory.repository.LabOrderRepository
import com.squamed.core.patient.domain.Allergy
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.AllergyRepository
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional(readOnly = true)
class FhirService(
  private val patientRepository: PatientRecordRepository,
  private val userAccountRepository: UserAccountRepository,
  private val userProfileRepository: UserProfileRepository,
  private val hospitalRepository: HospitalRepository,
  private val encounterRepository: EncounterRepository,
  private val diagnosisRepository: DiagnosisRepository,
  private val vitalSignRepository: VitalSignRepository,
  private val prescriptionRepository: PrescriptionRepository,
  private val orderEntryRepository: OrderEntryRepository,
  private val allergyRepository: AllergyRepository,
  private val labOrderRepository: LabOrderRepository,
) {
  fun bundle(resources: List<Map<String, Any?>>): Map<String, Any?> =
    mapOf(
      "resourceType" to "Bundle",
      "type" to "searchset",
      "total" to resources.size,
      "entry" to resources.map { mapOf("resource" to it) },
    )

  private fun reference(
    resourceType: String,
    id: UUID,
  ): Map<String, Any?> = mapOf("reference" to "$resourceType/$id")

  private fun code(value: String?): Map<String, Any?>? = value?.let { mapOf("coding" to listOf(mapOf("code" to it)), "text" to it) }

  fun patient(id: UUID): Map<String, Any?> = patient(id, patientRepository.findById(id).orElseThrow())

  private fun patient(
    id: UUID?,
    p: PatientRecord,
  ): Map<String, Any?> {
    val nameList =
      buildList {
        val given = listOfNotNull(p.firstName, p.middleName).filter { it.isNotBlank() }
        val family = p.lastName
        if (given.isNotEmpty() || family.isNotBlank()) {
          add(
            buildMap {
              if (given.isNotEmpty()) put("given", given)
              if (family.isNotBlank()) put("family", family)
              p.preferredName?.takeIf { it.isNotBlank() }?.let { put("text", it) }
            },
          )
        }
      }
    return mapOf(
      "resourceType" to "Patient",
      "id" to (id ?: p.id)?.toString(),
      "identifier" to
        listOfNotNull(
          p.mrn?.let { mapOf("system" to "urn:oid:1.2.3.4.5.6.7", "value" to it, "type" to mapOf("text" to "MRN")) },
          p.qid?.let { mapOf("system" to "urn:oid:2.16.840.1.113883.2.23.1.1", "value" to it, "type" to mapOf("text" to "QID")) },
        ),
      "active" to (p.recordsStatus != "duplicate"),
      "name" to nameList,
      "telecom" to
        listOfNotNull(
          p.phone?.let { mapOf("system" to "phone", "value" to it) },
          p.email?.let { mapOf("system" to "email", "value" to it) },
        ),
      "gender" to p.gender,
      "birthDate" to p.dateOfBirth?.toString(),
      "deceasedBoolean" to p.isDeceased,
      "maritalStatus" to code(p.maritalStatus),
      "address" to listOfNotNull(buildAddress(p.city, p.country).ifEmpty { null }),
    )
  }

  private fun buildAddress(
    city: String?,
    country: String?,
  ): Map<String, Any?> =
    buildMap {
      city?.let { put("city", it) }
      country?.let { put("country", it) }
    }

  fun practitioner(id: UUID): Map<String, Any?> {
    val account = userAccountRepository.findById(id).orElseThrow()
    return practitioner(id, account)
  }

  private fun practitioner(
    id: UUID?,
    a: UserAccount,
  ): Map<String, Any?> {
    val profile = id?.let { userProfileRepository.findByUserId(it) }?.orElse(null)
    return mapOf(
      "resourceType" to "Practitioner",
      "id" to id?.toString(),
      "active" to (a.status.name == "ACTIVE"),
      "name" to
        listOf(
          buildMap {
            profile?.firstName?.takeIf { it.isNotBlank() }?.let { put("given", listOf(it)) }
            profile?.lastName?.takeIf { it.isNotBlank() }?.let { put("family", it) }
            profile?.displayName?.takeIf { it.isNotBlank() }?.let { put("text", it) }
          }.ifEmpty { mapOf("text" to a.email) },
        ),
      "telecom" to
        listOfNotNull(
          a.email?.let { mapOf("system" to "email", "value" to it) },
          a.phone?.let { mapOf("system" to "phone", "value" to it) },
        ),
    )
  }

  fun organization(): Map<String, Any?> {
    val hospital =
      hospitalRepository.findAll().firstOrNull() ?: return mapOf(
        "resourceType" to "Organization",
        "id" to "hospital",
        "name" to "SQUAMED Hospital",
      )
    return organization(hospital.id, hospital)
  }

  private fun organization(
    id: UUID?,
    h: Hospital,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "Organization",
      "id" to id?.toString(),
      "name" to (h.legalName ?: h.name),
      "telecom" to
        listOfNotNull(
          h.phone?.let { mapOf("system" to "phone", "value" to it) },
          h.email?.let { mapOf("system" to "email", "value" to it) },
        ),
      "address" to
        listOf(
          buildMap {
            h.addressLine1?.let { put("line", listOf(it)) }
            h.city?.let { put("city", it) }
            h.country?.let { put("country", it) }
            h.postalCode?.let { put("postalCode", it) }
          }.ifEmpty { mapOf<String, Any?>() },
        ),
    )

  fun encounter(id: UUID): Map<String, Any?> = encounter(id, encounterRepository.findById(id).orElseThrow())

  private fun encounter(
    id: UUID?,
    e: Encounter,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "Encounter",
      "id" to id?.toString(),
      "status" to encounterStatus(e.status),
      "class" to mapOf("code" to (e.encounterType ?: "outpatient"), "system" to "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
      "type" to listOfNotNull(code(e.reason)),
      "subject" to e.patientId?.let { reference("Patient", it) },
      "period" to
        buildMap {
          e.startedAt?.let { put("start", it.atOffset(ZoneOffset.UTC).toString()) }
          e.endedAt?.let { put("end", it.atOffset(ZoneOffset.UTC).toString()) }
        }.ifEmpty { null },
    )

  private fun encounterStatus(status: String?): String =
    when (status) {
      "in-progress" -> "in-progress"
      "completed", "discharged" -> "finished"
      "cancelled" -> "cancelled"
      else -> "unknown"
    }

  fun condition(id: UUID): Map<String, Any?> {
    val dx = diagnosisRepository.findById(id).orElseThrow()
    return condition(id, dx)
  }

  private fun condition(
    id: UUID?,
    d: Diagnosis,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "Condition",
      "id" to id?.toString(),
      "subject" to d.patientId?.let { reference("Patient", it) },
      "encounter" to d.encounterId?.let { reference("Encounter", it) },
      "code" to listOfNotNull(code(d.name ?: d.code)),
      "verificationStatus" to mapOf("coding" to listOf(mapOf("code" to "confirmed"))),
      "clinicalStatus" to mapOf("coding" to listOf(mapOf("code" to "active"))),
    )

  fun observation(id: UUID): Map<String, Any?> {
    val v = vitalSignRepository.findById(id).orElseThrow()
    return observation(id, v)
  }

  private fun observation(
    id: UUID?,
    v: VitalSign,
  ): Map<String, Any?> {
    val components: MutableList<Map<String, Any?>> = mutableListOf()
    v.bloodPressureSystolic?.let { sys ->
      v.bloodPressureDiastolic?.let { dia ->
        components.add(valueComponent("8480-6", "Blood pressure systolic", sys, "mm[Hg]"))
        components.add(valueComponent("8462-4", "Blood pressure diastolic", dia, "mm[Hg]"))
      }
    }
    v.heartRate?.let { components.add(valueComponent("8867-4", "Heart rate", it, "/min")) }
    v.respiratoryRate?.let { components.add(valueComponent("9279-1", "Respiratory rate", it, "/min")) }
    v.temperatureC?.let { components.add(valueComponent("8310-5", "Body temperature", it, "Cel")) }
    v.spo2Percent?.let { components.add(valueComponent("59408-5", "Oxygen saturation", it, "%")) }
    return mapOf(
      "resourceType" to "Observation",
      "id" to id?.toString(),
      "status" to "final",
      "category" to
        listOf(
          mapOf(
            "coding" to
              listOf(
                mapOf("system" to "http://terminology.hl7.org/CodeSystem/observation-category", "code" to "vital-signs"),
              ),
          ),
        ),
      "code" to mapOf("coding" to listOf(mapOf("system" to "http://loinc.org", "code" to "8716-3", "display" to "Vital signs"))),
      "subject" to v.patientId?.let { reference("Patient", it) },
      "effectiveDateTime" to v.recordedAt?.atOffset(ZoneOffset.UTC).toString(),
      "performer" to v.recordedBy?.let { listOf(reference("Practitioner", it)) },
      if (components.isEmpty()) "valueString" to v.notes else "component" to components,
    )
  }

  private fun valueComponent(
    loinc: String,
    display: String,
    value: Number,
    unit: String,
  ): Map<String, Any?> =
    mapOf(
      "code" to mapOf("coding" to listOf(mapOf("system" to "http://loinc.org", "code" to loinc, "display" to display))),
      "valueQuantity" to
        mapOf(
          "value" to if (value is BigDecimal) value.toPlainString() else value,
          "unit" to unit,
          "system" to "http://unitsofmeasure.org",
          "code" to unit,
        ),
    )

  fun medicationRequest(id: UUID): Map<String, Any?> = medicationRequest(id, prescriptionRepository.findById(id).orElseThrow())

  private fun medicationRequest(
    id: UUID?,
    p: Prescription,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "MedicationRequest",
      "id" to id?.toString(),
      "status" to (p.status ?: "draft"),
      "intent" to "order",
      "subject" to p.patientId?.let { reference("Patient", it) },
      "encounter" to p.encounterId?.let { reference("Encounter", it) },
      "medicationCodeableConcept" to mapOf("text" to p.drugName),
      "requester" to p.prescribedBy?.let { reference("Practitioner", it) },
      "authoredOn" to p.prescribedAt?.atOffset(ZoneOffset.UTC).toString(),
      "dosageInstruction" to
        listOfNotNull(
          buildMap {
            p.dose?.let { put("text", it) }
            p.frequency?.let { put("timing", mapOf("text" to it)) }
            p.route?.let { put("route", mapOf("text" to it)) }
          }.ifEmpty { null },
        ),
    )

  fun serviceRequest(id: UUID): Map<String, Any?> = serviceRequest(id, orderEntryRepository.findById(id).orElseThrow())

  private fun serviceRequest(
    id: UUID?,
    o: OrderEntry,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "ServiceRequest",
      "id" to id?.toString(),
      "status" to (o.status ?: "active"),
      "intent" to "order",
      "subject" to o.patientId?.let { reference("Patient", it) },
      "encounter" to o.encounterId?.let { reference("Encounter", it) },
      "code" to code(o.orderName ?: o.orderCode),
      "requester" to o.orderedBy?.let { reference("Practitioner", it) },
      "authoredOn" to o.orderedAt?.atOffset(ZoneOffset.UTC).toString(),
    )

  fun allergyIntolerance(id: UUID): Map<String, Any?> {
    val a = allergyRepository.findById(id).orElseThrow()
    return allergyIntolerance(id, a)
  }

  private fun allergyIntolerance(
    id: UUID?,
    a: Allergy,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "AllergyIntolerance",
      "id" to id?.toString(),
      "clinicalStatus" to mapOf("coding" to listOf(mapOf("code" to if (a.isActive == true) "active" else "inactive"))),
      "type" to "allergy",
      "category" to listOf("medication"),
      "code" to mapOf("text" to a.allergen),
      "patient" to a.patientId?.let { reference("Patient", it) },
      "onset" to a.recordedDate?.toString(),
      "reaction" to
        listOfNotNull(
          buildMap {
            a.reaction?.let { put("manifestation", listOf(mapOf("text" to it))) }
            a.severity?.let { put("severity", it) }
          }.ifEmpty { null },
        ),
    )

  fun diagnosticReport(id: UUID): Map<String, Any?> = diagnosticReport(id, labOrderRepository.findById(id).orElseThrow())

  private fun diagnosticReport(
    id: UUID?,
    l: LabOrder,
  ): Map<String, Any?> =
    mapOf(
      "resourceType" to "DiagnosticReport",
      "id" to id?.toString(),
      "status" to labReportStatus(l.status, l.resultStatus),
      "subject" to l.patientId?.let { reference("Patient", it) },
      "encounter" to l.encounterId?.let { reference("Encounter", it) },
      "code" to mapOf("coding" to listOf(mapOf("code" to (l.orderNumber ?: "LAB"))), "text" to l.orderNumber),
      "issued" to l.releasedAt?.atOffset(ZoneOffset.UTC).toString(),
      "conclusion" to l.clinicalNote,
    )

  private fun labReportStatus(
    status: String?,
    resultStatus: String?,
  ): String =
    when {
      resultStatus == "released" -> "final"
      resultStatus == "preliminary" -> "preliminary"
      status == "cancelled" || status == "rejected" -> "cancelled"
      else -> "partial"
    }

  fun allPatients(): List<Map<String, Any?>> = patientRepository.findAll().map { patient(it.id, it) }

  fun allPractitioners(): List<Map<String, Any?>> = userAccountRepository.findAll().map { practitioner(it.id, it) }

  fun allOrganizations(): List<Map<String, Any?>> = hospitalRepository.findAll().map { organization(it.id, it) }

  fun allEncounters(): List<Map<String, Any?>> = encounterRepository.findAll().map { encounter(it.id, it) }

  fun allConditions(): List<Map<String, Any?>> = diagnosisRepository.findAll().map { condition(it.id, it) }

  fun allObservations(): List<Map<String, Any?>> = vitalSignRepository.findAll().map { observation(it.id, it) }

  fun allMedicationRequests(): List<Map<String, Any?>> = prescriptionRepository.findAll().map { medicationRequest(it.id, it) }

  fun allServiceRequests(): List<Map<String, Any?>> = orderEntryRepository.findAll().map { serviceRequest(it.id, it) }

  fun allAllergies(): List<Map<String, Any?>> = allergyRepository.findAll().map { allergyIntolerance(it.id, it) }

  fun allDiagnosticReports(): List<Map<String, Any?>> = labOrderRepository.findAll().map { diagnosticReport(it.id, it) }

  fun encountersByPatient(patientId: UUID): List<Map<String, Any?>> = encounterRepository.findByPatientId(patientId).map { encounter(it.id, it) }

  fun conditionsByPatient(patientId: UUID): List<Map<String, Any?>> = diagnosisRepository.findByPatientId(patientId).map { condition(it.id, it) }
}
