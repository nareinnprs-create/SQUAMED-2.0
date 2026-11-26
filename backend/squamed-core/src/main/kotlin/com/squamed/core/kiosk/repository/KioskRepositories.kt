package com.squamed.core.kiosk.repository

import com.squamed.core.kiosk.domain.KioskCampus
import com.squamed.core.kiosk.domain.KioskMapEdge
import com.squamed.core.kiosk.domain.KioskMapNode
import com.squamed.core.kiosk.domain.KioskPaymentReceipt
import com.squamed.core.kiosk.domain.KioskReportCollection
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface KioskPaymentReceiptRepository : JpaRepository<KioskPaymentReceipt, UUID> {
  fun findTopByOrderByReceiptNumberDesc(): KioskPaymentReceipt?

  fun findByHospitalIdOrderByCreatedAtDesc(hospitalId: UUID): List<KioskPaymentReceipt>
}

interface KioskReportCollectionRepository : JpaRepository<KioskReportCollection, UUID> {
  fun findByHospitalIdAndReportTypeAndReportId(
    hospitalId: UUID,
    reportType: String,
    reportId: UUID,
  ): KioskReportCollection?

  fun findTopByOrderByReceiptNumberDesc(): KioskReportCollection?
}

interface KioskCampusRepository : JpaRepository<KioskCampus, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): KioskCampus?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<KioskCampus>

  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): KioskCampus?
}

interface KioskMapNodeRepository : JpaRepository<KioskMapNode, UUID> {
  fun findByCampusIdOrderByCodeAsc(campusId: UUID): List<KioskMapNode>

  fun findByCampusIdAndCode(
    campusId: UUID,
    code: String,
  ): KioskMapNode?
}

interface KioskMapEdgeRepository : JpaRepository<KioskMapEdge, UUID> {
  fun findByCampusIdOrderByIdAsc(campusId: UUID): List<KioskMapEdge>
}
