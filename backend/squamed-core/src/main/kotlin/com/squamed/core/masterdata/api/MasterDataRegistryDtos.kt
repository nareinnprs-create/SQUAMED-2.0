package com.squamed.core.masterdata.api

import com.squamed.core.masterdata.domain.Employee
import com.squamed.core.masterdata.domain.Equipment
import com.squamed.core.masterdata.domain.PriceList
import com.squamed.core.masterdata.domain.PriceListItem
import com.squamed.core.masterdata.domain.Vendor
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class VendorDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val code: String?,
  val name: String?,
  val contactPerson: String?,
  val phone: String?,
  val email: String?,
  val address: String?,
  val taxId: String?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: Vendor) =
      VendorDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        code = entity.code,
        name = entity.name,
        contactPerson = entity.contactPerson,
        phone = entity.phone,
        email = entity.email,
        address = entity.address,
        taxId = entity.taxId,
        isActive = entity.isActive,
      )
  }
}

data class EquipmentDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val code: String?,
  val name: String?,
  val category: String?,
  val vendorId: UUID?,
  val serialNumber: String?,
  val location: String?,
  val purchaseDate: LocalDate?,
  val warrantyUntil: LocalDate?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: Equipment) =
      EquipmentDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        code = entity.code,
        name = entity.name,
        category = entity.category,
        vendorId = entity.vendorId,
        serialNumber = entity.serialNumber,
        location = entity.location,
        purchaseDate = entity.purchaseDate,
        warrantyUntil = entity.warrantyUntil,
        isActive = entity.isActive,
      )
  }
}

data class EmployeeDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val code: String?,
  val firstName: String?,
  val lastName: String?,
  val gender: String?,
  val dateOfBirth: LocalDate?,
  val phone: String?,
  val email: String?,
  val designation: String?,
  val department: String?,
  val qualification: String?,
  val licenseNumber: String?,
  val licenseExpiry: LocalDate?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: Employee) =
      EmployeeDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        code = entity.code,
        firstName = entity.firstName,
        lastName = entity.lastName,
        gender = entity.gender,
        dateOfBirth = entity.dateOfBirth,
        phone = entity.phone,
        email = entity.email,
        designation = entity.designation,
        department = entity.department,
        qualification = entity.qualification,
        licenseNumber = entity.licenseNumber,
        licenseExpiry = entity.licenseExpiry,
        isActive = entity.isActive,
      )
  }
}

data class PriceListDto(
  val id: UUID?,
  val hospitalId: UUID?,
  val code: String?,
  val name: String?,
  val currency: String?,
  val validFrom: LocalDate?,
  val validTo: LocalDate?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: PriceList) =
      PriceListDto(
        id = entity.id,
        hospitalId = entity.hospitalId,
        code = entity.code,
        name = entity.name,
        currency = entity.currency,
        validFrom = entity.validFrom,
        validTo = entity.validTo,
        isActive = entity.isActive,
      )
  }
}

data class PriceListItemDto(
  val id: UUID?,
  val priceListId: UUID?,
  val chargeItemId: UUID?,
  val priceQar: BigDecimal?,
  val isActive: Boolean?,
) {
  companion object {
    fun from(entity: PriceListItem) =
      PriceListItemDto(
        id = entity.id,
        priceListId = entity.priceListId,
        chargeItemId = entity.chargeItemId,
        priceQar = entity.priceQar,
        isActive = entity.isActive,
      )
  }
}
