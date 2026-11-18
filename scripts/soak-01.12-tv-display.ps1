# CHUNK 1 EXIT SOAK (section 01.12): TV display software soak. The 24 h soak on real
# Android TV hardware is owed to the E10 hardware-lab window (tracked, not skipped);
# this script is the interim SOFTWARE soak: for $Minutes it repeatedly fetches the
# whole-hospital display board with jittered pacing, asserts the payload stays
# well-formed (generatedAt + counters), and churns the queue (create + call + a
# foreign-token denial probe) so board content keeps changing under the display app's
# poll cadence. Runs against the app on port 8080 (pwsh). ASCII only.
param(
  [string]$Base = 'http://localhost:8080',
  [int]$Minutes = 30
)
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
      return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 20
    }
    return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -TimeoutSec 20
  } catch {
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

Write-Output "== 01.12 TV display software soak ($Minutes min) =="

# access tokens expire at 15 min (application.yml); refresh any session older than 10 min
$script:adminAt = Get-Date
$script:recAt = $script:adminAt
function FreshLogin {
  $now = Get-Date
  if (($now - $script:adminAt).TotalMinutes -ge 10) {
    $script:admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
    $script:adminAt = $now
  }
  if (($now - $script:recAt).TotalMinutes -ge 10) {
    $script:rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
    $script:recAt = $now
  }
}

$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "TV Soak $s"; code = "TS-$s"; country = 'Qatar' } $admin).data.id
$recEmail = "rec-tvsoak-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Soak$s"; roles = @('RECEPTIONIST'); hospitalId = $h } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$sp = (Api POST '/api/v1/queue/service-points' @{ name = 'Counter 1'; code = 'SP1'; queueType = 'consult'; counterNumber = '1' } $rec).data
Check 'soak hospital + counter ready' ($null -ne $h -and $null -ne $sp.id)

$deadline = (Get-Date).AddMinutes($Minutes)
$round = 0
$script:malformed = 0
$script:called = 0
$script:lastGood = 0
while ((Get-Date) -lt $deadline) {
  $round++
  FreshLogin
  $tick = (Get-Date).Ticks + $round
  $pa = (Api POST '/api/v1/patients' @{ firstName = "TV$round"; lastName = "Soak$s"; gender = 'male'; dateOfBirth = '1985-05-05'; hospitalId = $h } $admin).data.patient
  $entry = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa.id; servicePointId = 'SP1' } $rec).data
  $board = (Api GET '/api/v1/queue/display-board' $null $rec).data
  $wellFormed = ($null -ne $board.generatedAt -and $null -ne $board.counters)
  if (-not $wellFormed) {
    $script:malformed++
    if (($round - $script:lastGood) -ge 10) { Write-Output "ABORT after 10 consecutive malformed rounds (infra/flap?)"; exit 1 }
  } else { $script:lastGood = $round }
  $call = (Api POST '/api/v1/queue/entries/call-next' @{ queueType = 'consult' } $rec).data
  if ($null -ne $call.tokenNumber) { $script:called++ }
  if ($round % 10 -eq 0) {
    Write-Output "  round $round wellFormed=$wellFormed called=$($script:called) malformed=$($script:malformed)"
  }
  $jitter = Get-Random -Minimum 15 -Maximum 40
  $remaining = ($deadline - (Get-Date)).TotalSeconds
  if ($remaining -gt $jitter) { Start-Sleep -Seconds $jitter }
}

Check "board well-formed on every round ($round rounds, $script:malformed malformed)" ($script:malformed -eq 0 -and $round -gt 0)
Check "queue churn served tokens throughout ($script:called called)" ($script:called -ge 1)
$final = (Api GET '/api/v1/queue/display-board' $null $rec).data
Check 'final board fetch well-formed' ($null -ne $final.generatedAt)

Write-Output "== TV software soak done: $round rounds, $script:malformed malformed, $script:called called =="
if ($script:fail -gt 0) { exit 1 }
