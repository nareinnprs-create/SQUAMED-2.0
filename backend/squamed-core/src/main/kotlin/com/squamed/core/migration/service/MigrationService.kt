package com.squamed.core.migration.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.migration.domain.MigrationJob
import com.squamed.core.migration.domain.StagedRow
import com.squamed.core.migration.repository.MigrationJobRepository
import com.squamed.core.migration.repository.StagedRowRepository
import com.squamed.core.patient.api.PatientCreateDto
import com.squamed.core.patient.repository.PatientRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class MigrationJobDetailView(
  val job: MigrationJob,
  val rows: List<StagedRow>,
)

// 07.10 migration studio: extract -> map (client payload) -> validate -> import -> reconcile -> rollback.
@Service
class MigrationService(
  private val jobRepository: MigrationJobRepository,
  private val rowRepository: StagedRowRepository,
  private val rowImporter: MigrationRowImporter,
  private val patientRepository: PatientRecordRepository,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    private val SOURCES = setOf("HIS", "EMR", "LIS", "ERP", "EXCEL")
    private val GENDERS = setOf("male", "female", "other")
  }

  @Transactional
  fun createJob(
    hospitalId: UUID,
    sourceType: String,
    rows: List<Map<String, Any?>>,
    actorId: UUID?,
  ): MigrationJobDetailView {
    val source = sourceType.trim().uppercase()
    if (source !in SOURCES) throw IllegalArgumentException("MIGRATION_SOURCE_INVALID")
    if (rows.isEmpty()) throw IllegalArgumentException("MIGRATION_ROWS_REQUIRED")
    val job =
      jobRepository.save(
        MigrationJob(
          hospitalId = hospitalId,
          sourceType = source,
          totalRows = rows.size,
        ).also { it.createdBy = actorId },
      )
    val jobId = checkNotNull(job.id)
    var valid = 0
    var errors = 0
    rows.forEachIndexed { index, row ->
      val error = validatePatient(row)
      val staged =
        rowRepository.save(
          StagedRow(
            hospitalId = hospitalId,
            jobId = jobId,
            rowNo = index + 1,
            payload = objectMapper.valueToTree(row),
            status = if (error == null) "VALID" else "ERROR",
            errorMessage = error,
          ).also { it.createdBy = actorId },
        )
      if (staged.status == "VALID") valid++ else errors++
    }
    job.validRows = valid
    job.errorRows = errors
    job.status = "VALIDATED"
    job.updatedBy = actorId
    jobRepository.save(job)
    return detail(jobId, hospitalId)
  }

  @Transactional
  fun importJob(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): MigrationJobDetailView {
    val job = find(id, hospitalId)
    if (job.status == "IMPORTED") throw IllegalStateException("MIGRATION_ALREADY_IMPORTED")
    if (job.status == "ROLLED_BACK") throw IllegalStateException("MIGRATION_ALREADY_ROLLED_BACK")
    if (job.status != "VALIDATED") throw IllegalStateException("MIGRATION_NOT_VALIDATED")
    var created = 0
    var errors = job.errorRows
    rowRepository.findByJobIdOrderByRowNoAsc(id).filter { it.status == "VALID" }.forEach { row ->
      try {
        val dto = toPatientDto(hospitalId, row)
        row.entityId = rowImporter.importPatient(dto)
        row.updatedBy = actorId
        rowRepository.save(row)
        created++
      } catch (ex: RuntimeException) {
        row.status = "ERROR"
        row.errorMessage = ex.message?.take(400) ?: "IMPORT_FAILED"
        row.updatedBy = actorId
        rowRepository.save(row)
        errors++
      }
    }
    job.status = "IMPORTED"
    job.validRows = created
    job.errorRows = errors
    job.importedAt = Instant.now()
    job.updatedBy = actorId
    jobRepository.save(job)
    return detail(id, hospitalId)
  }

  @Transactional
  fun rollback(
    id: UUID,
    hospitalId: UUID,
    actorId: UUID?,
  ): MigrationJobDetailView {
    val job = find(id, hospitalId)
    if (job.status != "IMPORTED") throw IllegalStateException("MIGRATION_NOT_IMPORTED")
    rowRepository
      .findByJobIdOrderByRowNoAsc(id)
      .mapNotNull { it.entityId }
      .forEach { patientId ->
        patientRepository.findById(patientId).orElse(null)?.let { patient ->
          // Soft rollback: the imported record is hidden from search/counts, never hard-deleted.
          patient.recordsStatus = "duplicate"
          patient.updatedBy = actorId
          patientRepository.save(patient)
        }
      }
    job.status = "ROLLED_BACK"
    job.rolledBackAt = Instant.now()
    job.updatedBy = actorId
    jobRepository.save(job)
    return detail(id, hospitalId)
  }

  @Transactional(readOnly = true)
  fun jobs(hospitalId: UUID): List<MigrationJob> = jobRepository.findByHospitalIdOrderByCreatedAtDesc(hospitalId)

  @Transactional(readOnly = true)
  fun detail(
    id: UUID,
    hospitalId: UUID,
  ): MigrationJobDetailView {
    val job = find(id, hospitalId)
    return MigrationJobDetailView(job, rowRepository.findByJobIdOrderByRowNoAsc(id))
  }

  private fun validatePatient(row: Map<String, Any?>): String? {
    val first = row["firstName"]?.toString()?.trim()
    val last = row["lastName"]?.toString()?.trim()
    val gender = row["gender"]?.toString()?.trim()?.lowercase()
    if (first.isNullOrBlank()) return "firstName required"
    if (last.isNullOrBlank()) return "lastName required"
    if (gender == null || gender !in GENDERS) return "gender must be male/female/other"
    val dob = row["dateOfBirth"]?.toString()?.trim()
    if (dob.isNullOrBlank()) return "dateOfBirth required"
    if (runCatching { LocalDate.parse(dob) }.isFailure) return "dateOfBirth must be ISO yyyy-MM-dd"
    return null
  }

  private fun toPatientDto(
    hospitalId: UUID,
    row: StagedRow,
  ): PatientCreateDto {
    val node = row.payload

    fun text(key: String): String? =
      node
        .path(key)
        .takeIf { it.isTextual }
        ?.asText()
        ?.trim()
        ?.ifBlank { null }
    return PatientCreateDto(
      hospitalId = hospitalId,
      firstName = text("firstName"),
      middleName = text("middleName"),
      lastName = text("lastName"),
      gender = text("gender"),
      dateOfBirth = text("dateOfBirth")?.let(LocalDate::parse),
      phone = text("phone"),
      email = text("email"),
      nationality = text("nationality"),
      externalId = text("externalId"),
    )
  }

  private fun find(
    id: UUID,
    hospitalId: UUID,
  ): MigrationJob = jobRepository.findByIdAndHospitalId(id, hospitalId) ?: throw IllegalArgumentException("MIGRATION_JOB_NOT_FOUND")
}
