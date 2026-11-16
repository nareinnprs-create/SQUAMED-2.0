# Exit-demo slice for section 01.01 (Scheduling engine): session templates, idempotent slot
# generation, slot booking (capacity + overbook + no-show policy), template RBAC, waitlist
# promote, legacy cursor list, audit trail. Runs against the app on port 8081 (pwsh). ASCII only.
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

Write-Output "== 01.01 scheduling engine demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
$adminId = (Api GET '/api/v1/auth/me' $null $admin).data.userId
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital + receptionist (V54 re-lands scheduling write on RECEPTIONIST)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Sched Demo $s"; code = "SD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
$recEmail = "sched-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Demo$s"; roles = @('RECEPTIONIST'); hospitalId = $h } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'receptionist login' ($null -ne $rec)

# 3 patient (admin is not hospital-bound, so the explicit hospitalId pins the row)
$patId = (Api POST '/api/v1/patients' @{ firstName = 'Slot'; lastName = "Demo$s"; gender = 'female'; dateOfBirth = '1990-02-02'; hospitalId = $h } $admin).data.patient.id
Check 'patient created' ($null -ne $patId)

# 4 scheduling config doc: overbook factor 2.0 + no-show limit 1
$cfg = Api PUT "/api/v1/hospital-config/hospitals/$h/config/scheduling" @{ overbookFactor = 2.0; noShowWindowDays = 30; noShowLimit = 1 } $admin
Check 'scheduling config published' ($null -ne $cfg.data.id)

# 5 template validation: weekday 0 and end<=start rejected with the guard code
$day = (Get-Date).AddDays(3)
$dayStr = $day.ToString('yyyy-MM-dd')
$iso = [int]$day.DayOfWeek; if ($iso -eq 0) { $iso = 7 }
$badWd = Api POST '/api/v1/schedules/templates' @{ doctorId = $adminId; weekday = 0; startTime = '09:00'; endTime = '10:00' } $admin
$badEnd = Api POST '/api/v1/schedules/templates' @{ doctorId = $adminId; weekday = $iso; startTime = '10:00'; endTime = '09:00' } $admin
Check 'template validation (400 weekday 0 / end<=start)' ($badWd._status -eq 400 -and $badEnd._status -eq 400)

# 6 session template by receptionist: 2h, 60-min slots, capacity 1 -> two slots
$tpl = (Api POST '/api/v1/schedules/templates' @{ doctorId = $adminId; weekday = $iso; startTime = '09:00'; endTime = '11:00'; slotMinutes = 60; capacity = 1 } $rec).data
Check 'receptionist creates template' ($null -ne $tpl.id -and $tpl.active)

# 7 idempotent slot generation
$slots1 = (Api GET "/api/v1/schedules/$adminId/slots?from=$dayStr&to=$dayStr" $null $rec).data
$slots2 = (Api GET "/api/v1/schedules/$adminId/slots?from=$dayStr&to=$dayStr" $null $rec).data
Check 'two open slots generated' ($slots1.Count -eq 2 -and $slots1[0].status -eq 'open' -and $slots1[0].startTime -eq '09:00:00')
Check 'slot generation idempotent (same ids)' (($slots1 | ForEach-Object { $_.id }) -join ',' -eq ($slots2 | ForEach-Object { $_.id }) -join ',')
$slotA = $slots1[0].id
$slotB = $slots1[1].id

# 8 booking derives doctor/time from the slot (slot instants are ZoneOffset.UTC by construction)
$b1 = (Api POST '/api/v1/appointments' @{ slotId = $slotA; patientId = $patId; appointmentType = 'OPD' } $rec).data
Check 'slot booking derives slot fields' ($null -ne $b1.id -and $b1.slotId -eq $slotA -and $b1.scheduledStart.ToString('yyyy-MM-dd') -eq $dayStr)

# 9 overbook factor 2.0 on capacity 1: second 201, third 400 SCHEDULE_SLOT_FULL
$pat2 = (Api POST '/api/v1/patients' @{ firstName = 'Over'; lastName = "Book$s"; gender = 'male'; dateOfBirth = '1991-03-03'; hospitalId = $h } $admin).data.patient.id
$pat3 = (Api POST '/api/v1/patients' @{ firstName = 'Third'; lastName = "Book$s"; gender = 'male'; dateOfBirth = '1992-04-04'; hospitalId = $h } $admin).data.patient.id
$b2 = Api POST '/api/v1/appointments' @{ slotId = $slotA; patientId = $pat2; appointmentType = 'OPD' } $rec
$b3 = Api POST '/api/v1/appointments' @{ slotId = $slotA; patientId = $pat3; appointmentType = 'OPD' } $rec
Check 'overbook 2.0 admits second, rejects third' ($null -ne $b2.data.id -and $b3._status -eq 400)

# 10 no-show policy: window count 1 >= limit 1 -> rebook denied; override wins (slotB has capacity left)
Api PATCH "/api/v1/appointments/$($b1.id)/status" @{ status = 'no-show' } $rec | Out-Null
$rebook = Api POST '/api/v1/appointments' @{ slotId = $slotA; patientId = $patId; appointmentType = 'OPD' } $rec
$override = Api POST '/api/v1/appointments' @{ slotId = $slotB; patientId = $patId; appointmentType = 'OPD'; overrideNoShowLimit = $true } $rec
Check 'no-show limit denies rebook, override admits' ($rebook._status -eq 400 -and $null -ne $override.data.id)

# 11 template RBAC: doctor reads schedules, cannot write templates or book
$docEmail = "sched-doc-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $docEmail; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Sched$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doc = (Api POST '/api/v1/auth/login' @{ username = $docEmail; password = 'Passw0rd!123' } $null).data.accessToken
$docRead = Api GET "/api/v1/schedules/templates?doctorId=$adminId" $null $doc
$docWrite = Api POST '/api/v1/schedules/templates' @{ doctorId = $adminId; weekday = $iso; startTime = '13:00'; endTime = '14:00' } $doc
$docBook = Api POST '/api/v1/appointments' @{ slotId = $slotB; patientId = $pat2; appointmentType = 'OPD' } $doc
Check 'doctor reads schedules (200)' ($null -ne $docRead.data)
Check 'doctor template write denied (403)' ($docWrite._status -eq 403)
Check 'doctor booking denied (403)' ($docBook._status -eq 403)

# 12 waitlist fairness: urgent heads the queue; promote consumes a concrete slot
$dayP = ((Get-Date).AddDays(4)); $dayPStr = $dayP.ToString('yyyy-MM-dd'); $isoP = [int]$dayP.DayOfWeek; if ($isoP -eq 0) { $isoP = 7 }
Api POST "/api/v1/schedules/templates" @{ doctorId = $adminId; weekday = $isoP; startTime = '15:00'; endTime = '16:00'; slotMinutes = 30; capacity = 1 } $rec | Out-Null
$freeSlot = (Api GET "/api/v1/schedules/$adminId/slots?from=$dayPStr&to=$dayPStr" $null $rec).data[0].id
$urgent = (Api POST '/api/v1/schedules/waitlist' @{ patientId = $pat3; priority = 'urgent' } $rec).data
Api POST '/api/v1/schedules/waitlist' @{ patientId = $pat2; priority = 'routine' } $rec | Out-Null
$wl = (Api GET '/api/v1/schedules/waitlist' $null $rec).data
Check 'waitlist ordered urgent first' ($wl.Count -ge 2 -and $wl[0].id -eq $urgent.id -and $wl[0].priority -eq 'urgent')
$promoted = (Api POST "/api/v1/schedules/waitlist/$($urgent.id)/promote" @{ slotId = $freeSlot } $rec).data
$rePromote = Api POST "/api/v1/schedules/waitlist/$($urgent.id)/promote" @{ slotId = $freeSlot } $rec
Check 'waitlist promote books the slot (201, then 400)' ($null -ne $promoted.id -and $rePromote._status -eq 400)

# 13 teleconsult slot type books a telemedicine appointment
$dayT = ((Get-Date).AddDays(5)); $dayTStr = $dayT.ToString('yyyy-MM-dd'); $isoT = [int]$dayT.DayOfWeek; if ($isoT -eq 0) { $isoT = 7 }
Api POST "/api/v1/schedules/templates" @{ doctorId = $adminId; weekday = $isoT; sessionType = 'teleconsult'; startTime = '14:00'; endTime = '15:00'; slotMinutes = 30; capacity = 1 } $rec | Out-Null
$teleSlot = (Api GET "/api/v1/schedules/$adminId/slots?from=$dayTStr&to=$dayTStr" $null $rec).data[0].id
$tele = (Api POST '/api/v1/appointments' @{ slotId = $teleSlot; patientId = $pat2; appointmentType = 'OPD' } $rec).data
Check 'teleconsult slot -> telemedicine=true' ($tele.telemedicine)

# 14 legacy (no slot) booking + keyset cursor pagination
$nextDay = ((Get-Date).AddDays(1)).ToString('yyyy-MM-dd')
$legacy = (Api POST '/api/v1/appointments' @{ patientId = $pat3; doctorId = $adminId; appointmentType = 'OPD'; scheduledStart = "${nextDay}T12:00:00Z"; scheduledEnd = "${nextDay}T12:15:00Z"; durationMinutes = 15 } $rec).data
$page1 = Api GET "/api/v1/appointments?patientId=$pat3&limit=1" $null $rec
Check 'legacy booking + cursor page' ($null -ne $legacy.id -and $page1.data.Count -eq 1 -and $null -ne $page1.meta.nextCursor)

# 15 audit trail for the status change
Api PATCH "/api/v1/appointments/$($legacy.id)/status" @{ status = 'cancelled'; cancelledReason = 'demo' } $rec | Out-Null
$audit = Api GET "/api/v1/audit/logs?entityId=$($legacy.id)&limit=10" $null $admin
Check 'audit row for appointment write' ($audit.data.Count -ge 1)

# 16 catalog lists the appointment events
$catalog = Api GET '/api/v1/events/catalog' $null $null
$apptEvents = @($catalog.data | Where-Object { $_.name -like 'Appointment*' })
Check 'catalog carries appointment.* events' ($apptEvents.Count -ge 4)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
