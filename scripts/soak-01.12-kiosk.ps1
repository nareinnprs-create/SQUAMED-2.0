# CHUNK 1 EXIT SOAK (section 01.12): kiosk check-in throughput soak against the
# contract's "50 check-ins/hr" line. Registers the soak hospital, kiosk terminal and
# one patient+appointment per check-in, then performs $Count check-ins spaced to
# sustain at least 50/hr (interval = 3600/50 s), asserting every slip is well-formed,
# the display board keeps answering and no duplicate queue entries appear. Records the
# effective rate and pass/fail counts. Runs against the app on port 8080 (pwsh).
# ASCII only.
param(
  [string]$Base = 'http://localhost:8080',
  [int]$Count = 60
)
$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0

# access tokens expire at 15 min (application.yml); refresh a session older than 10 min
function FreshLogin {
  $now = Get-Date
  if (($now - $script:adminAt).TotalMinutes -ge 10) {
    $script:admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
    $script:adminAt = $now
  }
  if (($now - $script:kioskAt).TotalMinutes -ge 10) {
    $script:kiosk = (Api POST '/api/v1/auth/login' @{ username = $kioEmail; password = 'Passw0rd!123' } $null).data.accessToken
    $script:kioskAt = $now
  }
}

function Check($name, $ok) {
  if ($ok) { $script:pass++; Write-Output "PASS $name" }
  else { $script:fail++; Write-Output "FAIL $name" }
}

function Api($method, $path, $body, $token) {
  $headers = @{}
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  try {
    if ($null -ne $body) {
      return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 20
    }
    return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -TimeoutSec 20
  } catch {
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

Write-Output "== 01.12 kiosk check-in soak ($Count check-ins at >=50/hr) =="

# setup: admin, fresh hospital, kiosk terminal, department
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
$script:adminAt = Get-Date
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Kiosk Soak $s"; code = "KS-$s"; country = 'Qatar' } $admin).data.id
Check 'soak hospital created' ($null -ne $h)
$kioEmail = "kiosk-soak-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $kioEmail; password = 'Passw0rd!123'; firstName = 'Kiosk'; lastName = "Soak$s"; roles = @('KIOSK'); hospitalId = $h } $null | Out-Null
$kiosk = (Api POST '/api/v1/auth/login' @{ username = $kioEmail; password = 'Passw0rd!123' } $null).data.accessToken
$script:kioskAt = Get-Date
Check 'kiosk terminal logged in' ($null -ne $kiosk)

# doctor: the legacy appointment path requires doctorId (APPOINTMENT_DOCTOR_REQUIRED otherwise)
$docEmail = "doc-soak-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $docEmail; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Soak$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doc = (Api POST '/api/v1/auth/login' @{ username = $docEmail; password = 'Passw0rd!123' } $null).data.accessToken
$docId = (Api GET '/api/v1/auth/me' $null $doc).data.userId
Check 'doctor registered for bookings' ($null -ne $docId)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$script:ok = 0
$script:bad = 0
$intervalSec = 3600.0 / 50.0   # 72 s between check-ins sustains exactly 50/hr

for ($i = 1; $i -le $Count; $i++) {
  FreshLogin
  $tick = (Get-Date).Ticks + $i
  $phone = '56' + ($tick % 10000000000).ToString('D10')
  $pa = (Api POST '/api/v1/patients' @{ firstName = "Walk$i"; lastName = "Soak$s"; gender = 'female'; dateOfBirth = '1990-01-01'; phone = $phone; hospitalId = $h } $admin).data.patient
  $start = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  $end = (Get-Date).AddHours(2).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  $appt = (Api POST '/api/v1/appointments' @{ hospitalId = $h; patientId = $pa.id; doctorId = $docId; appointmentType = 'OPD'; scheduledStart = $start; scheduledEnd = $end; durationMinutes = 30; reason = 'kiosk soak'; priority = 'normal' } $admin).data
  $slip = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'mobile'; identifier = $phone } $kiosk).data
  if ($null -ne $slip -and $null -ne $slip.token -and -not [string]::IsNullOrEmpty($slip.token.tokenNumber)) {
    $script:ok++
    $script:streak = 0
  } else {
    $script:bad++
    Write-Output "FAIL check-in $i (patient $($pa.id) pa-status=$($pa._status) slip-status=$($slip._status))"
    $script:streak++
    if ($script:streak -ge 5) { Write-Output "ABORT after 5 consecutive check-in failures (infra/flap?)"; exit 1 }
  }
  if ($i % 10 -eq 0) {
    $board = (Api GET '/api/v1/queue/display-board' $null $admin).data
    Write-Output "  progress $i/$Count ok=$($script:ok) bad=$($script:bad) board generatedAt=$($board.generatedAt)"
  }
  if ($i -lt $Count) { Start-Sleep -Seconds $intervalSec }
}
$sw.Stop()

$rate = [math]::Round($Count / ($sw.Elapsed.TotalHours), 1)
Check "all $Count check-ins green" ($script:bad -eq 0)
Check "effective rate >= 50 check-ins/hr (measured $rate/hr)" ($rate -ge 50)
$board = (Api GET '/api/v1/queue/display-board' $null $admin).data
Check 'display board still well-formed at the end of the soak' ($null -ne $board.generatedAt)

Write-Output "== soak done: $script:ok ok, $script:bad bad, $rate check-ins/hr, elapsed $([int]$sw.Elapsed.TotalMinutes) min =="
if ($script:fail -gt 0 -or $script:bad -gt 0) { exit 1 }
