package com.squamed.core.masterdata.api

import com.squamed.core.masterdata.domain.ChargeItem
import com.squamed.core.masterdata.domain.DiagnosisCode
import com.squamed.core.masterdata.domain.Drug
import com.squamed.core.masterdata.domain.ImagingProcedure
import com.squamed.core.masterdata.domain.LabTest
import com.squamed.core.masterdata.domain.ProcedureCode
import java.math.BigDecimal
import java.util.UUID

data class DrugDto(
  val id: UUID?,
  val code: String?,
  val name: String?,
  val genericName: String?,
  val brandName: String?,
  val category: String?,
  val form: String?,
  val strength: String?,
  val unit: String?,
  val route: String?,
  val schedule: String?,
  val isControlled: Boolean?,
  val isAntibiotic: Boolean?,
  val requiresPrescription: Boolean?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: Drug) =
      DrugDto(
        id = entity.id,
        code = entity.code,
        name = entity.name,
        genericName = entity.genericName,
        brandName = entity.brandName,
        category = entity.category,
        form = entity.form,
        strength = entity.strength,
        unit = entity.unit,
        route = entity.route,
        schedule = entity.schedule,
        isControlled = entity.isControlled,
        isAntibiotic = entity.isAntibiotic,
        requiresPrescription = entity.requiresPrescription,
        isActive = entity.isActive,
      )
  }
}

data class LabTestDto(
  val id: UUID?,
  val code: String?,
  val name: String?,
  val category: String?,
  val specimenType: String?,
  val unit: String?,
  val referenceRange: String?,
  val isUrgent: Boolean?,
  val isActive: Boolean?,
  val criticalLow: Double? = null,
  val criticalHigh: Double? = null,
  val criticalSlaMinutes: Int? = null,
) {
  companion object {
    fun from(entity: LabTest) =
      LabTestDto(
        id = entity.id,
        code = entity.code,
        name = entity.name,
        category = entity.category,
        specimenType = entity.specimenType,
        unit = entity.unit,
        referenceRange = entity.referenceRange,
        isUrgent = entity.isUrgent,
        isActive = entity.isActive,
        criticalLow = entity.criticalLow,
        criticalHigh = entity.criticalHigh,
        criticalSlaMinutes = entity.criticalSlaMinutes,
      )
  }
}

data class ImagingProcedureDto(
  val id: UUID?,
  val code: String?,
  val name: String?,
  val modality: String?,
  val bodyPart: String?,
  val preparation: String?,
  val priceQar: BigDecimal?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: ImagingProcedure) =
      ImagingProcedureDto(
        id = entity.id,
        code = entity.code,
        name = entity.name,
        modality = entity.modality,
        bodyPart = entity.bodyPart,
        preparation = entity.preparation,
        priceQar = entity.priceQar,
        isActive = entity.isActive,
      )
  }
}

data class ProcedureCodeDto(
  val id: UUID?,
  val code: String?,
  val name: String?,
  val system: String?,
  val category: String?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: ProcedureCode) =
      ProcedureCodeDto(
        id = entity.id,
        code = entity.code,
        name = entity.name,
        system = entity.system,
        category = entity.category,
        isActive = entity.isActive,
      )
  }
}

data class DiagnosisCodeDto(
  val id: UUID?,
  val code: String?,
  val name: String?,
  val system: String?,
  val chapter: String?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: DiagnosisCode) =
      DiagnosisCodeDto(
        id = entity.id,
        code = entity.code,
        name = entity.name,
        system = entity.system,
        chapter = entity.chapter,
        isActive = entity.isActive,
      )
  }
}

data class ChargeItemDto(
  val id: UUID?,
  val code: String?,
  val name: String?,
  val category: String?,
  val priceQar: BigDecimal?,
  val taxRate: BigDecimal?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: ChargeItem) =
      ChargeItemDto(
        id = entity.id,
        code = entity.code,
        name = entity.name,
        category = entity.category,
        priceQar = entity.priceQar,
        taxRate = entity.taxRate,
        isActive = entity.isActive,
      )
  }
}

data class CatalogActiveDto(
  val isActive: Boolean?,
)

data class CatalogListResponse<T>(
  val items: List<T>,
)
