# CHUNK 4 EXIT DEMO (section 04.09): walks the chunk-4 contract EXIT CRITERIA scenario end
# to end - ER arrival + triage, admission with a bed, nursing assessment/care plan/risks,
# eMAR doses with a high-alert double-check and barcode hard block, monitor vitals into the
# ICU flowsheet with fluid totals and a SOFA score, an OT case through the WHO checklist
# (sign-in/time-out/sign-out), implant capture, PACU hand-off, the discharge checklist with
# med-rec before the signed coded summary, a final bill with package pricing settled, the
# bed auto-released to housekeeping then cleared, and a blood-bank crossmatch round-trip.
# Blood bank legs run as admin (no blood-bank role is seeded yet). Runs against the app on
# port 8080 (pwsh). ASCII only.
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

Write-Output "== 04.09 chunk-4 exit demo (8080) =="

# 1 admin, a fresh hospital with a ward + beds, and the ward staff
$admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
Check 'admin login' ($null -ne $admin)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Chunk4 Exit $s"; code = "C4-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
$ward = (Api POST "/api/v1/hospital-config/hospitals/$h/wards" @{ name = "Ward $s"; code = "W-$s"; type = 'general' } $admin).data.id
$icuWard = (Api POST "/api/v1/hospital-config/hospitals/$h/wards" @{ name = "ICU $s"; code = "ICU-$s"; type = 'icu' } $admin).data.id
$bedId = (Api POST "/api/v1/hospital-config/hospitals/$h/beds" @{ label = "B1-$s"; type = 'standard'; status = 'available'; wardId = $ward } $admin).data.id
$icuBedId = (Api POST "/api/v1/hospital-config/hospitals/$h/beds" @{ label = "ICU1-$s"; type = 'icu'; status = 'available'; wardId = $icuWard } $admin).data.id
Check 'ward + ICU ward + beds created' ($null -ne $bedId -and $null -ne $icuBedId)
function Staff($role, $tag) {
  $email = "$tag-c4-$s@test.local"
  Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = $tag; lastName = "Exit$s"; roles = @($role); hospitalId = $h } $null | Out-Null
  return (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
}
$doc = Staff 'DOCTOR' 'doc'
$nurse = Staff 'NURSE' 'nurse'
$checker = Staff 'NURSE' 'checker'
$bill = Staff 'BILLING_STAFF' 'bill'
$checkerId = (Api GET '/api/v1/auth/me' $null $checker).data.userId
Check 'doctor + two nurses + billing staff log in' ($null -ne $doc -and $null -ne $nurse -and $null -ne $checker -and $null -ne $bill -and $null -ne $checkerId)

# 2 patient, ER arrival + triage, admission into the bed
$stamp = (Get-Date).Ticks
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Ward'; lastName = "Exit$s"; gender = 'male'; dateOfBirth = '1962-02-02'; phone = ('61' + ($stamp % 10000000000).ToString('D10')); qid = ('C4' + ($stamp % 100000000).ToString('D9')).Substring(0, 11); nationality = 'QA'; city = 'Doha'; hospitalId = $h } $admin).data.patient
$paId = $pa.id; $mrn = $pa.mrn; $fullName = "Ward Exit$s"
Api POST "/api/v1/patients/$paId/allergies" @{ allergen = 'NKA'; severity = 'none' } $admin | Out-Null
$er = (Api POST '/api/v1/emergency/registrations' @{ hospitalId = $h; patientId = $paId; arrivalMode = 'ambulance'; presentingComplaint = 'RUQ pain with fever' } $doc).data
Check 'ER arrival registered' ($er.status -eq 'registered')
$triaged = (Api POST "/api/v1/emergency/registrations/$($er.id)/triage" @{ esiLevel = 2; triageCategory = 'emergent'; painScale = 7; vitalTriggers = $true; acuityNotes = 'sepsis screen' } $doc).data
Check 'triage-lite recorded (ESI 2)' ($triaged.esiLevel -eq 2 -and (Api GET /api/v1/emergency/registrations/$($er.id) $null $doc).data.status -eq 'triaged')
$adm = (Api POST '/api/v1/admissions' @{ hospitalId = $h; patientId = $paId; bedId = $bedId; admissionType = 'emergency' } $nurse).data
$admId = $adm.id
Check 'admitted with the bed assigned' ($adm.status -eq 'admitted' -and $adm.bedId -eq $bedId)
$disp = (Api PATCH "/api/v1/emergency/registrations/$($er.id)/status" @{ status = 'admitted'; admittedAdmissionId = $admId } $doc).data
Check 'ER disposition linked to the admission' ($disp.admittedAdmissionId -eq $admId)
$board = (Api GET '/api/v1/beds/board' $null $nurse).data | Where-Object { $_.bedId -eq $bedId }
Check 'bed board shows the bed occupied' ($board.status -eq 'occupied' -and $board.admissionId -eq $admId)

# 3 nursing assessment + care plan + falls risk + vitals
$assessment = (Api POST '/api/v1/nursing/assessments' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; assessmentType = 'initial'; findings = 'Febrile, RUQ tenderness, alert'; riskLevel = 'medium' } $nurse).data
Check 'nursing assessment recorded' ($null -ne $assessment.id)
$careplan = (Api POST '/api/v1/nursing/careplans' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; title = 'Sepsis watch'; problem = 'Infection risk'; goal = 'Afebrile in 48h'; interventions = 'Hourly vitals, antibiotics on time, fluid balance' } $nurse).data
Check 'care plan opened' ($careplan.status -eq 'active')
$risk = (Api POST '/api/v1/nursing/risk-assessments' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; riskType = 'falls'; score = 12; level = 'high' } $nurse).data
Check 'falls risk scored high' ($risk.level -eq 'high')
$v = (Api POST '/api/v1/nursing/vitals' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; systolic = 96; diastolic = 58; heartRate = 118; respiratoryRate = 24; temperature = 38.7; oxygenSaturation = 94; gcs = 15 } $nurse).data
Check 'ward vitals charted' ($v.heartRate -eq 118)

# 4 eMAR: insulin (high-alert) with barcode block + second checker
$enc = (Api POST '/api/v1/encounters' @{ patientId = $paId; chiefComplaint = 'RUQ pain'; reason = 'chunk-4 exit' } $doc).data.id
$drug = (Api POST '/api/v1/master-data/drugs' @{ code = "INS-$s"; name = 'Insulin Glargine'; genericName = 'insulin glargine'; category = 'General'; unit = 'unit' } $admin).data.id
$rx = (Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugId = $drug; drugName = 'Insulin Glargine'; dose = '10 units'; frequency = 'BD'; route = 'subcutaneous'; quantity = 10; patientMrn = $mrn } $doc).data.id
$doses = (Api POST '/api/v1/nursing/mar/schedule' @{ prescriptionId = $rx; hospitalId = $h; admissionId = $admId; doses = 2 } $nurse).data
Check 'BD prescription schedules two doses' ($doses.Count -eq 2 -and $doses[0].highAlert)
$wrong = Api POST "/api/v1/nursing/mar/$($doses[0].id)/administer" @{ patientBarcode = 'MQ-0000-0001'; drugBarcode = $drug } $nurse
Check 'wrong wristband is a hard block (409)' ($wrong._status -eq 409 -and $wrong._body -like '*MAR_BARCODE_MISMATCH*')
$noCheck = Api POST "/api/v1/nursing/mar/$($doses[0].id)/administer" @{ patientBarcode = $mrn; drugBarcode = $drug } $nurse
Check 'high-alert dose without a second checker blocked' ($noCheck._status -eq 400 -and $noCheck._body -like '*HIGH_ALERT_SECOND_CHECK_REQUIRED*')
$given = (Api POST "/api/v1/nursing/mar/$($doses[0].id)/administer" @{ patientBarcode = $mrn; drugBarcode = $drug; secondCheckerId = $checkerId } $nurse).data
Check 'high-alert dose administered with double-check' ($given.status -eq 'administered' -and $given.secondCheckerId -eq $checkerId)

# 5 ICU: monitor vitals into the flowsheet, fluid balance totals, SOFA
$iv = (Api POST '/api/v1/icu/vitals' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; systolic = 88; diastolic = 52; heartRate = 124; respiratoryRate = 28; temperature = 39.0; oxygenSaturation = 91; map = 64; gcs = 14 } $nurse).data
Check 'ICU monitor vitals streamed' ($iv.map -eq 64)
Api POST '/api/v1/icu/vitals' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; systolic = 102; diastolic = 60; heartRate = 108; respiratoryRate = 22; temperature = 38.4; oxygenSaturation = 95; map = 74; gcs = 15 } $nurse | Out-Null
Api POST '/api/v1/icu/fluid-balance' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; inputMl = 1000.0; outputMl = 600.0; inputType = 'crystalloid'; outputType = 'urine' } $nurse | Out-Null
Api POST '/api/v1/icu/fluid-balance' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; inputMl = 500.0; outputMl = 300.0; inputType = 'crystalloid'; outputType = 'urine' } $nurse | Out-Null
$totals = (Api GET "/api/v1/icu/patients/$paId/fluid-balance/totals?hours=24" $null $nurse).data
Check 'fluid balance totals net +600 ml' ($totals.inputMl -eq 1500.0 -and $totals.outputMl -eq 900.0 -and $totals.netMl -eq 600.0)
$sofa = (Api POST '/api/v1/icu/severity-scores' @{ patientId = $paId; hospitalId = $h; admissionId = $admId; pao2 = 80.0; fio2 = 0.5; ventilated = $false; platelets = 180.0; bilirubin = 1.2; map = 64; gcs = 14; creatinine = 1.1 } $nurse).data
Check 'SOFA score computed' ($sofa.scoreType -eq 'SOFA' -and $sofa.total -gt 0)
$flowsheet = (Api GET "/api/v1/icu/patients/$paId/flowsheet?hours=6" $null $nurse).data
Check 'ICU flowsheet carries the charted hours' ($flowsheet.Count -ge 1)

# 6 OT: consent -> schedule -> WHO checklist (three stages) -> implants -> PACU
$consent = (Api POST '/api/v1/consents' @{ hospitalId = $h; patientId = $paId; purpose = 'treatment'; scope = 'procedure'; signedByName = $fullName } $admin).data
$sched = (Api POST '/api/v1/surgery/schedules' @{ hospitalId = $h; patientId = $paId; admissionId = $admId; bookingNumber = "OT-$s"; procedureName = 'Laparoscopic cholecystectomy'; procedureCode = 'CH-02'; specialty = 'general'; consentId = $consent.id; scheduledStartAt = (Get-Date).AddHours(1).ToUniversalTime().ToString('o') } $doc).data
Check 'OT case scheduled with consent on file' ($sched.status -eq 'scheduled')
$who = (Api POST "/api/v1/surgery/schedules/$($sched.id)/checklist/who" @{} $doc).data
$whoItems = @($who.signIn) + @($who.timeOut) + @($who.signOut)
Check 'WHO checklist seeded with all three stages' ($who.signIn.Count -eq 5 -and $who.timeOut.Count -eq 4 -and $who.signOut.Count -eq 4)
foreach ($item in $whoItems) { Api PATCH "/api/v1/surgery/schedules/$($sched.id)/checklist/$($item.id)/check" @{} $doc | Out-Null }
$checked = (Api GET "/api/v1/surgery/schedules/$($sched.id)/checklist" $null $doc).data
Check 'all WHO items checked across the case' ($checked.Count -eq 13 -and (@($checked | Where-Object { -not $_.checked })).Count -eq 0)
$started = (Api PATCH "/api/v1/surgery/schedules/$($sched.id)/status" @{ status = 'started' } $doc).data
Check 'case started after the checklist' ($started.status -eq 'started')
$implant = (Api POST "/api/v1/surgery/schedules/$($sched.id)/implants" @{ implantName = 'Titanium clip'; catalogNumber = "CLIP-$s"; lotNumber = 'LOT-1'; expiryDate = (Get-Date).AddYears(2).ToString('yyyy-MM-dd'); quantity = 3 } $doc).data
Check 'implant recorded with lot + expiry' ($implant.quantity -eq 3 -and $implant.lotNumber -eq 'LOT-1')
Api POST "/api/v1/surgery/schedules/$($sched.id)/intra-op" @{ eventName = 'critical_moment'; notes = 'Cystic artery clipped' } $doc | Out-Null
$done = (Api PATCH "/api/v1/surgery/schedules/$($sched.id)/status" @{ status = 'completed' } $doc).data
Check 'case completed' ($done.status -eq 'completed')
$pacu = (Api POST "/api/v1/surgery/schedules/$($sched.id)/post-op" @{ patientCondition = 'stable'; destination = 'recovery'; painScore = 3; drainOutputMl = 20 } $doc).data
Check 'PACU hand-off recorded' ($pacu.destination -eq 'recovery')

# 7 billing: room charge by bed-hour, package price, final bill settled
Api POST '/api/v1/billing/tariffs' @{ hospitalId = $h; code = 'ROOM-STANDARD'; name = 'Standard room/hour'; category = 'room'; priceQar = 100.0 } $admin | Out-Null
$room = (Api POST "/api/v1/admissions/$admId/room-charge" @{ hours = 5 } $nurse).data
Check 'room charge priced from the tariff (5h x 100)' ($room.totalQar -eq 500.0)
$pkg = (Api POST '/api/v1/billing/packages' @{ hospitalId = $h; code = "PKG-CH-$s"; name = 'Lap chole package' } $bill).data
Api POST "/api/v1/billing/packages/$($pkg.id)/versions" @{ priceQar = 1200.0 } $bill | Out-Null
$pkgCharge = (Api POST '/api/v1/billing/charges' @{ patientId = $paId; hospitalId = $h; chargeCode = "PKG-CH-$s"; chargeName = 'Lap chole package'; category = 'procedure'; quantity = 1; packageCode = "PKG-CH-$s"; reason = 'package pricing for the OT case' } $bill).data
Check 'package charge priced from the pinned version (1200)' ($pkgCharge.unitPriceQar -eq 1200.0)
$inv = (Api POST '/api/v1/billing/invoices' @{ patientId = $paId; payerClass = 'general' } $bill).data
Check 'final bill = room 500 + package 1200' ($inv.invoice.totalQar -eq 1700.0)
$pay = (Api POST "/api/v1/billing/invoices/$($inv.invoice.id)/payments" @{ method = 'card'; amountQar = 1700.0; reference = "CARD-$s" } $bill).data
Check 'final bill settled by card' ($pay.amountQar -eq 1700.0)
$settled = (Api GET "/api/v1/billing/invoices/$($inv.invoice.id)" $null $bill).data.invoice
Check 'invoice status paid, due 0' ($settled.status -eq 'paid' -and $settled.amountDueQar -eq 0.0)

# 8 discharge: med-rec before the signed coded summary, checklist legs, bed -> housekeeping
Api PUT "/api/v1/admissions/$admId/discharge-summary" @{ summaryText = 'Lap chole, uneventful recovery'; followUpPlan = 'Surgical clinic in 2 weeks'; medicationsAtDischarge = 'Paracetamol 1g prn' } $nurse | Out-Null
$earlySign = Api POST "/api/v1/admissions/$admId/discharge-summary/sign" @{ icdCodes = @('K80.2') } $nurse
Check 'summary cannot be signed before med-rec (409)' ($earlySign._status -eq 409 -and $earlySign._body -like '*DISCHARGE_MEDREC_REQUIRED*')
Api POST "/api/v1/admissions/$admId/checklist/med_rec" @{} $nurse | Out-Null
$tasks = (Api GET '/api/v1/tasks' $null $doc).data
Check 'med-rec review task opened for the attending' ((@($tasks | Where-Object { $_.relatedType -eq 'DISCHARGE' -and $_.relatedId -eq $admId })).Count -ge 1)
$signed = (Api POST "/api/v1/admissions/$admId/discharge-summary/sign" @{ icdCodes = @('K80.2') } $nurse).data
Check 'coded summary signed after med-rec' ($signed.status -eq 'SIGNED' -and $signed.icdCodes -contains 'K80.2')
Api POST "/api/v1/admissions/$admId/checklist/pharmacy_return" @{} $nurse | Out-Null
Api POST "/api/v1/admissions/$admId/checklist/final_bill" @{} $nurse | Out-Null
$discharged = (Api POST "/api/v1/admissions/$admId/discharge" @{} $nurse).data
Check 'admission discharged' ($discharged.status -eq 'discharged')
$boardAfter = (Api GET '/api/v1/beds/board' $null $nurse).data | Where-Object { $_.bedId -eq $bedId }
Check 'bed auto-released to cleaning' ($boardAfter.status -eq 'cleaning')
$hk = (Api GET '/api/v1/tasks' $null $doc).data | Where-Object { $_.relatedType -eq 'HOUSEKEEPING' -and $_.relatedId -eq $bedId } | Select-Object -First 1
Check 'housekeeping task raised for the bed' ($null -ne $hk)
Api POST "/api/v1/beds/$bedId/available" @{} $nurse | Out-Null
$boardReady = (Api GET '/api/v1/beds/board' $null $nurse).data | Where-Object { $_.bedId -eq $bedId }
Check 'bed cleared back to available' ($boardReady.status -eq 'available' -and $null -eq $boardReady.admissionId)

# 9 blood bank crossmatch round-trip (admin: no blood-bank role seeded yet)
$today = Get-Date
$donor = (Api POST '/api/v1/blood-bank/donors' @{ hospitalId = $h; donorNumber = "BD-$s"; fullName = 'Exit Donor'; bloodGroup = 'O+'; gender = 'male' } $admin).data
$unit = (Api POST '/api/v1/blood-bank/units' @{ hospitalId = $h; unitNumber = "BU-$s"; bloodGroup = 'O+'; component = 'prbc'; expiryDate = $today.AddDays(35).ToString('yyyy-MM-dd'); donorId = $donor.id } $admin).data
Check 'donor + unit registered' ($unit.screeningStatus -eq 'PENDING')
$grouping = (Api POST '/api/v1/blood-bank/grouping' @{ hospitalId = $h; unitId = $unit.id; aboResult = 'O'; rhResult = 'positive' } $admin).data
Api PATCH "/api/v1/blood-bank/grouping/$($grouping.id)/confirm" @{} $admin | Out-Null
$xm = (Api POST '/api/v1/blood-bank/crossmatch' @{ hospitalId = $h; patientId = $paId; unitId = $unit.id; technique = 'gel' } $admin).data
Api PATCH "/api/v1/blood-bank/crossmatch/$($xm.id)/result" @{ compatibility = 'compatible' } $admin | Out-Null
$unscreened = Api POST '/api/v1/blood-bank/issues' @{ hospitalId = $h; patientId = $paId; unitId = $unit.id; patientBarcode = $mrn; confirmPatientName = $fullName } $admin
Check 'unscreened unit cannot be issued' ($unscreened._status -eq 400 -and $unscreened._body -like '*BLOOD_UNIT_NOT_SCREENED*')
Api POST "/api/v1/blood-bank/units/$($unit.id)/screening" @{ ttiResult = 'CLEARED'; natResult = 'CLEARED' } $admin | Out-Null
$wrongBand = Api POST '/api/v1/blood-bank/issues' @{ hospitalId = $h; patientId = $paId; unitId = $unit.id; patientBarcode = 'MQ-0000-0001'; confirmPatientName = $fullName } $admin
Check 'wrong wristband blocks the issue (409)' ($wrongBand._status -eq 409 -and $wrongBand._body -like '*BLOOD_IDENTITY_MISMATCH*')
$issue = (Api POST '/api/v1/blood-bank/issues' @{ hospitalId = $h; patientId = $paId; unitId = $unit.id; patientBarcode = $mrn; confirmPatientName = $fullName } $admin).data
Check 'identity-verified issue succeeds' ($null -ne $issue.id)
$tx = (Api POST '/api/v1/blood-bank/transfusions' @{ hospitalId = $h; patientId = $paId; unitId = $unit.id; volumeMl = 280 } $admin).data
Check 'transfusion started' ($tx.volumeMl -eq 280)
$trace = (Api GET "/api/v1/blood-bank/traceability?unitId=$($unit.id)" $null $admin).data
Check 'trace donor -> grouping -> crossmatch -> issue -> transfusion' ($trace.donorName -eq 'Exit Donor' -and $null -ne $trace.grouping -and $trace.crossmatches.Count -ge 1 -and $null -ne $trace.issue -and $trace.transfusions.Count -ge 1)

# 10 the bed board reflects the whole day
$finalBoard = (Api GET '/api/v1/beds/board' $null $nurse).data
$icuRow = $finalBoard | Where-Object { $_.bedId -eq $icuBedId }
Check 'bed board carries the ward + ICU beds' ($finalBoard.Count -ge 2 -and $null -ne $icuRow)

Write-Output "== result: $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
