package com.squamed.core.finance.domain

import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "gl_account", schema = "finance")
class GlAccount(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 20) var code: String,
  @Column(name = "name", nullable = false, length = 160) var name: String,
  @Column(name = "type", nullable = false, length = 20) var type: String,
  @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "cost_centre", schema = "finance")
class CostCentre(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "name", nullable = false, length = 160) var name: String,
  @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
) : BaseEntity()

@Entity
@Table(name = "journal_entry", schema = "finance")
class JournalEntry(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "entry_no", nullable = false, length = 40) var entryNo: String,
  @Column(name = "entry_date", nullable = false) var entryDate: LocalDate,
  @Column(name = "source_type", nullable = false, length = 30) var sourceType: String,
  @Column(name = "source_id", nullable = false) var sourceId: UUID,
  @Column(name = "description", nullable = false, length = 400) var description: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "POSTED",
  @Column(name = "posted_at", nullable = false) var postedAt: Instant = Instant.now(),
  @Column(name = "posted_by") var postedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "journal_line", schema = "finance")
class JournalLine(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "entry_id", nullable = false) var entryId: UUID,
  @Column(name = "account_code", nullable = false, length = 20) var accountCode: String,
  @Column(name = "debit_qar", nullable = false) var debitQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "credit_qar", nullable = false) var creditQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "cost_centre", length = 40) var costCentre: String? = null,
  @Column(name = "description", length = 300) var description: String? = null,
) : BaseEntity()

@Entity
@Table(name = "ap_invoice", schema = "finance")
class ApInvoice(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "vendor_name", nullable = false, length = 200) var vendorName: String,
  @Column(name = "number", nullable = false, length = 60) var number: String,
  @Column(name = "invoice_date", nullable = false) var invoiceDate: LocalDate,
  @Column(name = "due_date") var dueDate: LocalDate? = null,
  @Column(name = "amount_qar", nullable = false) var amountQar: BigDecimal,
  @Column(name = "paid_qar", nullable = false) var paidQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "status", nullable = false, length = 20) var status: String = "OPEN",
  @Column(name = "cost_centre", length = 40) var costCentre: String? = null,
) : BaseEntity()

@Entity
@Table(name = "ap_payment", schema = "finance")
class ApPayment(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "ap_invoice_id", nullable = false) var apInvoiceId: UUID,
  @Column(name = "amount_qar", nullable = false) var amountQar: BigDecimal,
  @Column(name = "paid_on", nullable = false) var paidOn: LocalDate,
  @Column(name = "method", nullable = false, length = 30) var method: String,
) : BaseEntity()

@Entity
@Table(name = "bank_account", schema = "finance")
class BankAccount(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "name", nullable = false, length = 160) var name: String,
  @Column(name = "opening_balance", nullable = false) var openingBalance: BigDecimal = BigDecimal.ZERO,
) : BaseEntity()

@Entity
@Table(name = "bank_statement_line", schema = "finance")
class BankStatementLine(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "bank_account_id", nullable = false) var bankAccountId: UUID,
  @Column(name = "statement_date", nullable = false) var statementDate: LocalDate,
  @Column(name = "amount_qar", nullable = false) var amountQar: BigDecimal,
  @Column(name = "reference", length = 120) var reference: String? = null,
  @Column(name = "matched_entry_id") var matchedEntryId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20) var status: String = "UNMATCHED",
) : BaseEntity()

@Entity
@Table(name = "fixed_asset", schema = "finance")
class FixedAsset(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "code", nullable = false, length = 40) var code: String,
  @Column(name = "name", nullable = false, length = 160) var name: String,
  @Column(name = "category", nullable = false, length = 60) var category: String = "equipment",
  @Column(name = "cost_qar", nullable = false) var costQar: BigDecimal,
  @Column(name = "useful_life_months", nullable = false) var usefulLifeMonths: Int,
  @Column(name = "acquired_on", nullable = false) var acquiredOn: LocalDate,
  @Column(name = "accumulated_depreciation", nullable = false) var accumulatedDepreciation: BigDecimal = BigDecimal.ZERO,
  @Column(name = "status", nullable = false, length = 20) var status: String = "ACTIVE",
) : BaseEntity()

@Entity
@Table(name = "depreciation_run", schema = "finance")
class DepreciationRun(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "period", nullable = false, length = 7) var period: String,
  @Column(name = "total_qar", nullable = false) var totalQar: BigDecimal,
  @Column(name = "posted_at", nullable = false) var postedAt: Instant = Instant.now(),
  @Column(name = "posted_by") var postedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "budget", schema = "finance")
class Budget(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "account_code", nullable = false, length = 20) var accountCode: String,
  @Column(name = "period", nullable = false, length = 7) var period: String,
  @Column(name = "amount_qar", nullable = false) var amountQar: BigDecimal,
  @Column(name = "cost_centre", nullable = false, length = 40) var costCentre: String = "",
) : BaseEntity()

@Entity
@Table(name = "period_close", schema = "finance")
class PeriodClose(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "period", nullable = false, length = 7) var period: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "CLOSED",
  @Column(name = "operational_total", nullable = false) var operationalTotal: BigDecimal,
  @Column(name = "gl_total", nullable = false) var glTotal: BigDecimal,
  @Column(name = "variance_qar", nullable = false) var varianceQar: BigDecimal,
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "trial_balance", columnDefinition = "jsonb")
  var trialBalance: com.fasterxml.jackson.databind.JsonNode? = null,
  @Column(name = "closed_at", nullable = false) var closedAt: Instant = Instant.now(),
  @Column(name = "closed_by") var closedBy: UUID? = null,
) : BaseEntity()

@Entity
@Table(name = "gst_return", schema = "finance")
class GstReturn(
  @Column(name = "hospital_id", nullable = false) var hospitalId: UUID,
  @Column(name = "period", nullable = false, length = 7) var period: String,
  @Column(name = "status", nullable = false, length = 20) var status: String = "DRAFT",
  @Column(name = "taxable_value", nullable = false) var taxableValue: BigDecimal = BigDecimal.ZERO,
  @Column(name = "cgst_qar", nullable = false) var cgstQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "sgst_qar", nullable = false) var sgstQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "igst_qar", nullable = false) var igstQar: BigDecimal = BigDecimal.ZERO,
  @Column(name = "total_tax_qar", nullable = false) var totalTaxQar: BigDecimal = BigDecimal.ZERO,
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  var payload: com.fasterxml.jackson.databind.JsonNode? = null,
  @Column(name = "generated_at", nullable = false) var generatedAt: Instant = Instant.now(),
  @Column(name = "filed_at") var filedAt: Instant? = null,
) : BaseEntity()
