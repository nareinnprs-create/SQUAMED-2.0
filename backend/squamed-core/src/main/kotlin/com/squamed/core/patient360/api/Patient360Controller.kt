package com.squamed.core.patient360.api

import com.squamed.core.common.api.ApiResponse
import com.squamed.core.identity.security.RequiresPermission
import com.squamed.core.patient360.service.Patient360Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/patient360")
class Patient360Controller(
  private val patient360Service: Patient360Service,
) {
  @GetMapping("/{patientId}")
  @RequiresPermission("patient:read:patient")
  @Transactional(readOnly = true)
  fun getPatient360(
    @PathVariable patientId: UUID,
  ): ApiResponse<Patient360View> = ApiResponse.ok(patient360Service.getPatient360(patientId))
}
