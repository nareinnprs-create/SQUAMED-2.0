package com.squamed.core.masterdata.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.common.tenant.TenantResolver
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.identity.security.SquamedPrincipal
import com.squamed.core.masterdata.domain.Employee
import com.squamed.core.masterdata.domain.Equipment
import com.squamed.core.masterdata.domain.PriceList
import com.squamed.core.masterdata.domain.PriceListItem
import com.squamed.core.masterdata.domain.Vendor
import com.squamed.core.masterdata.repository.ChargeItemRepository
import com.squamed.core.masterdata.repository.EmployeeRepository
import com.squamed.core.masterdata.repository.EquipmentRepository
import com.squamed.core.masterdata.repository.PriceListItemRepository
import com.squamed.core.masterdata.repository.PriceListRepository
import com.squamed.core.masterdata.repository.VendorRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
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

/**
 * Hospital-scoped master data registries (00.06). Tenant comes from the principal
 * (TenantResolver); hospital users are pinned server-side, platform admins may choose
 * explicitly or default to the primary hospital.
 */
@RestController
@RequestMapping("/api/v1/master-data")
class MasterDataRegistryController(
  private val vendorRepository: VendorRepository,
  private val equipmentRepository: EquipmentRepository,
  private val employeeRepository: EmployeeRepository,
  private val priceListRepository: PriceListRepository,
  private val priceListItemRepository: PriceListItemRepository,
  private val chargeItemRepository: ChargeItemRepository,
  private val tenantResolver: TenantResolver,
) {
  // ---- vendors ----

  @GetMapping("/vendors")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listVendors(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<VendorDto>> {
    val pinned = tenantResolver.pinOrDefault(principal, hospitalId)
    val pageable = buildPageable(page, size)
    val content =
      when {
        search != null && active != null -> {
          vendorRepository.search(pinned, "%$search%", "%$search%", active, pageable).content
        }

        search != null -> {
          vendorRepository.search(pinned, "%$search%", "%$search%", pageable).content
        }

        active != null -> {
          vendorRepository.findByHospitalIdAndIsActive(pinned, active, pageable).content
        }

        else -> {
          vendorRepository.findByHospitalId(pinned, pageable).content
        }
      }
    return ApiResponse.ok(content.map { VendorDto.from(it) })
  }

  @PostMapping("/vendors")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createVendor(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestBody dto: VendorDto,
  ): ApiResponse<VendorDto> {
    val pinned = tenantResolver.pinOrDefault(principal, dto.hospitalId)
    requireUnique { vendorRepository.findByHospitalIdAndCode(pinned, requireField(dto.code, "VENDOR_CODE_REQUIRED")) }
    val vendor =
      vendorRepository.save(
        Vendor(
          hospitalId = pinned,
          code = requireField(dto.code, "VENDOR_CODE_REQUIRED"),
          name = requireField(dto.name, "VENDOR_NAME_REQUIRED"),
          contactPerson = dto.contactPerson,
          phone = dto.phone,
          email = dto.email,
          address = dto.address,
          taxId = dto.taxId,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(VendorDto.from(vendor))
  }

  @PutMapping("/vendors/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateVendor(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: VendorDto,
  ): ApiResponse<VendorDto> {
    val vendor = vendorRepository.findById(id).orElseThrow { IllegalArgumentException("VENDOR_NOT_FOUND") }
    requireSameHospital(principal, vendor.hospitalId)
    vendor.code = dto.code ?: vendor.code
    vendor.name = dto.name ?: vendor.name
    vendor.contactPerson = dto.contactPerson ?: vendor.contactPerson
    vendor.phone = dto.phone ?: vendor.phone
    vendor.email = dto.email ?: vendor.email
    vendor.address = dto.address ?: vendor.address
    vendor.taxId = dto.taxId ?: vendor.taxId
    vendor.isActive = dto.isActive ?: vendor.isActive
    vendorRepository.save(vendor)
    return ApiResponse.ok(VendorDto.from(vendor))
  }

  @PatchMapping("/vendors/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setVendorActive(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<VendorDto> {
    val vendor = vendorRepository.findById(id).orElseThrow { IllegalArgumentException("VENDOR_NOT_FOUND") }
    requireSameHospital(principal, vendor.hospitalId)
    vendor.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    vendorRepository.save(vendor)
    return ApiResponse.ok(VendorDto.from(vendor))
  }

  // ---- equipment ----

  @GetMapping("/equipment")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listEquipment(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<EquipmentDto>> {
    val pinned = tenantResolver.pinOrDefault(principal, hospitalId)
    val pageable = buildPageable(page, size)
    val content =
      when {
        search != null && active != null -> {
          equipmentRepository.search(pinned, "%$search%", "%$search%", active, pageable).content
        }

        search != null -> {
          equipmentRepository.search(pinned, "%$search%", "%$search%", pageable).content
        }

        active != null -> {
          equipmentRepository.findByHospitalIdAndIsActive(pinned, active, pageable).content
        }

        else -> {
          equipmentRepository.findByHospitalId(pinned, pageable).content
        }
      }
    return ApiResponse.ok(content.map { EquipmentDto.from(it) })
  }

  @PostMapping("/equipment")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createEquipment(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestBody dto: EquipmentDto,
  ): ApiResponse<EquipmentDto> {
    val pinned = tenantResolver.pinOrDefault(principal, dto.hospitalId)
    requireUnique { equipmentRepository.findByHospitalIdAndCode(pinned, requireField(dto.code, "EQUIPMENT_CODE_REQUIRED")) }
    val equipment =
      equipmentRepository.save(
        Equipment(
          hospitalId = pinned,
          code = requireField(dto.code, "EQUIPMENT_CODE_REQUIRED"),
          name = requireField(dto.name, "EQUIPMENT_NAME_REQUIRED"),
          category = dto.category,
          vendorId = dto.vendorId,
          serialNumber = dto.serialNumber,
          location = dto.location,
          purchaseDate = dto.purchaseDate,
          warrantyUntil = dto.warrantyUntil,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(EquipmentDto.from(equipment))
  }

  @PutMapping("/equipment/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateEquipment(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: EquipmentDto,
  ): ApiResponse<EquipmentDto> {
    val equipment = equipmentRepository.findById(id).orElseThrow { IllegalArgumentException("EQUIPMENT_NOT_FOUND") }
    requireSameHospital(principal, equipment.hospitalId)
    equipment.code = dto.code ?: equipment.code
    equipment.name = dto.name ?: equipment.name
    equipment.category = dto.category ?: equipment.category
    equipment.vendorId = dto.vendorId ?: equipment.vendorId
    equipment.serialNumber = dto.serialNumber ?: equipment.serialNumber
    equipment.location = dto.location ?: equipment.location
    equipment.purchaseDate = dto.purchaseDate ?: equipment.purchaseDate
    equipment.warrantyUntil = dto.warrantyUntil ?: equipment.warrantyUntil
    equipment.isActive = dto.isActive ?: equipment.isActive
    equipmentRepository.save(equipment)
    return ApiResponse.ok(EquipmentDto.from(equipment))
  }

  @PatchMapping("/equipment/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setEquipmentActive(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<EquipmentDto> {
    val equipment = equipmentRepository.findById(id).orElseThrow { IllegalArgumentException("EQUIPMENT_NOT_FOUND") }
    requireSameHospital(principal, equipment.hospitalId)
    equipment.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    equipmentRepository.save(equipment)
    return ApiResponse.ok(EquipmentDto.from(equipment))
  }

  // ---- employees ----

  @GetMapping("/employees")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listEmployees(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<EmployeeDto>> {
    val pinned = tenantResolver.pinOrDefault(principal, hospitalId)
    val pageable = buildPageable(page, size)
    val content =
      when {
        search != null && active != null -> {
          employeeRepository.search(pinned, "%$search%", "%$search%", active, pageable).content
        }

        search != null -> {
          employeeRepository.search(pinned, "%$search%", "%$search%", pageable).content
        }

        active != null -> {
          employeeRepository.findByHospitalIdAndIsActive(pinned, active, pageable).content
        }

        else -> {
          employeeRepository.findByHospitalId(pinned, pageable).content
        }
      }
    return ApiResponse.ok(content.map { EmployeeDto.from(it) })
  }

  @PostMapping("/employees")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createEmployee(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestBody dto: EmployeeDto,
  ): ApiResponse<EmployeeDto> {
    val pinned = tenantResolver.pinOrDefault(principal, dto.hospitalId)
    requireUnique { employeeRepository.findByHospitalIdAndCode(pinned, requireField(dto.code, "EMPLOYEE_CODE_REQUIRED")) }
    val employee =
      employeeRepository.save(
        Employee(
          hospitalId = pinned,
          code = requireField(dto.code, "EMPLOYEE_CODE_REQUIRED"),
          firstName = requireField(dto.firstName, "EMPLOYEE_FIRST_NAME_REQUIRED"),
          lastName = requireField(dto.lastName, "EMPLOYEE_LAST_NAME_REQUIRED"),
          gender = dto.gender,
          dateOfBirth = dto.dateOfBirth,
          phone = dto.phone,
          email = dto.email,
          designation = dto.designation,
          department = dto.department,
          qualification = dto.qualification,
          licenseNumber = dto.licenseNumber,
          licenseExpiry = dto.licenseExpiry,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(EmployeeDto.from(employee))
  }

  @PutMapping("/employees/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updateEmployee(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: EmployeeDto,
  ): ApiResponse<EmployeeDto> {
    val employee = employeeRepository.findById(id).orElseThrow { IllegalArgumentException("EMPLOYEE_NOT_FOUND") }
    requireSameHospital(principal, employee.hospitalId)
    employee.code = dto.code ?: employee.code
    employee.firstName = dto.firstName ?: employee.firstName
    employee.lastName = dto.lastName ?: employee.lastName
    employee.gender = dto.gender ?: employee.gender
    employee.dateOfBirth = dto.dateOfBirth ?: employee.dateOfBirth
    employee.phone = dto.phone ?: employee.phone
    employee.email = dto.email ?: employee.email
    employee.designation = dto.designation ?: employee.designation
    employee.department = dto.department ?: employee.department
    employee.qualification = dto.qualification ?: employee.qualification
    employee.licenseNumber = dto.licenseNumber ?: employee.licenseNumber
    employee.licenseExpiry = dto.licenseExpiry ?: employee.licenseExpiry
    employee.isActive = dto.isActive ?: employee.isActive
    employeeRepository.save(employee)
    return ApiResponse.ok(EmployeeDto.from(employee))
  }

  @PatchMapping("/employees/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setEmployeeActive(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<EmployeeDto> {
    val employee = employeeRepository.findById(id).orElseThrow { IllegalArgumentException("EMPLOYEE_NOT_FOUND") }
    requireSameHospital(principal, employee.hospitalId)
    employee.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    employeeRepository.save(employee)
    return ApiResponse.ok(EmployeeDto.from(employee))
  }

  // ---- price lists ----

  @GetMapping("/price-lists")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listPriceLists(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestParam(name = "hospitalId", required = false) hospitalId: UUID?,
    @RequestParam(name = "search", required = false) search: String?,
    @RequestParam(name = "active", required = false) active: Boolean?,
    @RequestParam(name = "page", defaultValue = "0") page: Int,
    @RequestParam(name = "size", defaultValue = "20") size: Int,
  ): ApiResponse<List<PriceListDto>> {
    val pinned = tenantResolver.pinOrDefault(principal, hospitalId)
    val pageable = buildPageable(page, size)
    val content =
      when {
        search != null && active != null -> {
          priceListRepository.search(pinned, "%$search%", "%$search%", active, pageable).content
        }

        search != null -> {
          priceListRepository.search(pinned, "%$search%", "%$search%", pageable).content
        }

        active != null -> {
          priceListRepository.findByHospitalIdAndIsActive(pinned, active, pageable).content
        }

        else -> {
          priceListRepository.findByHospitalId(pinned, pageable).content
        }
      }
    return ApiResponse.ok(content.map { PriceListDto.from(it) })
  }

  @PostMapping("/price-lists")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun createPriceList(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @RequestBody dto: PriceListDto,
  ): ApiResponse<PriceListDto> {
    val pinned = tenantResolver.pinOrDefault(principal, dto.hospitalId)
    requireUnique { priceListRepository.findByHospitalIdAndCode(pinned, requireField(dto.code, "PRICE_LIST_CODE_REQUIRED")) }
    val priceList =
      priceListRepository.save(
        PriceList(
          hospitalId = pinned,
          code = requireField(dto.code, "PRICE_LIST_CODE_REQUIRED"),
          name = requireField(dto.name, "PRICE_LIST_NAME_REQUIRED"),
          currency = dto.currency ?: "QAR",
          validFrom = dto.validFrom,
          validTo = dto.validTo,
          isActive = dto.isActive ?: true,
        ),
      )
    return ApiResponse.ok(PriceListDto.from(priceList))
  }

  @PutMapping("/price-lists/{id}")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun updatePriceList(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: PriceListDto,
  ): ApiResponse<PriceListDto> {
    val priceList = priceListRepository.findById(id).orElseThrow { IllegalArgumentException("PRICE_LIST_NOT_FOUND") }
    requireSameHospital(principal, priceList.hospitalId)
    priceList.code = dto.code ?: priceList.code
    priceList.name = dto.name ?: priceList.name
    priceList.currency = dto.currency ?: priceList.currency
    priceList.validFrom = dto.validFrom ?: priceList.validFrom
    priceList.validTo = dto.validTo ?: priceList.validTo
    priceList.isActive = dto.isActive ?: priceList.isActive
    priceListRepository.save(priceList)
    return ApiResponse.ok(PriceListDto.from(priceList))
  }

  @PatchMapping("/price-lists/{id}/active")
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun setPriceListActive(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: CatalogActiveDto,
  ): ApiResponse<PriceListDto> {
    val priceList = priceListRepository.findById(id).orElseThrow { IllegalArgumentException("PRICE_LIST_NOT_FOUND") }
    requireSameHospital(principal, priceList.hospitalId)
    priceList.isActive = dto.isActive ?: throw IllegalArgumentException("IS_ACTIVE_REQUIRED")
    priceListRepository.save(priceList)
    return ApiResponse.ok(PriceListDto.from(priceList))
  }

  @GetMapping("/price-lists/{id}/items")
  @RequiresPermission("master-data:read:catalog")
  @Transactional(readOnly = true)
  fun listPriceListItems(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
  ): ApiResponse<List<PriceListItemDto>> {
    val priceList = requireOwnPriceList(principal, id)
    return ApiResponse.ok(
      priceListItemRepository.findByPriceListId(priceList.id!!).map { PriceListItemDto.from(it) },
    )
  }

  /** Upsert: re-posting an existing (list, charge item) pair re-prices it instead of failing. */
  @PostMapping("/price-lists/{id}/items")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("master-data:write:catalog")
  @Transactional
  fun addPriceListItem(
    @AuthenticationPrincipal principal: SquamedPrincipal,
    @PathVariable id: UUID,
    @RequestBody dto: PriceListItemDto,
  ): ApiResponse<PriceListItemDto> {
    val priceList = requireOwnPriceList(principal, id)
    val chargeItemId = dto.chargeItemId ?: throw IllegalArgumentException("CHARGE_ITEM_ID_REQUIRED")
    chargeItemRepository.findById(chargeItemId).orElseThrow { IllegalArgumentException("CHARGE_ITEM_NOT_FOUND") }
    val price = dto.priceQar ?: BigDecimal.ZERO
    val item =
      priceListItemRepository.findByPriceListIdAndChargeItemId(id, chargeItemId)?.apply { priceQar = price }
        ?: priceListItemRepository.save(
          PriceListItem(
            hospitalId = priceList.hospitalId,
            priceListId = id,
            chargeItemId = chargeItemId,
            priceQar = price,
            isActive = true,
          ),
        )
    return ApiResponse.ok(PriceListItemDto.from(item))
  }

  private fun requireOwnPriceList(
    principal: SquamedPrincipal,
    id: UUID,
  ): PriceList {
    val priceList = priceListRepository.findById(id).orElseThrow { IllegalArgumentException("PRICE_LIST_NOT_FOUND") }
    requireSameHospital(principal, priceList.hospitalId)
    return priceList
  }

  /** Hospital-bound users may only touch rows of their own hospital; admins pass. */
  private fun requireSameHospital(
    principal: SquamedPrincipal,
    entityHospitalId: UUID,
  ) {
    val pinned = tenantResolver.pinOrDefault(principal, entityHospitalId)
    if (pinned != entityHospitalId) throw AccessDeniedException("TENANT_FORBIDDEN")
  }

  private fun requireField(
    value: String?,
    error: String,
  ): String = value?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException(error)

  private fun requireUnique(existing: () -> Any?) {
    if (existing() != null) throw IllegalArgumentException("CODE_DUPLICATE")
  }

  private fun buildPageable(
    page: Int,
    size: Int,
  ): PageRequest = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 200), Sort.by(Sort.Direction.ASC, "id"))
}
