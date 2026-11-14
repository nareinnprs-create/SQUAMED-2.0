package com.squamed.core.hospitalconfig.api

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.hospitalconfig.domain.Bed
import com.squamed.core.hospitalconfig.domain.Building
import com.squamed.core.hospitalconfig.domain.Campus
import com.squamed.core.hospitalconfig.domain.ConfigDocument
import com.squamed.core.hospitalconfig.domain.Department
import com.squamed.core.hospitalconfig.domain.Hospital
import com.squamed.core.hospitalconfig.domain.HospitalService
import com.squamed.core.hospitalconfig.domain.Room
import com.squamed.core.hospitalconfig.domain.Specialty
import com.squamed.core.hospitalconfig.domain.Ward
import java.util.UUID

data class HospitalDto(
  val id: UUID?,
  val name: String?,
  val code: String?,
  val legalName: String?,
  val country: String?,
  val region: String?,
  val city: String?,
  val addressLine1: String?,
  val addressLine2: String?,
  val postalCode: String?,
  val phone: String?,
  val email: String?,
  val licenseNumber: String?,
  val timezone: String?,
  val currency: String?,
  val status: String?,
) {
  companion object {
    fun from(entity: Hospital) =
      HospitalDto(
        id = entity.id,
        name = entity.name,
        code = entity.code,
        legalName = entity.legalName,
        country = entity.country,
        region = entity.region,
        city = entity.city,
        addressLine1 = entity.addressLine1,
        addressLine2 = entity.addressLine2,
        postalCode = entity.postalCode,
        phone = entity.phone,
        email = entity.email,
        licenseNumber = entity.licenseNumber,
        timezone = entity.timezone,
        currency = entity.currency,
        status = entity.status,
      )
  }
}

data class CampusDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String?,
  val code: String?,
  val addressLine1: String?,
  val city: String?,
  val timezone: String?,
) {
  companion object {
    fun from(entity: Campus) =
      CampusDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        code = entity.code,
        addressLine1 = entity.addressLine1,
        city = entity.city,
        timezone = entity.timezone,
      )
  }
}

data class BuildingDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val campusId: UUID?,
  val name: String?,
  val code: String?,
  val floors: Int?,
) {
  companion object {
    fun from(entity: Building) =
      BuildingDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        campusId = entity.campusId,
        name = entity.name,
        code = entity.code,
        floors = entity.floors,
      )
  }
}

data class DepartmentDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String?,
  val code: String?,
  val type: String?,
  val parentId: UUID?,
  val status: String?,
  val description: String?,
) {
  companion object {
    fun from(entity: Department) =
      DepartmentDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        code = entity.code,
        type = entity.type,
        parentId = entity.parentId,
        status = entity.status,
        description = entity.description,
      )
  }
}

data class SpecialtyDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String?,
  val code: String?,
  val departmentId: UUID?,
  val description: String?,
) {
  companion object {
    fun from(entity: Specialty) =
      SpecialtyDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        code = entity.code,
        departmentId = entity.departmentId,
        description = entity.description,
      )
  }
}

data class WardDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val departmentId: UUID?,
  val name: String?,
  val code: String?,
  val type: String?,
  val roomCount: Int?,
  val bedCount: Int?,
) {
  companion object {
    fun from(entity: Ward) =
      WardDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        departmentId = entity.departmentId,
        name = entity.name,
        code = entity.code,
        type = entity.type,
        roomCount = entity.roomCount,
        bedCount = entity.bedCount,
      )
  }
}

data class RoomDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val wardId: UUID?,
  val buildingId: UUID?,
  val floor: Int?,
  val name: String?,
  val code: String?,
  val type: String?,
  val maxBeds: Int?,
) {
  companion object {
    fun from(entity: Room) =
      RoomDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        wardId = entity.wardId,
        buildingId = entity.buildingId,
        floor = entity.floor,
        name = entity.name,
        code = entity.code,
        type = entity.type,
        maxBeds = entity.maxBeds,
      )
  }
}

data class BedDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val wardId: UUID?,
  val roomId: UUID?,
  val label: String?,
  val type: String?,
  val status: String?,
) {
  companion object {
    fun from(entity: Bed) =
      BedDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        wardId = entity.wardId,
        roomId = entity.roomId,
        label = entity.label,
        type = entity.type,
        status = entity.status,
      )
  }
}

data class HospitalServiceDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val name: String?,
  val code: String?,
  val category: String?,
  val isActive: Boolean?,
  val description: String?,
) {
  companion object {
    fun from(entity: HospitalService) =
      HospitalServiceDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        name = entity.name,
        code = entity.code,
        category = entity.category,
        isActive = entity.isActive,
        description = entity.description,
      )
  }
}

data class BedStatusDto(
  val status: String?,
)

data class HospitalTreeDto(
  val hospital: HospitalDto,
  val campuses: List<CampusTreeDto> = emptyList(),
  val departments: List<DepartmentTreeDto> = emptyList(),
)

data class CampusTreeDto(
  val campus: CampusDto,
  val buildings: List<BuildingDto> = emptyList(),
)

data class DepartmentTreeDto(
  val department: DepartmentDto,
  val specialties: List<SpecialtyDto> = emptyList(),
  val wards: List<WardTreeDto> = emptyList(),
)

data class WardTreeDto(
  val ward: WardDto,
  val rooms: List<RoomTreeDto> = emptyList(),
)

data class RoomTreeDto(
  val room: RoomDto,
  val beds: List<BedDto> = emptyList(),
)

data class ConfigDocumentDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val type: String,
  val version: Int,
  val status: String,
  val content: JsonNode,
  val createdAt: String?,
) {
  companion object {
    fun from(entity: ConfigDocument) =
      ConfigDocumentDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        type = entity.type,
        version = entity.documentVersion,
        status = entity.status,
        content = entity.content,
        createdAt = entity.createdAt?.toString(),
      )
  }
}
