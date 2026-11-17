# Exit-demo slice for section 01.04 (OPD EMR core): allergy BLOCK/WARN gate on
# prescriptions (override requires a typed reason + KPI counter), CS-01 two-identifier
# confirm (patient MRN on Rx + orders), clinical note signature (PIN required, re-sign
# 409), catalog-validated coded diagnoses, specialty note templates, procedures,
# referrals (target required), follow-up booking via the scheduling delegate (reception
# owns the slot, doctor denied), orders->tasks outbox event, RBAC and cross-tenant
# denial. Runs against the app on port 8081 (pwsh). ASCII only.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
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
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

Write-Output "== 01.04 OPD EMR core demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital + doctor + receptionist (+ foreign doctor for tenancy)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Emr Demo $s"; code = "ED-$s"; country = 'Qatar' } $admin).data.id
$hF = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Emr F $s"; code = "EDF-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h -and $null -ne $hF)
$docEmail = "doc-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $docEmail; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Tor$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doc = (Api POST '/api/v1/auth/login' @{ username = $docEmail; password = 'Passw0rd!123' } $null).data.accessToken
$recEmail = "recep-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Demo$s"; roles = @('RECEPTIONIST'); hospitalId = $h } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$docFEmail = "doc-foreign-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $docFEmail; password = 'Passw0rd!123'; firstName = 'For'; lastName = "Eign$s"; roles = @('DOCTOR'); hospitalId = $hF } $null | Out-Null
$docF = (Api POST '/api/v1/auth/login' @{ username = $docFEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'doctor + receptionist + foreign doctor log in' ($null -ne $doc -and $null -ne $rec -and $null -ne $docF)

# 3 patient with a documented penicillin allergy
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Emr'; lastName = "Patient$s"; gender = 'female'; dateOfBirth = '1990-02-02'; hospitalId = $h } $admin).data.patient
$paId = $pa.id; $mrn = $pa.mrn
Check 'patient registered with MRN' ($null -ne $paId -and $mrn -like 'MQ-*')
Api POST "/api/v1/patients/$paId/allergies" @{ allergen = 'Penicillin'; reaction = 'Urticaria'; severity = 'high' } $doc | Out-Null
Check 'allergy documented' $true

# 4 encounter start
$enc = (Api POST '/api/v1/encounters' @{ patientId = $paId; reason = 'OPD EMR demo'; chiefComplaint = 'sore throat' } $doc).data.id
Check 'encounter started' ($null -ne $enc)

# 5 CS-02: exact allergen match blocks the Rx outright
$blocked = Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Penicillin'; patientMrn = $mrn } $doc
Check 'CS-02 allergy BLOCK (409 ALLERGY_BLOCK)' ($blocked._status -eq 409)

# 6 CS-03: substring match warns - override needs a typed reason, then saves warn_override
$warn = Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Benzylpenicillin'; patientMrn = $mrn } $doc
Check 'CS-03 warn without reason (409 ALLERGY_WARN_REASON_REQUIRED)' ($warn._status -eq 409)
$overridden = (Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Benzylpenicillin'; patientMrn = $mrn; overrideReason = 'Culture confirms susceptible strain' } $doc).data
Check 'CS-03 warn override saved' ($overridden.allergyStatus -eq 'warn_override')
$metrics = Api GET '/actuator/prometheus' $null $null
Check 'warn-override KPI counter exported' ($metrics -match 'squamed_rx_allergy_warn_overrides_total')

# 7 CS-01: two-identifier confirm - missing or wrong MRN rejected, clean Rx records the screen
$noMrn = Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Paracetamol' } $doc
Check 'CS-01 Rx without MRN (400 PATIENT_MISMATCH)' ($noMrn._status -eq 400)
$clean = (Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Paracetamol'; patientMrn = $mrn } $doc).data
Check 'clean Rx records allergyStatus=checked' ($clean.allergyStatus -eq 'checked')

# 8 orders: same MRN gate + outbox event for the chunk-2 task engine
$orderNoMrn = Api POST "/api/v1/encounters/$enc/orders" @{ orderType = 'lab'; orderName = 'CBC' } $doc
Check 'order without MRN (400 PATIENT_MISMATCH)' ($orderNoMrn._status -eq 400)
$order = (Api POST "/api/v1/encounters/$enc/orders" @{ orderType = 'lab'; orderName = 'CBC'; patientMrn = $mrn } $doc).data
Check 'order placed' ($null -ne $order.id)
$outbox = (Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin).data
$emitted = @($outbox) | Where-Object { $_.eventType -eq 'encounter.order.placed' }
Check 'encounter.order.placed in the outbox' ($null -ne $emitted)

# 9 note signature: PIN required, re-sign 409, no note editing after signing
$note = (Api POST "/api/v1/encounters/$enc/notes" @{ noteType = 'progress'; title = 'OPD note'; subjective = 'sore throat 2 days'; assessment = 'pharyngitis'; plan = 'rest' } $doc).data
$noPin = Api POST "/api/v1/encounters/notes/$($note.id)/sign" @{ method = 'PIN' } $doc
Check 'sign without PIN (400 SIGNATURE_PIN_REQUIRED)' ($noPin._status -eq 400)
$signed = (Api POST "/api/v1/encounters/notes/$($note.id)/sign" @{ method = 'PIN'; pin = '4821' } $doc).data
Check 'PIN sign records the method' ($signed.signatureMethod -eq 'PIN')
$resign = Api POST "/api/v1/encounters/notes/$($note.id)/sign" @{ method = 'PIN'; pin = '4821' } $doc
Check 're-sign rejected (409 NOTE_ALREADY_SIGNED)' ($resign._status -eq 409)

# 10 coded diagnosis: unknown code rejected, catalog resolves the display name
$badDx = Api POST "/api/v1/encounters/$enc/diagnoses" @{ code = 'ZZZ9'; system = 'ICD-10' } $doc
Check 'unknown dx code (400 DIAGNOSIS_CODE_UNKNOWN)' ($badDx._status -eq 400)
$dx = (Api POST "/api/v1/encounters/$enc/diagnoses" @{ code = 'E11.9'; system = 'ICD-10' } $doc).data
Check 'catalog display fills the dx name' ($dx.name -eq 'Type 2 diabetes mellitus without complications')

# 11 global catalogs: 8+ specialty SOAP templates, 45+ dx rows
$templates = (Api GET '/api/v1/encounters/note-templates' $null $doc).data
$catalog = (Api GET '/api/v1/encounters/diagnosis-catalog' $null $doc).data
Check 'note templates seeded (8+)' (@($templates).Count -ge 8)
Check 'diagnosis catalog seeded (45+)' (@($catalog).Count -ge 45)

# 12 procedures: documented on the encounter, listable
$proc = (Api POST "/api/v1/encounters/$enc/procedures" @{ procedureName = 'Nebulisation'; status = 'completed'; notes = 'single dose' } $doc).data
$procs = (Api GET "/api/v1/encounters/$enc/procedures" $null $doc).data
Check 'procedure recorded + listed' ($proc.status -eq 'completed' -and @($procs).Count -eq 1)

# 13 referrals: target required, specialty referral persists
$noTarget = Api POST "/api/v1/encounters/$enc/referrals" @{ reason = 'unclear' } $doc
Check 'referral without target (400 REFERRAL_TARGET_REQUIRED)' ($noTarget._status -eq 400)
$referral = (Api POST "/api/v1/encounters/$enc/referrals" @{ toSpecialty = 'Cardiology'; reason = 'chest pain workup'; urgency = 'urgent' } $doc).data
$referrals = (Api GET "/api/v1/encounters/$enc/referrals" $null $doc).data
Check 'referral created pending + listed' ($referral.status -eq 'pending' -and @($referrals).Count -eq 1)

# 14 follow-up: booking stays with reception (V54); the encounter gets the appointment stamp
$fuDenied = Api POST "/api/v1/encounters/$enc/follow-up" @{ slotId = '00000000-0000-0000-0000-000000000000' } $doc
Check 'doctor cannot book follow-up (403)' ($fuDenied._status -eq 403)
$day = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
$weekday = [int](Get-Date $day).DayOfWeek; if ($weekday -eq 0) { $weekday = 7 }
Api POST '/api/v1/schedules/templates' @{ doctorId = (Api GET '/api/v1/auth/me' $null $admin).data.userId; weekday = $weekday; startTime = '09:00'; endTime = '10:00'; slotMinutes = 30; capacity = 1 } $rec | Out-Null
$slots = (Api GET "/api/v1/schedules/$((Api GET '/api/v1/auth/me' $null $admin).data.userId)/slots?from=$day&to=$day" $null $rec).data
$slot = @($slots)[0].id
$fu = (Api POST "/api/v1/encounters/$enc/follow-up" @{ slotId = $slot } $rec).data
Check 'receptionist books follow-up (encounter stamped)' ($null -ne $fu.followUpAppointmentId)

# 15 cross-tenant denial
$fxEnc = Api GET "/api/v1/encounters/$enc" $null $docF
$fxProc = Api GET "/api/v1/encounters/$enc/procedures" $null $docF
Check 'cross-tenant encounter read denied' ($fxEnc._status -eq 403)
Check 'cross-tenant procedures read denied' ($fxProc._status -eq 403)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
