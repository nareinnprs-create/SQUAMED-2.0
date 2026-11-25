package com.squamed.core.finance.repository

import com.squamed.core.finance.domain.ApInvoice
import com.squamed.core.finance.domain.ApPayment
import com.squamed.core.finance.domain.BankAccount
import com.squamed.core.finance.domain.BankStatementLine
import com.squamed.core.finance.domain.Budget
import com.squamed.core.finance.domain.CostCentre
import com.squamed.core.finance.domain.DepreciationRun
import com.squamed.core.finance.domain.FixedAsset
import com.squamed.core.finance.domain.GlAccount
import com.squamed.core.finance.domain.GstReturn
import com.squamed.core.finance.domain.JournalEntry
import com.squamed.core.finance.domain.JournalLine
import com.squamed.core.finance.domain.PeriodClose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface GlAccountRepository : JpaRepository<GlAccount, UUID> {
  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<GlAccount>

  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): GlAccount?
}

interface CostCentreRepository : JpaRepository<CostCentre, UUID> {
  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<CostCentre>

  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): CostCentre?
}

interface JournalEntryRepository : JpaRepository<JournalEntry, UUID> {
  fun findByHospitalIdAndSourceTypeAndSourceId(
    hospitalId: UUID,
    sourceType: String,
    sourceId: UUID,
  ): JournalEntry?

  fun findTopByOrderByEntryNoDesc(): JournalEntry?

  fun findByHospitalIdAndEntryDateBetween(
    hospitalId: UUID,
    from: LocalDate,
    to: LocalDate,
  ): List<JournalEntry>
}

interface JournalLineRepository : JpaRepository<JournalLine, UUID> {
  fun findByEntryIdOrderByIdAsc(entryId: UUID): List<JournalLine>

  @Query(
    "select l from JournalLine l, JournalEntry e where e.id = l.entryId " +
      "and e.hospitalId = :hid and e.entryDate between :from and :to",
  )
  fun findForPeriod(
    @Param("hid") hospitalId: UUID,
    @Param("from") from: LocalDate,
    @Param("to") to: LocalDate,
  ): List<JournalLine>
}

interface ApInvoiceRepository : JpaRepository<ApInvoice, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): ApInvoice?

  fun findByHospitalIdOrderByInvoiceDateDesc(hospitalId: UUID): List<ApInvoice>

  fun findByHospitalIdAndNumber(
    hospitalId: UUID,
    number: String,
  ): ApInvoice?
}

interface ApPaymentRepository : JpaRepository<ApPayment, UUID> {
  fun findByApInvoiceIdOrderByPaidOnAsc(apInvoiceId: UUID): List<ApPayment>

  fun findByHospitalIdAndPaidOnBetween(
    hospitalId: UUID,
    from: LocalDate,
    to: LocalDate,
  ): List<ApPayment>
}

interface BankAccountRepository : JpaRepository<BankAccount, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): BankAccount?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<BankAccount>

  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): BankAccount?
}

interface BankStatementLineRepository : JpaRepository<BankStatementLine, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): BankStatementLine?

  fun findByHospitalIdAndStatusOrderByStatementDateAsc(
    hospitalId: UUID,
    status: String,
  ): List<BankStatementLine>

  fun findByHospitalIdAndBankAccountIdOrderByStatementDateAsc(
    hospitalId: UUID,
    bankAccountId: UUID,
  ): List<BankStatementLine>
}

interface FixedAssetRepository : JpaRepository<FixedAsset, UUID> {
  fun findByIdAndHospitalId(
    id: UUID,
    hospitalId: UUID,
  ): FixedAsset?

  fun findByHospitalIdOrderByCodeAsc(hospitalId: UUID): List<FixedAsset>

  fun findByHospitalIdAndCode(
    hospitalId: UUID,
    code: String,
  ): FixedAsset?
}

interface DepreciationRunRepository : JpaRepository<DepreciationRun, UUID> {
  fun findByHospitalIdAndPeriod(
    hospitalId: UUID,
    period: String,
  ): DepreciationRun?

  fun findByHospitalIdOrderByPeriodDesc(hospitalId: UUID): List<DepreciationRun>
}

interface BudgetRepository : JpaRepository<Budget, UUID> {
  fun findByHospitalIdAndPeriodOrderByAccountCodeAsc(
    hospitalId: UUID,
    period: String,
  ): List<Budget>

  fun findByHospitalIdAndAccountCodeAndPeriodAndCostCentre(
    hospitalId: UUID,
    accountCode: String,
    period: String,
    costCentre: String,
  ): Budget?
}

interface PeriodCloseRepository : JpaRepository<PeriodClose, UUID> {
  fun findByHospitalIdAndPeriod(
    hospitalId: UUID,
    period: String,
  ): PeriodClose?

  fun findByHospitalIdOrderByPeriodDesc(hospitalId: UUID): List<PeriodClose>
}

interface GstReturnRepository : JpaRepository<GstReturn, UUID> {
  fun findByHospitalIdAndPeriod(
    hospitalId: UUID,
    period: String,
  ): GstReturn?

  fun findByHospitalIdOrderByPeriodDesc(hospitalId: UUID): List<GstReturn>
}
