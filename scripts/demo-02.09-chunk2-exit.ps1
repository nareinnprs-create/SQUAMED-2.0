# CHUNK 2 EXIT DEMO (section 02.09): walks the chunk-2 contract EXIT CRITERIA
# scenario end to end on fresh seed data - doctor orders CBC + chest X-ray, phlebotomy
# barcode/collect + accession (chain of custody), analyzer-equivalent result entry with a
# critical potassium that opens an SLA-tracked critical result, ack, pathologist
# verification + release, branded PDF publish + counter delivery, TAT analytics,
# radiologist worklist from the modality worklist feed, study completion (Orthanc refs,
# StudyCompleted + imaging charge), structured report sign, critical finding + ack,
# pharmacy dispense with controlled-register entry and stock ledger consumption,
# over-dispense guard, charge events for lab + imaging, and the Patient 360 thread.
# Interaction WARN capture is proven by the 02.07 eval PIT (rules are dataset-driven,
# no rule-authoring API in chunk 2). Runs against the app on port 8080 (pwsh). ASCII only.
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

function OutboxRow($token, $eventType, $needle) {
  $rows = (Api GET '/api/v1/admin/events/outbox?limit=200' $null $token).data
  return $rows | Where-Object { $_.eventType -eq $eventType -and $_.envelope.Contains($needle) } | Select-Object -First 1
}

Write-Output "== 02.09 chunk-2 exit demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital + chunk-2 staff: doctor, pathologist, radiologist, pharmacist
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Chunk2 Exit $s"; code = "C2-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
function Staff($role, $tag) {
  $email = "$tag-c2-$s@test.local"
  Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = $tag; lastName = "Exit$s"; roles = @($role); hospitalId = $h } $null | Out-Null
  $tok = (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
  return $tok
}
$doc = Staff 'DOCTOR' 'doc'
$patho = Staff 'PATHOLOGIST' 'path'
$rad = Staff 'RADIOLOGIST' 'rad'
$pharm = Staff 'PHARMACIST' 'pharm'
$labtech = Staff 'LAB_TECHNICIAN' 'lab'
$docId = (Api GET '/api/v1/auth/me' $null $doc).data.userId
Check 'doctor + pathologist + radiologist + pharmacist + lab tech log in' ($null -ne $doc -and $null -ne $patho -and $null -ne $rad -and $null -ne $pharm -and $null -ne $labtech)

# 3 patient + NKA + encounter
$stamp = (Get-Date).Ticks
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Chunk'; lastName = "Two$s"; gender = 'female'; dateOfBirth = '1984-04-04'; phone = ('56' + ($stamp % 10000000000).ToString('D10')); hospitalId = $h } $admin).data.patient
$paId = $pa.id; $mrn = $pa.mrn
Check 'patient registered with MRN' ($null -ne $paId -and $mrn.StartsWith('MQ'))
$nka = Api POST "/api/v1/patients/$paId/allergies" @{ allergen = 'NKA'; severity = 'none' } $admin
Check 'NKA allergy status documented' ($nka.data.allergen -eq 'NKA')
$enc = (Api POST '/api/v1/encounters' @{ patientId = $paId; chiefComplaint = 'fever and cough'; reason = 'chunk-2 exit' } $doc).data.id
Check 'encounter started' ($null -ne $enc)

# 4 master critical test + CBC order + chest X-ray order
$testCode = "C2CRIT-$s"
$master = Api POST '/api/v1/master-data/lab-tests' @{ code = $testCode; name = "Critical Potassium $s"; unit = 'mmol/L'; referenceRange = '3.5-5.1'; criticalLow = 2.0; criticalHigh = 10.0; criticalSlaMinutes = 60 } $admin
Check 'master test with critical thresholds + SLA' ($master.data.criticalHigh -eq 10.0 -and $master.data.criticalSlaMinutes -eq 60)
$labOrder = (Api POST '/api/v1/lab/orders' @{ patientId = $paId; encounterId = $enc; tests = @(@{ testCode = $testCode; testName = "Critical Potassium $s" }); priority = 'urgent' } $doc).data.order
$labId = $labOrder.id
$labReqId = (Api GET "/api/v1/lab/orders/$labId" $null $doc).data.tests[0].id
Check 'CBC-style lab order placed' ($null -ne $labId)
$radOrder = (Api POST '/api/v1/radiology/orders' @{ patientId = $paId; encounterId = $enc; hospitalId = $h; modality = 'DX'; bodyPart = 'CHEST'; priority = 'urgent'; clinicalNote = 'rule out pneumonia' } $doc).data
$radId = $radOrder.id; $accession = $radOrder.accession
Check 'chest X-ray order with accession' ($accession.StartsWith('RAD-'))

# 5 phlebotomy: barcode collect -> accession/receive -> start (custody chain)
Check 'sample collected (barcode, lab tech)' ((Api POST "/api/v1/lab/orders/$labId/collect" @{} $labtech)._status -eq $null)
Check 'sample received/accessioned' ((Api POST "/api/v1/lab/orders/$labId/receive" @{} $labtech)._status -eq $null)
Check 'analysis started' ((Api POST "/api/v1/lab/orders/$labId/start" @{} $labtech)._status -eq $null)

# 6 critical result: enter 12.0 -> OPEN critical -> verify -> release blocked -> ack -> release
$entered = Api POST "/api/v1/lab/orders/$labId/results" @{ results = @(@{ testRequestId = $labReqId; resultValue = '12.0'; unit = 'mmol/L' }) } $doc
Check 'result entered (critical value)' ($entered._status -eq $null)
$criticals = (Api GET '/api/v1/lab/critical-results?status=OPEN' $null $admin).data | Where-Object { $_.labOrderId -eq $labId }
$crit = $criticals | Select-Object -First 1
Check 'critical result opened with SLA snapshot' ($null -ne $crit -and $crit.slaMinutes -eq 60)
Check 'verify (pathologist)' ((Api POST "/api/v1/lab/orders/$labId/verify" @{} $patho)._status -eq $null)
$blockedRelease = Api POST "/api/v1/lab/orders/$labId/release" @{} $patho
Check 'release blocked while critical unacked (CRITICAL_RESULT_UNACKED)' ($blockedRelease._status -eq 400 -and $blockedRelease._body.Contains('CRITICAL_RESULT_UNACKED'))
$ack = Api POST "/api/v1/lab/critical-results/$($crit.id)/ack" @{ note = 'called attending' } $patho
Check 'critical acknowledged with trail note' ($ack.data.status -eq 'ACKNOWLEDGED')
Check 'release after ack' ((Api POST "/api/v1/lab/orders/$labId/release" @{} $patho)._status -eq $null)

# 7 branded report publish + counter delivery
$report = Api POST "/api/v1/lab/orders/$labId/report" @{} $patho
Check 'report published (version 1)' ($report.data.reportVersion -eq 1)
$pdf = Invoke-WebRequest -Method GET -Uri "$base/api/v1/lab/orders/$labId/report" -Headers @{ Authorization = "Bearer $patho" } -UseBasicParsing
Check 'branded PDF served (%PDF magic)' ([System.Text.Encoding]::ASCII.GetString($pdf.Content[0..3]) -eq '%PDF')
Check 'delivered at the counter' ((Api POST "/api/v1/lab/reports/$($report.data.id)/deliver" @{ via = 'COUNTER' } $patho)._status -eq $null)

# 8 TAT analytics shows the day
$tat = (Api GET '/api/v1/lab/tat?days=30' $null $admin).data
Check 'TAT analytics rows present' ($tat.Count -ge 1)

# 9 radiology: MWL feed -> study completion -> report sign -> critical finding ack
$mwl = (Api GET '/api/v1/radiology/mwl?modality=DX' $null $rad).data | Where-Object { $_.accession -eq $accession }
Check 'modality worklist carries the order' ($null -ne $mwl)
$uid = "1.2.826.0.1.$s"
$study = Api POST "/api/v1/radiology/orders/$radId/study:complete" @{ studyInstanceUid = $uid; orthancStudyId = "orth-$s"; seriesCount = 2 } $rad
Check 'study completed with Orthanc refs' ($study.data.study.studyInstanceUid -eq $uid)
$studyEvent = OutboxRow $admin 'radiology.study_completed' $uid
Check 'StudyCompleted event in the outbox' ($null -ne $studyEvent)
$draft = Api POST "/api/v1/radiology/orders/$radId/reports" @{ templateCode = 'DX-CHEST'; findings = 'Right lower lobe consolidation.'; impression = 'Right lower lobe pneumonia.'; icdCodes = @('J18.9'); snomedCodes = @('385093006') } $rad
Check 'structured draft with ICD + SNOMED' ($draft.data.icdCodes[0] -eq 'J18.9' -and $draft.data.snomedCodes[0] -eq '385093006')
$signed = Api POST "/api/v1/radiology/reports/$($draft.data.id)/sign" @{} $rad
Check 'radiologist signed the report' ($signed.data.status -eq 'signed')
$cf = Api POST "/api/v1/radiology/reports/$($draft.data.id)/critical-findings" @{ severity = 'critical'; finding = 'Right lower lobe pneumonia'; slaMinutes = 30 } $rad
Check 'critical finding raised (SLA 30)' ($cf.data.status -eq 'OPEN' -and $cf.data.slaMinutes -eq 30)
Check 'critical finding acknowledged' ((Api POST "/api/v1/radiology/critical-findings/$($cf.data.id)/ack" @{ note = 'called ward' } $rad).data.status -eq 'ACKNOWLEDGED')

# 10 pharmacy: controlled drug dispense -> register + ledger; over-dispense guard
$drug = (Api POST '/api/v1/master-data/drugs' @{ code = "C2DRUG-$s"; name = "C2 Drug $s"; genericName = "c2drug$s"; category = 'Controlled'; unit = 'tab'; isControlled = $true } $admin).data
$stock = (Api POST '/api/v1/pharmacy/stock' @{ hospitalId = $h; drugId = $drug.id; drugName = "C2 Drug $s"; batchNumber = "C2B-$s"; expiryDate = (Get-Date).AddDays(180).ToString('yyyy-MM-dd'); quantityOnHand = 40; reorderLevel = 5; unitPrice = 9.5; location = 'C-1' } $admin).data
Check 'controlled drug stock seeded' ($stock.quantityOnHand -eq 40)
$rx = (Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugId = $drug.id; drugName = "C2 Drug $s"; quantity = 10; patientMrn = $mrn } $doc).data.id
Check 'eRx written (qty 10)' ($null -ne $rx)
$safety = Api POST '/api/v1/medication-safety/check' @{ patientId = $paId; drugIds = @($drug.id) } $admin
Check 'safety check runs clean for this pair' ($safety._status -eq $null)
$disp = Api POST '/api/v1/pharmacy/dispensations' @{ prescriptionId = $rx; patientId = $paId; encounterId = $enc; drugId = $drug.id; drugName = "C2 Drug $s"; quantity = 4; unitPrice = 9.5; secondAuthorizerId = $docId } $pharm
Check 'controlled dispense with second authorizer' ($disp.data.quantity -eq 4 -and $disp.data.batchNumber -eq "C2B-$s")
$over = Api POST '/api/v1/pharmacy/dispensations' @{ prescriptionId = $rx; patientId = $paId; encounterId = $enc; drugId = $drug.id; drugName = "C2 Drug $s"; quantity = 9; unitPrice = 9.5; secondAuthorizerId = $docId } $pharm
Check 'over-dispense blocked (DISPENSE_EXCEEDS_PRESCRIBED)' ($over._status -eq 400 -and $over._body.Contains('DISPENSE_EXCEEDS_PRESCRIBED'))
$register = (Api GET '/api/v1/pharmacy/controlled-register' $null $pharm).data | Where-Object { $_.dispensationId -eq $disp.data.id }
Check 'controlled register row with both actors' ($null -ne $register -and $register.secondAuthorizerId -eq $docId)
$ledger = (Api GET "/api/v1/pharmacy/stock/ledger?stockId=$($stock.id)" $null $pharm).data | Where-Object { $_.referenceId -eq $disp.data.id }
Check 'stock ledger consumption (-4) references the dispensation' ($null -ne $ledger -and $ledger.quantityDelta -eq -4 -and $ledger.balanceAfter -eq 36)

# 11 charge events for the performed services (chunk-3 consumers)
$labCharge = OutboxRow $admin 'billing.chargeable_performed' $labOrder.orderNumber
Check 'lab ChargeablePerformed in the outbox' ($null -ne $labCharge)
$imgCharge = OutboxRow $admin 'billing.chargeable_performed' $accession
Check 'imaging ChargeablePerformed in the outbox' ($null -ne $imgCharge)

# 12 Patient 360 thread: labs, imaging, medications
$p360 = (Api GET "/api/v1/patient360/$paId" $null $doc).data
Check 'Patient 360 lab orders' (($p360.labOrders | Where-Object { $_.id -eq $labId }).Count -eq 1)
Check 'Patient 360 imaging with signed report' (($p360.imaging | Where-Object { $_.accession -eq $accession -and $_.signedReportVersion -eq 1 }).Count -eq 1)
Check 'Patient 360 medications' (($p360.medications | Where-Object { $_.id -eq $disp.data.id }).Count -eq 1)

Write-Output "== result: $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
