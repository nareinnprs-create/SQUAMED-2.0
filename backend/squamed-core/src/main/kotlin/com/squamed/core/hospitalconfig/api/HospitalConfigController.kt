package com.squamed.core.hospitalconfig.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.hospitalconfig.domain.Bed
import com.squamed.core.hospitalconfig.domain.Department
import com.squamed.core.hospitalconfig.domain.Hospital
import com.squamed.core.hospitalconfig.domain.HospitalService
import com.squamed.core.hospitalconfig.domain.Specialty
import com.squamed.core.hospitalconfig.domain.Ward
import com.squamed.core.hospitalconfig.repository.BedRepository
import com.squamed.core.hospitalconfig.repository.BuildingRepository
import com.squamed.core.hospitalconfig.repository.CampusRepository
import com.squamed.core.hospitalconfig.repository.DepartmentRepository
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import com.squamed.core.hospitalconfig.repository.HospitalServiceRepository
import com.squamed.core.hospitalconfig.repository.RoomRepository
import com.squamed.core.hospitalconfig.repository.SpecialtyRepository
import com.squamed.core.hospitalconfig.repository.WardRepository
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/hospital-config")
class HospitalConfigController(
  private val hospitalRepository: HospitalRepository,
  private val campusRepository: CampusRepository,
  private val buildingRepository: BuildingRepository,
  private val departmentRepository: DepartmentRepository,
  private val specialtyRepository: SpecialtyRepository,
  private val wardRepository: WardRepository,
  private val roomRepository: RoomRepository,
  private val bedRepository: BedRepository,
  private val hospitalServiceRepository: HospitalServiceRepository,
) {
  @GetMapping("/hospitals")
  @RequiresPermission("hospital-config:read:hospital")
  @Transactional(readOnly = true)
  fun listHospitals(
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<List<HospitalDto>> {
    // 00.04 tenant sweep: bare /hospitals has no {id} segment so TenantInterceptor never fires —
    // the tenant directory is a platform-admin surface (principal.hospitalId == null).
    requirePlatformAdmin(principal)
    val hospitals = hospitalRepository.findAll().map { HospitalDto.from(it) }
    return ApiResponse.ok(hospitals)
  }

  @PostMapping("/hospitals")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("hospital-config:write:hospital")
  @Transactional
  fun createHospital(
    @RequestBody dto: HospitalDto,
    @AuthenticationPrincipal principal: SquamedPrincipal,
  ): ApiResponse<HospitalDto> {
    requirePlatformAdmin(principal)
    val hospital =
      hospitalRepository.save(
        Hospital(
          name = dto.name ?: throw IllegalArgumentException("HOSPITAL_NAME_REQUIRED"),
          code = dto.code ?: throw IllegalArgumentException("HOSPITAL_CODE_REQUIRED"),
          country = dto.country ?: throw IllegalArgumentException("HOSPITAL_COUNTRY_REQUIRED"),
          legalName = dto.legalName,
          region = dto.region,
          city = dto.city,
          addressLine1 = dto.addressLine1,
          addressLine2 = dto.addressLine2,
          postalCode = dto.postalCode,
          phone = dto.phone,
          email = dto.email,
          licenseNumber = dto.licenseNumber,
          timezone = dto.timezone ?: "UTC",
          currency = dto.currency ?: "USD",
          status = dto.status ?: "active",
        ),
      )
    return ApiResponse.ok(HospitalDto.from(hospital))
  }

  @GetMapping("/hospitals/{id}")
  @RequiresPermission("hospital-config:read:hospital")
  @Transactional(readOnly = true)
  fun getHospital(
    @PathVariable id: UUID,
  ): ApiResponse<HospitalDto> {
    val hospital = hospitalRepository.findById(id).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    return ApiResponse.ok(HospitalDto.from(hospital))
  }

  @PutMapping("/hospitals/{id}")
  @RequiresPermission("hospital-config:write:hospital")
  @Transactional
  fun updateHospital(
    @PathVariable id: UUID,
    @RequestBody dto: HospitalDto,
  ): ApiResponse<HospitalDto> {
    val hospital = hospitalRepository.findById(id).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    hospital.name = dto.name ?: hospital.name
    hospital.code = dto.code ?: hospital.code
    hospital.country = dto.country ?: hospital.country
    hospital.legalName = dto.legalName ?: hospital.legalName
    hospital.region = dto.region ?: hospital.region
    hospital.city = dto.city ?: hospital.city
    hospital.addressLine1 = dto.addressLine1 ?: hospital.addressLine1
    hospital.addressLine2 = dto.addressLine2 ?: hospital.addressLine2
    hospital.postalCode = dto.postalCode ?: hospital.postalCode
    hospital.phone = dto.phone ?: hospital.phone
    hospital.email = dto.email ?: hospital.email
    hospital.licenseNumber = dto.licenseNumber ?: hospital.licenseNumber
    hospital.timezone = dto.timezone ?: hospital.timezone
    hospital.currency = dto.currency ?: hospital.currency
    hospital.status = dto.status ?: hospital.status
    hospitalRepository.save(hospital)
    return ApiResponse.ok(HospitalDto.from(hospital))
  }

  @DeleteMapping("/hospitals/{id}")
  @RequiresPermission("hospital-config:write:hospital")
  @Transactional
  fun deleteHospital(
    @PathVariable id: UUID,
  ): ApiResponse<Unit> {
    val hospital = hospitalRepository.findById(id).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    hospitalRepository.delete(hospital)
    return ApiResponse.ok()
  }

  @GetMapping("/hospitals/{hospitalId}/tree")
  @RequiresPermission("hospital-config:read:hospital")
  @Transactional(readOnly = true)
  fun getHospitalTree(
    @PathVariable hospitalId: UUID,
  ): ApiResponse<HospitalTreeDto> {
    val hospital = hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
    val campuses = campusRepository.findByHospitalId(hospitalId)
    val buildings = buildingRepository.findByHospitalId(hospitalId)
    val departments = departmentRepository.findByHospitalId(hospitalId)
    val specialties = specialtyRepository.findByHospitalId(hospitalId)
    val wards = wardRepository.findByHospitalId(hospitalId)
    val rooms = roomRepository.findByHospitalId(hospitalId)
    val beds = bedRepository.findByHospitalId(hospitalId)

    val buildingsByCampus = buildings.groupBy { it.campusId }
    val campusTree =
      campuses.map { campus ->
        CampusTreeDto(
          campus = CampusDto.from(campus),
          buildings = buildingsByCampus[campus.id].orEmpty().map { BuildingDto.from(it) },
        )
      }

    val bedsByRoom = beds.groupBy { it.roomId }
    val roomsByWard = rooms.groupBy { it.wardId }
    val roomTree =
      wards.map { ward ->
        WardTreeDto(
          ward = WardDto.from(ward),
          rooms =
            roomsByWard[ward.id].orEmpty().map { room ->
              RoomTreeDto(
                room = RoomDto.from(room),
                beds = bedsByRoom[room.id].orEmpty().map { BedDto.from(it) },
              )
            },
        )
      }

    val specialtiesByDepartment = specialties.groupBy { it.departmentId }
    val departmentsWithChildren =
      departments.map { department ->
        DepartmentTreeDto(
          department = DepartmentDto.from(department),
          specialties = specialtiesByDepartment[department.id].orEmpty().map { SpecialtyDto.from(it) },
          wards = roomTree.filter { it.ward.departmentId == department.id },
        )
      }

    return ApiResponse.ok(
      HospitalTreeDto(
        hospital = HospitalDto.from(hospital),
        campuses = campusTree,
        departments = departmentsWithChildren,
      ),
    )
  }

  @GetMapping("/hospitals/{hospitalId}/departments")
  @RequiresPermission("hospital-config:read:department")
  @Transactional(readOnly = true)
  fun listDepartments(
    @PathVariable hospitalId: UUID,
    @RequestParam(name = "type", required = false) type: String?,
  ): ApiResponse<List<DepartmentDto>> {
    val departments =
      if (type != null) {
        departmentRepository.findByHospitalIdAndType(hospitalId, type)
      } else {
        departmentRepository.findByHospitalId(hospitalId)
      }
    return ApiResponse.ok(departments.map { DepartmentDto.from(it) })
  }

  @PostMapping("/hospitals/{hospitalId}/departments")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("hospital-config:write:department")
  @Transactional
  fun createDepartment(
    @PathVariable hospitalId: UUID,
    @RequestBody dto: DepartmentDto,
  ): ApiResponse<DepartmentDto> {
    requireHospitalExists(hospitalId)
    val department =
      departmentRepository.save(
        Department(
          hospitalId = hospitalId,
          name = dto.name ?: throw IllegalArgumentException("DEPARTMENT_NAME_REQUIRED"),
          code = dto.code ?: throw IllegalArgumentException("DEPARTMENT_CODE_REQUIRED"),
          type = dto.type ?: throw IllegalArgumentException("DEPARTMENT_TYPE_REQUIRED"),
          parentId = dto.parentId,
          status = dto.status ?: "active",
          description = dto.description,
        ),
      )
    return ApiResponse.ok(DepartmentDto.from(department))
  }

  @PutMapping("/hospitals/{hospitalId}/departments/{id}")
  @RequiresPermission("hospital-config:write:department")
  @Transactional
  fun updateDepartment(
    @PathVariable hospitalId: UUID,
    @PathVariable id: UUID,
    @RequestBody dto: DepartmentDto,
  ): ApiResponse<DepartmentDto> {
    val department = departmentRepository.findById(id).orElseThrow { IllegalArgumentException("DEPARTMENT_NOT_FOUND") }
    department.name = dto.name ?: department.name
    department.code = dto.code ?: department.code
    department.type = dto.type ?: department.type
    department.parentId = dto.parentId ?: department.parentId
    department.status = dto.status ?: department.status
    department.description = dto.description ?: department.description
    departmentRepository.save(department)
    return ApiResponse.ok(DepartmentDto.from(department))
  }

  @GetMapping("/hospitals/{hospitalId}/specialties")
  @RequiresPermission("hospital-config:read:specialty")
  @Transactional(readOnly = true)
  fun listSpecialties(
    @PathVariable hospitalId: UUID,
    @RequestParam(name = "departmentId", required = false) departmentId: UUID?,
  ): ApiResponse<List<SpecialtyDto>> {
    val specialties =
      if (departmentId != null) {
        specialtyRepository.findByDepartmentId(departmentId)
      } else {
        specialtyRepository.findByHospitalId(hospitalId)
      }
    return ApiResponse.ok(specialties.map { SpecialtyDto.from(it) })
  }

  @PostMapping("/hospitals/{hospitalId}/specialties")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("hospital-config:write:specialty")
  @Transactional
  fun createSpecialty(
    @PathVariable hospitalId: UUID,
    @RequestBody dto: SpecialtyDto,
  ): ApiResponse<SpecialtyDto> {
    requireHospitalExists(hospitalId)
    val specialty =
      specialtyRepository.save(
        Specialty(
          hospitalId = hospitalId,
          name = dto.name ?: throw IllegalArgumentException("SPECIALTY_NAME_REQUIRED"),
          code = dto.code ?: throw IllegalArgumentException("SPECIALTY_CODE_REQUIRED"),
          departmentId = dto.departmentId,
          description = dto.description,
        ),
      )
    return ApiResponse.ok(SpecialtyDto.from(specialty))
  }

  @GetMapping("/hospitals/{hospitalId}/wards")
  @RequiresPermission("hospital-config:read:ward")
  @Transactional(readOnly = true)
  fun listWards(
    @PathVariable hospitalId: UUID,
    @RequestParam(name = "departmentId", required = false) departmentId: UUID?,
  ): ApiResponse<List<WardDto>> {
    val wards =
      if (departmentId != null) {
        wardRepository.findByDepartmentId(departmentId)
      } else {
        wardRepository.findByHospitalId(hospitalId)
      }
    return ApiResponse.ok(wards.map { WardDto.from(it) })
  }

  @PostMapping("/hospitals/{hospitalId}/wards")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("hospital-config:write:ward")
  @Transactional
  fun createWard(
    @PathVariable hospitalId: UUID,
    @RequestBody dto: WardDto,
  ): ApiResponse<WardDto> {
    requireHospitalExists(hospitalId)
    val ward =
      wardRepository.save(
        Ward(
          hospitalId = hospitalId,
          departmentId = dto.departmentId,
          name = dto.name ?: throw IllegalArgumentException("WARD_NAME_REQUIRED"),
          code = dto.code,
          type = dto.type ?: "general",
          roomCount = dto.roomCount ?: 0,
          bedCount = dto.bedCount ?: 0,
        ),
      )
    return ApiResponse.ok(WardDto.from(ward))
  }

  @GetMapping("/hospitals/{hospitalId}/beds")
  @RequiresPermission("hospital-config:read:bed")
  @Transactional(readOnly = true)
  fun listBeds(
    @PathVariable hospitalId: UUID,
    @RequestParam(name = "roomId", required = false) roomId: UUID?,
    @RequestParam(name = "wardId", required = false) wardId: UUID?,
    @RequestParam(name = "status", required = false) status: String?,
  ): ApiResponse<List<BedDto>> {
    val beds =
      when {
        roomId != null -> bedRepository.findByRoomId(roomId)
        wardId != null -> bedRepository.findByWardId(wardId)
        else -> bedRepository.findByHospitalId(hospitalId)
      }
    val filtered = if (status != null) beds.filter { it.status == status } else beds
    return ApiResponse.ok(filtered.map { BedDto.from(it) })
  }

  @PostMapping("/hospitals/{hospitalId}/beds")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("hospital-config:write:bed")
  @Transactional
  fun createBed(
    @PathVariable hospitalId: UUID,
    @RequestBody dto: BedDto,
  ): ApiResponse<BedDto> {
    requireHospitalExists(hospitalId)
    val bed =
      bedRepository.save(
        Bed(
          hospitalId = hospitalId,
          wardId = dto.wardId,
          roomId = dto.roomId,
          label = dto.label ?: throw IllegalArgumentException("BED_LABEL_REQUIRED"),
          type = dto.type ?: "standard",
          status = dto.status ?: "available",
        ),
      )
    return ApiResponse.ok(BedDto.from(bed))
  }

  @PatchMapping("/hospitals/{hospitalId}/beds/{id}/status")
  @RequiresPermission("hospital-config:write:bed")
  @Transactional
  fun updateBedStatus(
    @PathVariable hospitalId: UUID,
    @PathVariable id: UUID,
    @RequestBody dto: BedStatusDto,
  ): ApiResponse<BedDto> {
    val bed = bedRepository.findById(id).orElseThrow { IllegalArgumentException("BED_NOT_FOUND") }
    val status = dto.status ?: throw IllegalArgumentException("BED_STATUS_REQUIRED")
    bed.status = status
    bedRepository.save(bed)
    return ApiResponse.ok(BedDto.from(bed))
  }

  @GetMapping("/hospitals/{hospitalId}/services")
  @RequiresPermission("hospital-config:read:service")
  @Transactional(readOnly = true)
  fun listServices(
    @PathVariable hospitalId: UUID,
    @RequestParam(name = "isActive", required = false) isActive: Boolean?,
  ): ApiResponse<List<HospitalServiceDto>> {
    val services =
      if (isActive == true) {
        hospitalServiceRepository.findByIsActiveTrueAndHospitalId(hospitalId)
      } else {
        hospitalServiceRepository.findByHospitalId(hospitalId)
      }
    val filtered = if (isActive == false) services.filter { !it.isActive } else services
    return ApiResponse.ok(filtered.map { HospitalServiceDto.from(it) })
  }

  @PostMapping("/hospitals/{hospitalId}/services")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("hospital-config:write:service")
  @Transactional
  fun createService(
    @PathVariable hospitalId: UUID,
    @RequestBody dto: HospitalServiceDto,
  ): ApiResponse<HospitalServiceDto> {
    requireHospitalExists(hospitalId)
    val service =
      hospitalServiceRepository.save(
        HospitalService(
          hospitalId = hospitalId,
          name = dto.name ?: throw IllegalArgumentException("SERVICE_NAME_REQUIRED"),
          code = dto.code,
          category = dto.category,
          isActive = dto.isActive ?: true,
          description = dto.description,
        ),
      )
    return ApiResponse.ok(HospitalServiceDto.from(service))
  }

  private fun requireHospitalExists(hospitalId: UUID) {
    hospitalRepository.findById(hospitalId).orElseThrow { IllegalArgumentException("HOSPITAL_NOT_FOUND") }
  }

  private fun requirePlatformAdmin(principal: SquamedPrincipal) {
    if (principal.hospitalId != null) throw AccessDeniedException("TENANT_FORBIDDEN")
  }
}
