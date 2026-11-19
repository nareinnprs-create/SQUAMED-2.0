package com.squamed.core.masterdata.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.masterdata.domain.ChargeItem
import com.squamed.core.masterdata.domain.DiagnosisCode
import com.squamed.core.masterdata.domain.Drug
import com.squamed.core.masterdata.domain.ImagingProcedure
import com.squamed.core.masterdata.domain.LabTest
import com.squamed.core.masterdata.domain.ProcedureCode
import com.squamed.core.masterdata.repository.ChargeItemRepository
import com.squamed.core.masterdata.repository.DiagnosisCodeRepository
import com.squamed.core.masterdata.repository.DrugRepository
import com.squamed.core.masterdata.repository.ImagingProcedureRepository
import com.squamed.core.masterdata.repository.LabTestRepository
import com.squamed.core.masterdata.repository.ProcedureCodeRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/master-data")
class MasterDataController(
  private val drugRepository: DrugRepository,
  private val labTestRepository: LabTestRepository,
  private val imagingProcedureRepository: ImagingProcedureRepository,
  private val procedureCodeRepository: ProcedureCodeRepository,
  private val diagnosisCodeRepository: DiagnosisCodeRepository,
  private val chargeItemRepository: ChargeItemRepository,
) {
  @GetMapping("/drugs")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listDrugs(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<DrugDto>> {
    val pageable = buildPageable(page, size)
    val paged =
      when {
        search != null && active != null -> {
          drugRepository.search("%$search%", "%$search%", active, pageable)
        }

        search != null -> {
          drugRepository.search("%$search%", "%$search%", pageable)
        }

        active != null -> {
          drugRepository.findByIsActive(active, pageable)
        }

        else -> {
          drugRepository.findAll(pageable)
        }
      }
    return ApiResponse.ok(paged.content.map { DrugDto.from(it) }, paged)
  }

  @PostMapping("/drugs")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createDrug(
    @RequestBody dto: DrugDto,
  ): ApiResponse<DrugDto> {
    val drug =
      drugRepository.save(
        Drug(
          code = dto.code ?: throw IllegalArgumentException("DRUG_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("DRUG_NAME_REQUIRED"),
          genericName = dto.genericName,
          brandName = dto.brandName,
          category = dto.category,
          form = dto.form,
          strength = dto.strength,
          unit = dto.unit,
          route = dto.route,
          schedule = dto.schedule,
          isControlled = dto.isControlled ?: false,
          isAntibiotic = dto.isAntibiotic ?: false,
          requiresPrescription = dto.requiresPrescription ?: true,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(DrugDto.from(drug))
  }

  @PutMapping("/drugs/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateDrug(
    @PathVariable id: UUID,
    @RequestBody dto: DrugDto,
  ): ApiResponse<DrugDto> {
    val drug = drugRepository.findById(id).orElseThrow { IllegalArgumentException("DRUG_NOT_FOUND") }
    drug.code = dto.code ?: drug.code
    drug.name = dto.name ?: drug.name
    drug.genericName = dto.genericName ?: drug.genericName
    drug.brandName = dto.brandName ?: drug.brandName
    drug.category = dto.category ?: drug.category
    drug.form = dto.form ?: drug.form
    drug.strength = dto.strength ?: drug.strength
    drug.unit = dto.unit ?: drug.unit
    drug.route = dto.route ?: drug.route
    drug.schedule = dto.schedule ?: drug.schedule
    drug.isControlled = dto.isControlled ?: drug.isControlled
    drug.isAntibiotic = dto.isAntibiotic ?: drug.isAntibiotic
    drug.requiresPrescription = dto.requiresPrescription ?: drug.requiresPrescription
    drug.isActive = dto.isActive ?: drug.isActive
    drugRepository.save(drug)
    return ApiResponse.ok(DrugDto.from(drug))
  }

  @PatchMapping("/drugs/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setDrugActive(
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<DrugDto> {
    val drug = drugRepository.findById(id).orElseThrow { IllegalArgumentException("DRUG_NOT_FOUND") }
    drug.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    drugRepository.save(drug)
    return ApiResponse.ok(DrugDto.from(drug))
  }

  @GetMapping("/lab-tests")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listLabTests(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<LabTestDto>> {
    val pageable = buildPageable(page, size)
    val paged =
      when {
        search != null && active != null -> {
          labTestRepository.search("%$search%", "%$search%", active, pageable)
        }

        search != null -> {
          labTestRepository.search("%$search%", "%$search%", pageable)
        }

        active != null -> {
          labTestRepository.findByIsActive(active, pageable)
        }

        else -> {
          labTestRepository.findAll(pageable)
        }
      }
    return ApiResponse.ok(paged.content.map { LabTestDto.from(it) }, paged)
  }

  @PostMapping("/lab-tests")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createLabTest(
    @RequestBody dto: LabTestDto,
  ): ApiResponse<LabTestDto> {
    val labTest =
      labTestRepository.save(
        LabTest(
          code = dto.code ?: throw IllegalArgumentException("LAB_TEST_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("LAB_TEST_NAME_REQUIRED"),
          category = dto.category,
          specimenType = dto.specimenType,
          unit = dto.unit,
          referenceRange = dto.referenceRange,
          isUrgent = dto.isUrgent ?: false,
          isActive = dto.isActive ?: true,
          criticalLow = dto.criticalLow,
          criticalHigh = dto.criticalHigh,
          criticalSlaMinutes = dto.criticalSlaMinutes ?: 60,
        ),
      )
    return ApiResponse.ok(LabTestDto.from(labTest))
  }

  @PutMapping("/lab-tests/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateLabTest(
    @PathVariable id: UUID,
    @RequestBody dto: LabTestDto,
  ): ApiResponse<LabTestDto> {
    val labTest = labTestRepository.findById(id).orElseThrow { IllegalArgumentException("LAB_TEST_NOT_FOUND") }
    labTest.code = dto.code ?: labTest.code
    labTest.name = dto.name ?: labTest.name
    labTest.category = dto.category ?: labTest.category
    labTest.specimenType = dto.specimenType ?: labTest.specimenType
    labTest.unit = dto.unit ?: labTest.unit
    labTest.referenceRange = dto.referenceRange ?: labTest.referenceRange
    labTest.isUrgent = dto.isUrgent ?: labTest.isUrgent
    labTest.isActive = dto.isActive ?: labTest.isActive
    if (dto.criticalLow != null) labTest.criticalLow = dto.criticalLow
    if (dto.criticalHigh != null) labTest.criticalHigh = dto.criticalHigh
    if (dto.criticalSlaMinutes != null) labTest.criticalSlaMinutes = dto.criticalSlaMinutes
    labTestRepository.save(labTest)
    return ApiResponse.ok(LabTestDto.from(labTest))
  }

  @PatchMapping("/lab-tests/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setLabTestActive(
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<LabTestDto> {
    val labTest = labTestRepository.findById(id).orElseThrow { IllegalArgumentException("LAB_TEST_NOT_FOUND") }
    labTest.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    labTestRepository.save(labTest)
    return ApiResponse.ok(LabTestDto.from(labTest))
  }

  @GetMapping("/imaging-procedures")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listImagingProcedures(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<ImagingProcedureDto>> {
    val pageable = buildPageable(page, size)
    val paged =
      when {
        search != null && active != null -> {
          imagingProcedureRepository.search("%$search%", "%$search%", active, pageable)
        }

        search != null -> {
          imagingProcedureRepository.search("%$search%", "%$search%", pageable)
        }

        active != null -> {
          imagingProcedureRepository.findByIsActive(active, pageable)
        }

        else -> {
          imagingProcedureRepository.findAll(pageable)
        }
      }
    return ApiResponse.ok(paged.content.map { ImagingProcedureDto.from(it) }, paged)
  }

  @PostMapping("/imaging-procedures")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createImagingProcedure(
    @RequestBody dto: ImagingProcedureDto,
  ): ApiResponse<ImagingProcedureDto> {
    val imagingProcedure =
      imagingProcedureRepository.save(
        ImagingProcedure(
          code = dto.code ?: throw IllegalArgumentException("IMAGING_PROCEDURE_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("IMAGING_PROCEDURE_NAME_REQUIRED"),
          modality = dto.modality,
          bodyPart = dto.bodyPart,
          preparation = dto.preparation,
          priceQar = dto.priceQar,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(ImagingProcedureDto.from(imagingProcedure))
  }

  @PutMapping("/imaging-procedures/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateImagingProcedure(
    @PathVariable id: UUID,
    @RequestBody dto: ImagingProcedureDto,
  ): ApiResponse<ImagingProcedureDto> {
    val imagingProcedure =
      imagingProcedureRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("IMAGING_PROCEDURE_NOT_FOUND") }
    imagingProcedure.code = dto.code ?: imagingProcedure.code
    imagingProcedure.name = dto.name ?: imagingProcedure.name
    imagingProcedure.modality = dto.modality ?: imagingProcedure.modality
    imagingProcedure.bodyPart = dto.bodyPart ?: imagingProcedure.bodyPart
    imagingProcedure.preparation = dto.preparation ?: imagingProcedure.preparation
    imagingProcedure.priceQar = dto.priceQar ?: imagingProcedure.priceQar
    imagingProcedure.isActive = dto.isActive ?: imagingProcedure.isActive
    imagingProcedureRepository.save(imagingProcedure)
    return ApiResponse.ok(ImagingProcedureDto.from(imagingProcedure))
  }

  @PatchMapping("/imaging-procedures/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setImagingProcedureActive(
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<ImagingProcedureDto> {
    val imagingProcedure =
      imagingProcedureRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("IMAGING_PROCEDURE_NOT_FOUND") }
    imagingProcedure.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    imagingProcedureRepository.save(imagingProcedure)
    return ApiResponse.ok(ImagingProcedureDto.from(imagingProcedure))
  }

  @GetMapping("/procedure-codes")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listProcedureCodes(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<ProcedureCodeDto>> {
    val pageable = buildPageable(page, size)
    val paged =
      when {
        search != null && active != null -> {
          procedureCodeRepository.search("%$search%", "%$search%", active, pageable)
        }

        search != null -> {
          procedureCodeRepository.search("%$search%", "%$search%", pageable)
        }

        active != null -> {
          procedureCodeRepository.findByIsActive(active, pageable)
        }

        else -> {
          procedureCodeRepository.findAll(pageable)
        }
      }
    return ApiResponse.ok(paged.content.map { ProcedureCodeDto.from(it) }, paged)
  }

  @PostMapping("/procedure-codes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createProcedureCode(
    @RequestBody dto: ProcedureCodeDto,
  ): ApiResponse<ProcedureCodeDto> {
    val procedureCode =
      procedureCodeRepository.save(
        ProcedureCode(
          code = dto.code ?: throw IllegalArgumentException("PROCEDURE_CODE_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("PROCEDURE_CODE_NAME_REQUIRED"),
          system = dto.system ?: throw IllegalArgumentException("PROCEDURE_CODE_SYSTEM_REQUIRED"),
          category = dto.category,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(ProcedureCodeDto.from(procedureCode))
  }

  @PutMapping("/procedure-codes/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateProcedureCode(
    @PathVariable id: UUID,
    @RequestBody dto: ProcedureCodeDto,
  ): ApiResponse<ProcedureCodeDto> {
    val procedureCode =
      procedureCodeRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("PROCEDURE_CODE_NOT_FOUND") }
    procedureCode.code = dto.code ?: procedureCode.code
    procedureCode.name = dto.name ?: procedureCode.name
    procedureCode.system = dto.system ?: procedureCode.system
    procedureCode.category = dto.category ?: procedureCode.category
    procedureCode.isActive = dto.isActive ?: procedureCode.isActive
    procedureCodeRepository.save(procedureCode)
    return ApiResponse.ok(ProcedureCodeDto.from(procedureCode))
  }

  @PatchMapping("/procedure-codes/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setProcedureCodeActive(
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<ProcedureCodeDto> {
    val procedureCode =
      procedureCodeRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("PROCEDURE_CODE_NOT_FOUND") }
    procedureCode.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    procedureCodeRepository.save(procedureCode)
    return ApiResponse.ok(ProcedureCodeDto.from(procedureCode))
  }

  @GetMapping("/diagnosis-codes")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listDiagnosisCodes(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<DiagnosisCodeDto>> {
    val pageable = buildPageable(page, size)
    val paged =
      when {
        search != null && active != null -> {
          diagnosisCodeRepository.search("%$search%", "%$search%", active, pageable)
        }

        search != null -> {
          diagnosisCodeRepository.search("%$search%", "%$search%", pageable)
        }

        active != null -> {
          diagnosisCodeRepository.findByIsActive(active, pageable)
        }

        else -> {
          diagnosisCodeRepository.findAll(pageable)
        }
      }
    return ApiResponse.ok(paged.content.map { DiagnosisCodeDto.from(it) }, paged)
  }

  @PostMapping("/diagnosis-codes")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createDiagnosisCode(
    @RequestBody dto: DiagnosisCodeDto,
  ): ApiResponse<DiagnosisCodeDto> {
    val diagnosisCode =
      diagnosisCodeRepository.save(
        DiagnosisCode(
          code = dto.code ?: throw IllegalArgumentException("DIAGNOSIS_CODE_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("DIAGNOSIS_CODE_NAME_REQUIRED"),
          system = dto.system ?: "ICD-10",
          chapter = dto.chapter,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(DiagnosisCodeDto.from(diagnosisCode))
  }

  @PutMapping("/diagnosis-codes/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateDiagnosisCode(
    @PathVariable id: UUID,
    @RequestBody dto: DiagnosisCodeDto,
  ): ApiResponse<DiagnosisCodeDto> {
    val diagnosisCode =
      diagnosisCodeRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("DIAGNOSIS_CODE_NOT_FOUND") }
    diagnosisCode.code = dto.code ?: diagnosisCode.code
    diagnosisCode.name = dto.name ?: diagnosisCode.name
    diagnosisCode.system = dto.system ?: diagnosisCode.system
    diagnosisCode.chapter = dto.chapter ?: diagnosisCode.chapter
    diagnosisCode.isActive = dto.isActive ?: diagnosisCode.isActive
    diagnosisCodeRepository.save(diagnosisCode)
    return ApiResponse.ok(DiagnosisCodeDto.from(diagnosisCode))
  }

  @PatchMapping("/diagnosis-codes/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setDiagnosisCodeActive(
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<DiagnosisCodeDto> {
    val diagnosisCode =
      diagnosisCodeRepository
        .findById(id)
        .orElseThrow { IllegalArgumentException("DIAGNOSIS_CODE_NOT_FOUND") }
    diagnosisCode.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    diagnosisCodeRepository.save(diagnosisCode)
    return ApiResponse.ok(DiagnosisCodeDto.from(diagnosisCode))
  }

  @GetMapping("/charge-items")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listChargeItems(
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<ChargeItemDto>> {
    val pageable = buildPageable(page, size)
    val paged =
      when {
        search != null && active != null -> {
          chargeItemRepository.search("%$search%", "%$search%", active, pageable)
        }

        search != null -> {
          chargeItemRepository.search("%$search%", "%$search%", pageable)
        }

        active != null -> {
          chargeItemRepository.findByIsActive(active, pageable)
        }

        else -> {
          chargeItemRepository.findAll(pageable)
        }
      }
    return ApiResponse.ok(paged.content.map { ChargeItemDto.from(it) }, paged)
  }

  @PostMapping("/charge-items")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createChargeItem(
    @RequestBody dto: ChargeItemDto,
  ): ApiResponse<ChargeItemDto> {
    val chargeItem =
      chargeItemRepository.save(
        ChargeItem(
          code = dto.code ?: throw IllegalArgumentException("CHARGE_ITEM_CODE_REQUIRED"),
          name = dto.name ?: throw IllegalArgumentException("CHARGE_ITEM_NAME_REQUIRED"),
          category = dto.category,
          priceQar = dto.priceQar ?: BigDecimal.ZERO,
          taxRate = dto.taxRate ?: BigDecimal.ZERO,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(ChargeItemDto.from(chargeItem))
  }

  @PutMapping("/charge-items/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateChargeItem(
    @PathVariable id: UUID,
    @RequestBody dto: ChargeItemDto,
  ): ApiResponse<ChargeItemDto> {
    val chargeItem = chargeItemRepository.findById(id).orElseThrow { IllegalArgumentException("CHARGE_ITEM_NOT_FOUND") }
    chargeItem.code = dto.code ?: chargeItem.code
    chargeItem.name = dto.name ?: chargeItem.name
    chargeItem.category = dto.category ?: chargeItem.category
    chargeItem.priceQar = dto.priceQar ?: chargeItem.priceQar
    chargeItem.taxRate = dto.taxRate ?: chargeItem.taxRate
    chargeItem.isActive = dto.isActive ?: chargeItem.isActive
    chargeItemRepository.save(chargeItem)
    return ApiResponse.ok(ChargeItemDto.from(chargeItem))
  }

  @PatchMapping("/charge-items/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setChargeItemActive(
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<ChargeItemDto> {
    val chargeItem = chargeItemRepository.findById(id).orElseThrow { IllegalArgumentException("CHARGE_ITEM_NOT_FOUND") }
    chargeItem.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    chargeItemRepository.save(chargeItem)
    return ApiResponse.ok(ChargeItemDto.from(chargeItem))
  }

  private fun buildPageable(
    page: Int,
    size: Int,
  ): PageRequest = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(Sort.Direction.ASC, "name"))
}
