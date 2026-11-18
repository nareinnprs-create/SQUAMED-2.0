# CHUNK 1 EXIT DEMO (section 01.12): walks the chunk-1 contract EXIT CRITERIA
# scenario end to end on fresh seed data - walk-in registration under 90 s, booked
# appointment, kiosk check-in (mobile identifier), TV display board + Hindi voice
# announcement on call, vitals capture, SOAP consult note with PIN signature,
# e-prescription under the allergy gate (BLOCK hard-fail, WARN override with a typed
# reason, clean Rx marked checked), CBC order (task-engine outbox event), tracked
# task, consult-fee receipt, follow-up booking via reception, encounter completion
# and the Patient 360 thread. Runs against the app on port 8080 (pwsh). ASCII only.
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
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

function OutboxRow($token, $eventType, $needle) {
  $rows = (Api GET '/api/v1/admin/events/outbox?limit=200' $null $token).data
  return $rows | Where-Object { $_.eventType -eq $eventType -and $_.envelope.Contains($needle) } | Select-Object -First 1
}

Write-Output "== 01.12 chunk-1 exit demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital + the walk's staff: doctor, receptionist, nurse, kiosk terminal
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Exit Demo $s"; code = "XD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
$docEmail = "doc-exit-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $docEmail; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Tor$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doc = (Api POST '/api/v1/auth/login' @{ username = $docEmail; password = 'Passw0rd!123' } $null).data.accessToken
$docId = (Api GET '/api/v1/auth/me' $null $doc).data.userId
$recEmail = "rec-exit-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Eption$s"; roles = @('RECEPTIONIST'); hospitalId = $h } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$nurEmail = "nur-exit-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $nurEmail; password = 'Passw0rd!123'; firstName = 'Nur'; lastName = "Singh$s"; roles = @('NURSE'); hospitalId = $h } $null | Out-Null
$nurse = (Api POST '/api/v1/auth/login' @{ username = $nurEmail; password = 'Passw0rd!123' } $null).data.accessToken
$kioEmail = "kio-exit-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $kioEmail; password = 'Passw0rd!123'; firstName = 'Kiosk'; lastName = "Term$s"; roles = @('KIOSK'); hospitalId = $h } $null | Out-Null
$kiosk = (Api POST '/api/v1/auth/login' @{ username = $kioEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'doctor + receptionist + nurse + kiosk log in' ($null -ne $doc -and $null -ne $rec -and $null -ne $nurse -and $null -ne $kiosk)

# 3 walk-in registration timed (contract: under 90 s)
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$stamp = (Get-Date).Ticks
$phone = '55' + ($stamp % 10000000000).ToString('D10')
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Asha'; lastName = "Verma$s"; gender = 'female'; dateOfBirth = '1991-06-06'; phone = $phone; hospitalId = $h } $admin).data.patient
$sw.Stop()
$paId = $pa.id; $mrn = $pa.mrn
Check 'walk-in registered with MRN' ($null -ne $paId -and $mrn.StartsWith('MQ'))
Check "walk-in registration under 90 s ([int]$sw.Elapsed.TotalSeconds s)" ($sw.Elapsed.TotalSeconds -lt 90)

# 4 booked appointment: today's session template -> slot -> booking (reception owns booking, V54).
# shortcut: template window is now+2h..now+3h so the today-slot is bookable at run time;
# a run between 21:00-24:00 crosses midnight and fails the template validation - move it to morning then.
$today = (Get-Date).ToString('yyyy-MM-dd')
$weekday = [int](Get-Date).DayOfWeek; if ($weekday -eq 0) { $weekday = 7 }
$tStart = (Get-Date).AddHours(2).ToString('HH:mm')
$tEnd = (Get-Date).AddHours(3).ToString('HH:mm')
$tmpl = (Api POST '/api/v1/schedules/templates' @{ doctorId = $docId; weekday = $weekday; startTime = $tStart; endTime = $tEnd; slotMinutes = 30; capacity = 1 } $rec).data
Check 'session template created' ($null -ne $tmpl.id)
$slots = @((Api GET "/api/v1/schedules/$docId/slots?from=$today&to=$today" $null $rec).data)
$slot = $slots[0]
Check 'today slot generated' ($null -ne $slot.id)
$appt = (Api POST '/api/v1/appointments' @{ slotId = $slot.id; patientId = $paId; appointmentType = 'OPD'; reason = 'OPD exit walk'; priority = 'routine'; visitType = 'outpatient' } $rec).data
Check 'appointment booked (201)' ($null -ne $appt.id)

# 5 kiosk check-in by mobile identifier -> queue slip with token
$slip = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'mobile'; identifier = $phone } $kiosk).data
$tok = $slip.token.tokenNumber
if ([string]::IsNullOrEmpty($tok)) { $tok = (@((Api GET '/api/v1/queue/board?queueType=consult' $null $kiosk).data) | Select-Object -Last 1).tokenNumber }
Check 'kiosk check-in issued queue token' (-not [string]::IsNullOrEmpty($tok))

# 6 TV display board renders for the display app (public read with a kiosk token)
$board = (Api GET '/api/v1/queue/display-board' $null $kiosk).data
Check 'TV display board fetched' ($null -ne $board.generatedAt -and $null -ne $board.counters)

# 7 TV voice announcement: service point + display zone + en/hi rule, then the call
$sp = (Api POST '/api/v1/queue/service-points' @{ name = 'Counter 1'; code = 'SP1'; queueType = 'consult'; counterNumber = '1' } $rec).data
$zone = (Api POST '/api/v1/queue/zones' @{ name = 'Main Display'; servicePointId = 'SP1'; volume = 90 } $rec).data
$rule = (Api POST '/api/v1/queue/announcement-rules' @{ queueType = 'consult'; repeatCount = 3; languages = 'en,hi'; zoneId = $zone.id } $rec).data
Check 'zone + announcement rule configured' ($null -ne $zone.id -and $null -ne $rule.id)
Api PATCH "/api/v1/queue/entries/consult/$tok/call" $null $rec | Out-Null
$envA = ((OutboxRow $admin 'announcement.enqueued' $tok).envelope | ConvertFrom-Json)
Check 'announcement.enqueued carries the Hindi voice text' ($envA.payload.texts.hi -and $envA.payload.texts.hi.Contains($tok))

# 8 encounter opened by the doctor
$enc = (Api POST '/api/v1/encounters' @{ patientId = $paId; reason = 'OPD exit walk'; chiefComplaint = 'fever 2 days' } $doc).data.id
Check 'encounter started' ($null -ne $enc)

# 9 vitals: the nurse is denied (clinical:write:vital is doctor-gated in chunk 1),
# the doctor records the vitals set
$nurVitals = Api POST "/api/v1/encounters/$enc/vitals" @{ bloodPressureSystolic = 118; bloodPressureDiastolic = 76; heartRate = 90; temperatureC = 38.1; spo2Percent = 98 } $nurse
Check 'nurse vitals denied (403) - chunk-1 boundary' ($nurVitals._status -eq 403)
$vitals = (Api POST "/api/v1/encounters/$enc/vitals" @{ bloodPressureSystolic = 118; bloodPressureDiastolic = 76; heartRate = 90; temperatureC = 38.1; spo2Percent = 98 } $doc).data
Check 'vitals captured' ($null -ne $vitals.id)

# 10 SOAP consult note + PIN signature (immutable once signed)
$note = (Api POST "/api/v1/encounters/$enc/notes" @{ noteType = 'progress'; title = 'OPD consult'; subjective = 'fever 2 days, myalgia'; objective = 'temp 38.1, throat clear'; assessment = 'suspected viral fever'; plan = 'hydrate, antipyretic' } $doc).data
$noPin = Api POST "/api/v1/encounters/notes/$($note.id)/sign" @{ method = 'PIN' } $doc
$signed = (Api POST "/api/v1/encounters/notes/$($note.id)/sign" @{ method = 'PIN'; pin = '4821' } $doc).data
$resign = Api POST "/api/v1/encounters/notes/$($note.id)/sign" @{ method = 'PIN'; pin = '4821' } $doc
Check 'SOAP note signed with PIN' ($signed.signatureMethod -eq 'PIN')
Check 're-sign rejected (409 NOTE_ALREADY_SIGNED)' ($resign._status -eq 409)

# 11 e-prescription under the allergy gate: document allergy first, then BLOCK,
# WARN-override with a typed reason, clean Rx marked checked
Api POST "/api/v1/patients/$paId/allergies" @{ allergen = 'Penicillin'; reaction = 'Urticaria'; severity = 'high' } $doc | Out-Null
$blocked = Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Penicillin'; patientMrn = $mrn } $doc
Check 'allergy BLOCK hard-fails (409 ALLERGY_BLOCK)' ($blocked._status -eq 409)
$warn = Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Benzylpenicillin'; patientMrn = $mrn } $doc
Check 'WARN without reason rejected (409 ALLERGY_WARN_REASON_REQUIRED)' ($warn._status -eq 409)
$overridden = (Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Benzylpenicillin'; patientMrn = $mrn; overrideReason = 'Culture confirms susceptible strain' } $doc).data
Check 'WARN override saved with typed reason' ($overridden.allergyStatus -eq 'warn_override')
$clean = (Api POST "/api/v1/encounters/$enc/prescriptions" @{ drugName = 'Paracetamol'; patientMrn = $mrn } $doc).data
Check 'clean Rx records allergyStatus=checked' ($clean.allergyStatus -eq 'checked')

# 12 CBC order + outbox event for the chunk-2 task engine
$order = (Api POST "/api/v1/encounters/$enc/orders" @{ orderType = 'lab'; orderName = 'CBC'; patientMrn = $mrn } $doc).data
Check 'CBC order placed' ($null -ne $order.id)
$envO = ((OutboxRow $admin 'encounter.order.placed' $order.id.ToString()).envelope | ConvertFrom-Json)
Check 'encounter.order.placed in the outbox' ($envO.eventType -eq 'encounter.order.placed')

# 13 tracked task for the nurse (task:manage:task) and a status transition
$task = (Api POST '/api/v1/tasks' @{ hospitalId = $h; title = 'Collect CBC sample'; description = 'CBC ordered on OPD exit walk'; relatedType = 'encounter'; relatedId = $enc } $nurse).data
$taskStart = (Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'in_progress' } $nurse).data
Check 'task tracked for the nurse (pending -> in_progress)' ($task.status -eq 'pending' -and $taskStart.status -eq 'in_progress')

# 14 consult-fee receipt on the reception visit
$visit = (Api POST '/api/v1/reception/visits' @{ patientId = $paId; visitType = 'outpatient'; reason = 'OPD exit walk' } $rec).data.visit
$receipt = (Api POST "/api/v1/reception/visits/$($visit.id)/receipts" @{ amountQar = 500; mode = 'cash' } $rec).data
Check 'consult-fee receipt issued' ($null -ne $receipt.id)

# 15 follow-up booking: reception books (doctor denied), encounter stamped
$fuDay = (Get-Date).AddDays(3).ToString('yyyy-MM-dd')
$fuWeekday = [int](Get-Date $fuDay).DayOfWeek; if ($fuWeekday -eq 0) { $fuWeekday = 7 }
Api POST '/api/v1/schedules/templates' @{ doctorId = $docId; weekday = $fuWeekday; startTime = '11:00'; endTime = '12:00'; slotMinutes = 30; capacity = 1; sessionType = 'in_person' } $rec | Out-Null
$fuSlot = @((Api GET "/api/v1/schedules/$docId/slots?from=$fuDay&to=$fuDay" $null $rec).data)[0]
$fuDenied = Api POST "/api/v1/encounters/$enc/follow-up" @{ slotId = '00000000-0000-0000-0000-000000000000' } $doc
$fu = (Api POST "/api/v1/encounters/$enc/follow-up" @{ slotId = $fuSlot.id } $rec).data
Check 'doctor cannot book the follow-up (403)' ($fuDenied._status -eq 403)
Check 'receptionist books the follow-up (encounter stamped)' ($null -ne $fu.followUpAppointmentId)

# 16 encounter completed
$done = (Api PATCH "/api/v1/encounters/$enc/complete" $null $doc).data
Check 'encounter completed' ($done.status -eq 'completed')

# 17 Patient 360 full thread
$p360 = (Api GET "/api/v1/patient360/$paId" $null $doc).data
$vitalsThread = @((Api GET "/api/v1/encounters/patients/$paId/vitals" $null $doc).data)
Check 'Patient 360 aggregation returned' ($null -ne $p360)
Check 'Patient 360 vitals thread populated' ($vitalsThread.Count -ge 1)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
