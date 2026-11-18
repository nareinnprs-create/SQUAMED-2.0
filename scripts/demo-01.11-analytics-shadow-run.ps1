# Exit-demo slice for section 01.11 (Analytics mini + shadow-run prep):
# a fresh pilot hospital books two OPD appointments, one completes and one is
# marked no-show, the appointments analytics read reports the exact no-show
# rate (50.0 over terminal outcomes), two queue entries populate the 01.02
# wait-time estimates so queue/overview avg wait is non-null, and a second
# fresh hospital reads zeros/null (tenant scoping). Runs against the app on
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
      return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 30
    }
    return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers -TimeoutSec 30
  } catch {
    $status = $_.Exception.Response.StatusCode.value__
    $code = $null
    try { $code = ($_.ErrorDetails.Message | ConvertFrom-Json).error.code } catch {}
    if (-not $code) {
      try {
        $stream = $_.Exception.Response.GetResponseStream()
        if ($stream) { $code = ([IO.StreamReader]$stream).ReadToEnd() | ConvertFrom-Json | ForEach-Object { $_.error.code } }
      } catch {}
    }
    return @{ _status = $status; _code = $code }
  }
}

Write-Output "== 01.11 analytics shadow-run demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)
$doctorId = (Api GET '/api/v1/auth/me' $null $admin).data.userId

# 2 fresh hospital + receptionist (books and marks outcomes) + a hospital-bound
#   SYSTEM_ADMIN reader (only SYSTEM_ADMIN carries report:read:report; binding it
#   to the hospital scopes its analytics reads to this tenant)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h1 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Shadow Demo $s"; code = "SD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h1)
$recEmail = "rec-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Shadow$s"; roles = @('RECEPTIONIST'); hospitalId = $h1 } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$readerEmail = "reader-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $readerEmail; password = 'Passw0rd!123'; firstName = 'Reader'; lastName = "Shadow$s"; roles = @('SYSTEM_ADMIN'); hospitalId = $h1 } $null | Out-Null
$reader = (Api POST '/api/v1/auth/login' @{ username = $readerEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'receptionist and bound reader log in' ($null -ne $rec -and $null -ne $reader)

# 3 doctor template for the demo day -> exactly two 30-minute slots
$day = (Get-Date).AddDays(3)
$dayStr = $day.ToString('yyyy-MM-dd')
$dow = [int]$day.DayOfWeek
if ($dow -eq 0) { $dow = 7 }
$template = (Api POST '/api/v1/schedules/templates' @{
  hospitalId = $h1
  doctorId = $doctorId
  weekday = $dow
  sessionType = 'in_person'
  startTime = '09:00'
  endTime = '10:00'
  slotMinutes = 30
  capacity = 1
} $admin).data
$templateId = $template.id
$slots = @((Api GET "/api/v1/schedules/$doctorId/slots?from=$dayStr&to=$dayStr" $null $rec).data | Where-Object { $_.templateId -eq $templateId })
Check 'template yields two slots' ($slots.Count -eq 2)

# 4 two patients, two bookings, one completed + one no-show
$stamp = (Get-Date).Ticks
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Arjun'; lastName = "Shadow$s"; gender = 'male'; dateOfBirth = '1980-01-01'; phone = ('55' + ($stamp % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
$pb = (Api POST '/api/v1/patients' @{ firstName = 'Beena'; lastName = "Shadow$s"; gender = 'female'; dateOfBirth = '1985-05-05'; phone = ('55' + (($stamp + 1) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
Check 'patients created' ($null -ne $pa.id -and $null -ne $pb.id)
$book = { param($slotId, $patientId)
  (Api POST '/api/v1/appointments' @{
    slotId = $slotId
    patientId = $patientId
    appointmentType = 'OPD'
    reason = 'shadow run'
    priority = 'routine'
    visitType = 'outpatient'
  } $rec).data
}
$a1 = & $book $slots[0].id $pa.id
$a2 = & $book $slots[1].id $pb.id
Check 'both bookings accepted (201)' ($null -ne $a1.id -and $null -ne $a2.id)
Api PATCH "/api/v1/appointments/$($a1.id)/status" @{ status = 'completed' } $rec | Out-Null
Api PATCH "/api/v1/appointments/$($a2.id)/status" @{ status = 'no-show' } $rec | Out-Null

# 5 appointments analytics: rate = no-show / (completed + no-show) = 50.0
$stats = (Api GET '/api/v1/analytics/appointments' $null $reader).data
Check 'fresh hospital sees only its own appointments (total=2)' ($stats.total -eq 2)
Check 'noShowRate exactly 50.0' ($stats.noShowRate -eq 50.0)
$completed = @($stats.byStatus | Where-Object { $_.name -eq 'completed' })[0]
$noShow = @($stats.byStatus | Where-Object { $_.name -eq 'no-show' })[0]
Check 'byStatus buckets completed=1 no-show=1' ($completed.count -eq 1 -and $noShow.count -eq 1)

# 6 queue entries seed the 01.02 wait-time estimates -> avg wait non-null
Api POST '/api/v1/queue/entries' @{ queueType = 'OPD'; patientId = $pa.id; patientName = 'Arjun'; priority = 'normal' } $rec | Out-Null
Api POST '/api/v1/queue/entries' @{ queueType = 'OPD'; patientId = $pb.id; patientName = 'Beena'; priority = 'normal' } $rec | Out-Null
$queue = (Api GET '/api/v1/analytics/queue' $null $reader).data
$overview = (Api GET '/api/v1/analytics/overview' $null $reader).data
Check 'queue avg wait populated' ($null -ne $queue.avgWaitMinutes)
Check 'overview avg wait populated' ($null -ne $overview.avgWaitMinutes)

# 7 cross-tenant scoping: a second fresh hospital reads only its own (empty) numbers
$h2 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Shadow Other $s"; code = "SO-$s"; country = 'Qatar' } $admin).data.id
$reader2Email = "reader2-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $reader2Email; password = 'Passw0rd!123'; firstName = 'Reader2'; lastName = "Shadow$s"; roles = @('SYSTEM_ADMIN'); hospitalId = $h2 } $null | Out-Null
$reader2 = (Api POST '/api/v1/auth/login' @{ username = $reader2Email; password = 'Passw0rd!123' } $null).data.accessToken
$foreign = (Api GET '/api/v1/analytics/appointments' $null $reader2).data
$foreignQueue = (Api GET '/api/v1/analytics/queue' $null $reader2).data
Check 'foreign hospital sees no appointments (total=0)' ($foreign.total -eq 0)
Check 'foreign noShowRate null (nothing resolved)' ($null -eq $foreign.noShowRate)
Check 'foreign queue avg wait null' ($null -eq $foreignQueue.avgWaitMinutes)

Write-Output "== 01.11 demo: $($script:pass) passed, $($script:fail) failed =="
if ($script:fail -gt 0) { exit 1 }
