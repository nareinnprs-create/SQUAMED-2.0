# CHUNK 3 EXIT DEMO (section 03.07): walks the chunk-3 contract EXIT CRITERIA
# scenario end to end - lab + pharmacy activity from chunk 2 auto-captures charges into the
# ledger, the bill reprices for a TPA payer class and splits exempt vs GST lines, a deposit
# and a counter payment settle it, the cash drawer balances, a bank statement reconciles,
# the integrity engine flags an unbilled charge and a supervisor resolves it with an audit
# note, and the daily GL journal exports balanced. Insurance pre-auth/claim legs are owed
# to 03.04 (pilot payer list D6 + gateway E4). Runs against the app on port 8080 (pwsh). ASCII only.
param([string]$Base = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0

function Check($name, $ok) {
  if ($ok) { $script:pass++; Write-Output "PASS $name" }
  else { $script:fail++; Write-Output "FAIL $name" }
}

function Api($method, $path, $body, $token) {
  $headers = @{}
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  try {
    if ($null -ne $body) {
      return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers
  } catch {
    $status = $_.Exception.Response.StatusCode.value__
    $respBody = ''
    try { $respBody = $_.ErrorDetails.Message } catch { }
    return @{ _status = $status; _body = $respBody }
  }
}

Write-Output "== 03.07 chunk-3 exit demo (8080) =="

# 1 admin + a fresh hospital + the revenue staff
$admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
Check 'admin login' ($null -ne $admin)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Chunk3 Exit $s"; code = "C3-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
function Staff($role, $tag) {
  $email = "$tag-c3-$s@test.local"
  Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = $tag; lastName = "Exit$s"; roles = @($role); hospitalId = $h } $null | Out-Null
  return (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
}
$doc = Staff 'DOCTOR' 'doc'
$staff = Staff 'BILLING_STAFF' 'bill'
$sup = Staff 'BILLING_SUPERVISOR' 'sup'
$coder = Staff 'MEDICAL_CODER' 'coder'
$labtech = Staff 'LAB_TECHNICIAN' 'lab'
$patho = Staff 'PATHOLOGIST' 'path'
Check 'doctor + billing staff + supervisor + coder + lab tech + pathologist log in' ($null -ne $doc -and $null -ne $staff -and $null -ne $sup -and $null -ne $coder -and $null -ne $labtech -and $null -ne $patho)

# 2 patient + encounter + tariff + TPA rate card + tax profile
$stamp = (Get-Date).Ticks
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Revenue'; lastName = "Exit$s"; gender = 'female'; dateOfBirth = '1983-03-03'; phone = ('60' + ($stamp % 10000000000).ToString('D10')); hospitalId = $h } $admin).data.patient
$paId = $pa.id; $mrn = $pa.mrn
Api POST "/api/v1/patients/$paId/allergies" @{ allergen = 'NKA'; severity = 'none' } $admin | Out-Null
$enc = (Api POST '/api/v1/encounters' @{ patientId = $paId; chiefComplaint = 'revenue day'; reason = 'chunk-3 exit' } $doc).data.id
Check 'patient + encounter ready' ($null -ne $enc)
$testCode = "C3LAB-$s"
Api POST '/api/v1/billing/tariffs' @{ hospitalId = $h; code = $testCode; name = "Exit panel $s"; category = 'lab'; priceQar = 80.0 } $admin | Out-Null
Api POST '/api/v1/billing/rate-cards' @{ hospitalId = $h; payerClass = 'tpa'; code = $testCode; priceQar = 96.0 } $staff | Out-Null
$profile = Api POST '/api/v1/billing/tax-profile' @{ hospitalId = $h; gstin = '27AABCS1429B1Z1'; stateCode = '27'; eInvoiceThresholdQar = 5000.0 } $staff
Check 'tax profile with GSTIN + e-invoice threshold' ($profile.data.gstin -eq '27AABCS1429B1Z1')

# 3 lab order -> release -> charge auto-captured from the event
$order = (Api POST '/api/v1/lab/orders' @{ patientId = $paId; encounterId = $enc; tests = @(@{ testCode = $testCode; testName = "Exit panel $s" }) } $doc).data.order
$reqId = (Api GET "/api/v1/lab/orders/$($order.id)" $null $doc).data.tests[0].id
Api POST "/api/v1/lab/orders/$($order.id)/collect" @{} $labtech | Out-Null
Api POST "/api/v1/lab/orders/$($order.id)/receive" @{} $doc | Out-Null
Api POST "/api/v1/lab/orders/$($order.id)/start" @{} $doc | Out-Null
Api POST "/api/v1/lab/orders/$($order.id)/results" @{ results = @(@{ testRequestId = $reqId; resultValue = '4.1' }) } $doc | Out-Null
Api POST "/api/v1/lab/orders/$($order.id)/verify" @{} $patho | Out-Null
Api POST "/api/v1/lab/orders/$($order.id)/release" @{} $patho | Out-Null
$deadline = (Get-Date).AddSeconds(45); $labCharge = $null
while ((Get-Date) -lt $deadline -and $null -eq $labCharge) {
  Start-Sleep -Milliseconds 700
  $labCharge = (Api GET "/api/v1/billing/charges?patientId=$paId" $null $staff).data | Where-Object { $_.referenceType -eq 'lab-order' -and $_.referenceId -eq $order.id } | Select-Object -First 1
}
Check 'lab release auto-captured a ledger charge' ($null -ne $labCharge -and $labCharge.entryMode -eq 'EVENT')
Check 'event charge priced from the tariff (80)' ($labCharge.unitPriceQar -eq 80.0)
Check 'event charge classified exempt with SAC 9993' ($labCharge.taxCategory -eq 'EXEMPT_SERVICE' -and $labCharge.hsnCode -eq '9993')

# 4 pharmacy goods charge (5% GST) + a TPA bill with the correct split
$goods = Api POST '/api/v1/billing/charges' @{ patientId = $paId; hospitalId = $h; chargeCode = "C3PH-$s"; chargeName = 'Paracetamol 500 x10'; category = 'pharmacy'; quantity = 1; unitPriceQar = 100.0; reason = 'pharmacy counter sale' } $staff
Check 'pharmacy goods charge defaults to 5% GST' ($goods.data.taxCategory -eq 'GOODS_GST_5')
$inv = (Api POST '/api/v1/billing/invoices' @{ patientId = $paId; payerClass = 'tpa' } $staff).data
Check 'TPA bill reprices the lab line to the rate card' ($inv.invoice.subtotalQar -eq 196.0)
Check 'mixed tax split: 5% on goods only (5.00)' ($inv.invoice.taxQar -eq 5.0)
Check 'invoice total = 201.00' ($inv.invoice.totalQar -eq 201.0)
Check 'exempt + GST tax lines present' ($inv.taxLines.Count -eq 2)
Check 'below e-invoice threshold' (-not $inv.invoice.eInvoiceRequired)

# 5 deposit + counter payment settle the bill; cash drawer balances
$deposit = Api POST '/api/v1/billing/deposits' @{ patientId = $paId; hospitalId = $h; amountQar = 100.0; method = 'cash' } $staff
Api POST "/api/v1/billing/deposits/$($deposit.data.id)/apply" @{ invoiceId = $inv.invoice.id } $staff | Out-Null
$invAfterDeposit = (Api GET "/api/v1/billing/invoices/$($inv.invoice.id)" $null $staff).data.invoice
Check 'deposit applied (paid 100, due 101)' ($invAfterDeposit.amountPaidQar -eq 100.0 -and $invAfterDeposit.amountDueQar -eq 101.0)
$cashup = Api POST '/api/v1/billing/cashups' @{ hospitalId = $h; openingFloatQar = 50.0 } $staff
$pay = Api POST "/api/v1/billing/invoices/$($inv.invoice.id)/payments" @{ method = 'cash'; amountQar = 101.0 } $staff
Check 'counter cash payment captured' ($pay.data.amountQar -eq 101.0)
$closed = Api POST "/api/v1/billing/cashups/$($cashup.data.id)/close" @{ countedCashQar = 151.0; note = 'balanced' } $staff
Check 'cash drawer balanced (variance 0)' ($closed.data.varianceQar -eq 0.0)
$invPaid = (Api GET "/api/v1/billing/invoices/$($inv.invoice.id)" $null $staff).data.invoice
Check 'invoice settled (status paid, due 0)' ($invPaid.status -eq 'paid' -and $invPaid.amountDueQar -eq 0.0)

# 6 reconciliation: the statement matches recorded cash
$from = (Get-Date).AddHours(-1).ToUniversalTime().ToString('o')
$to = (Get-Date).AddHours(1).ToUniversalTime().ToString('o')
$recon = Api POST '/api/v1/billing/recon/statements' @{ hospitalId = $h; method = 'cash'; periodStart = $from; periodEnd = $to; statementTotalQar = 101.0 } $staff
Check 'bank statement reconciles (MATCHED)' ($recon.data.status -eq 'MATCHED' -and $recon.data.varianceQar -eq 0.0)

# 7 HIM coding on the completed encounter
Api PATCH "/api/v1/encounters/$enc/complete" @{} $doc | Out-Null
$queue = (Api GET '/api/v1/him/coding-queue' $null $coder).data | Where-Object { $_.encounterId -eq $enc }
Check 'completed encounter on the coding queue' ($null -ne $queue)
$coding = Api POST "/api/v1/him/encounters/$enc/coding" @{ icdCodes = @('R05', 'J18.9') } $coder
Check 'ICD-10 coding assigned' ($coding.data.icdCodes -contains 'R05')

# 8 integrity engine flags an unbilled charge; supervisor resolves with audit
$unbilled = Api POST '/api/v1/billing/charges' @{ patientId = $paId; hospitalId = $h; chargeCode = "C3UNB-$s"; chargeName = 'Unbilled dressing'; quantity = 1; unitPriceQar = 25.0; reason = 'dressing at the counter' } $staff
$scan = (Api POST '/api/v1/integrity/scan?unbilledHours=0' @{} $sup).data
Check 'integrity scan flags the unbilled charge' ($scan.byRule.UNBILLED_CHARGE -ge 1)
$finding = (Api GET '/api/v1/integrity/findings?status=OPEN' $null $sup).data | Where-Object { $_.subjectId -eq $unbilled.data.id } | Select-Object -First 1
Check 'finding points at the charge' ($null -ne $finding)
$triaged = Api POST "/api/v1/integrity/findings/$($finding.id)/triage" @{ status = 'RESOLVED'; resolution = 'billed on the next invoice at the counter' } $sup
Check 'supervisor resolved with an audit note' ($triaged.data.status -eq 'RESOLVED' -and $null -ne $triaged.data.resolvedBy)

# 9 supervisor refund on a payment (approval chain)
$refund = Api POST "/api/v1/billing/payments/$($pay.data.id)/refund" @{ amountQar = 1.0; reason = 'rounding goodwill' } $sup
Check 'supervisor refund issued with a reason' ($refund.data.amountQar -eq 1.0)
$staffRefund = Api POST "/api/v1/billing/payments/$($pay.data.id)/refund" @{ amountQar = 1.0; reason = 'again' } $staff
Check 'staff cannot refund (403)' ($staffRefund._status -eq 403)

# 10 daily GL journal export balances
$journal = (Api GET "/api/v1/billing/journal/export?from=$from&to=$to" $null $staff).data
Check 'journal has cash debit + revenue credit lines' (($journal.lines | Where-Object { $_.account -eq 'CASH_CASH_IN' -and $_.debitQar -gt 0 }).Count -ge 1 -and ($journal.lines | Where-Object { $_.account -eq 'REVENUE' }).Count -ge 1)
Check 'journal debits equal credits' ([Math]::Abs($journal.totalDebitQar - $journal.totalCreditQar) -lt 0.001)

Write-Output "== result: $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
