# Exit-demo slice for section 01.09 (Emergency fast-path + downtime pack v1):
# walk-in triage-lite tags (red/yellow/green) boosting the live queue, red firing
# queue.urgent_arrival + queue.bumped, call-next honoring the boost order, the
# downtime window lifecycle, paper-token re-entry that preserves written token
# numbers, paper check-in re-stamp, and a no-loss board check. Runs against the
# app on port 8080 (pwsh). ASCII only.
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

Write-Output "== 01.09 emergency fast-path + downtime pack demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital + receptionist (rides queue:manage:queue / queue:read:queue) + kiosk user (no queue manage grant)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h1 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "FastPath Demo $s"; code = "FP-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h1)
$recEmail = "rec-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Fast$s"; roles = @('RECEPTIONIST'); hospitalId = $h1 } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$kioskEmail = "kiosk-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $kioskEmail; password = 'Passw0rd!123'; firstName = 'Kiosk'; lastName = "Fast$s"; roles = @('KIOSK'); hospitalId = $h1 } $null | Out-Null
$kiosk = (Api POST '/api/v1/auth/login' @{ username = $kioskEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'receptionist and kiosk log in' ($null -ne $rec -and $null -ne $kiosk)

# 3 three walk-ins join today's consult queue as routine (C-01..C-03)
$stamp = (Get-Date).Ticks
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Arjun'; lastName = "Walkin$s"; gender = 'male'; dateOfBirth = '1980-01-01'; phone = ('55' + ($stamp % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
$pb = (Api POST '/api/v1/patients' @{ firstName = 'Beena'; lastName = "Walkin$s"; gender = 'female'; dateOfBirth = '1985-05-05'; phone = ('55' + (($stamp + 1) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
$pc = (Api POST '/api/v1/patients' @{ firstName = 'Chirag'; lastName = "Walkin$s"; gender = 'male'; dateOfBirth = '1990-09-09'; phone = ('55' + (($stamp + 2) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
Check 'walk-in patients created' ($null -ne $pa.id -and $null -ne $pb.id -and $null -ne $pc.id)
$eA = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa.id; patientName = 'Arjun'; hospitalId = $h1 } $admin).data
$eB = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pb.id; patientName = 'Beena'; hospitalId = $h1 } $admin).data
$eC = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pc.id; patientName = 'Chirag'; hospitalId = $h1 } $admin).data
$tA = $eA.tokenNumber
$tB = $eB.tokenNumber
$tC = $eC.tokenNumber
Check 'walk-ins queued C-01..C-03' ($tA -eq 'C-01' -and $tB -eq 'C-02' -and $tC -eq 'C-03')

# 4 triage-lite validation comes before any boost
Check 'missing tag rejected (TRIAGE_TAG_REQUIRED)' ((Api POST "/api/v1/queue/entries/consult/$tA/triage-lite" @{} $rec)._code -eq 'TRIAGE_TAG_REQUIRED')
Check 'unknown tag rejected (TRIAGE_TAG_INVALID)' ((Api POST "/api/v1/queue/entries/consult/$tA/triage-lite" @{ tag = 'purple' } $rec)._code -eq 'TRIAGE_TAG_INVALID')

# 5 yellow -> urgent, green -> stays routine, red -> emergency
$yB = (Api POST "/api/v1/queue/entries/consult/$tB/triage-lite" @{ tag = 'yellow' } $rec).data
$gC = (Api POST "/api/v1/queue/entries/consult/$tC/triage-lite" @{ tag = 'green' } $rec).data
$rA = (Api POST "/api/v1/queue/entries/consult/$tA/triage-lite" @{ tag = 'red' } $rec).data
Check 'yellow tag boosts to urgent' ($yB.priority -eq 'urgent')
Check 'green tag leaves routine' ($gC.priority -eq 'routine')
Check 'red tag boosts to emergency' ($rA.priority -eq 'emergency')

# 6 boost order on the board and at the counter
$board = @((Api GET '/api/v1/queue/board?queueType=consult' $null $rec).data)
$order = @($board | ForEach-Object { $_.tokenNumber })
Check 'red ranks before yellow before routine' (($order.IndexOf($tA) -lt $order.IndexOf($tB)) -and ($order.IndexOf($tB) -lt $order.IndexOf($tC)))
Check 'call-next pulls the red token first' ((Api POST '/api/v1/queue/entries/call-next' @{ queueType = 'consult' } $rec).data.tokenNumber -eq $tA)
Check 'call-next pulls the yellow token next' ((Api POST '/api/v1/queue/entries/call-next' @{ queueType = 'consult' } $rec).data.tokenNumber -eq $tB)

# 7 red fired queue.urgent_arrival (CriticalQueueEvent) and queue.bumped for displays
$catalog = @((Api GET '/api/v1/events/catalog' $null $admin).data)
Check 'CriticalQueueEvent cataloged EMITTED' (@($catalog | Where-Object { $_.name -eq 'CriticalQueueEvent' -and $_.status -eq 'EMITTED' }).Count -eq 1)
$outbox = @((Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin).data)
Check 'queue.urgent_arrival in the outbox' (@($outbox | Where-Object { $_.eventType -eq 'queue.urgent_arrival' -and $_.envelope.Contains($tA) }).Count -ge 1)
Check 'queue.bumped in the outbox' (@($outbox | Where-Object { $_.eventType -eq 'queue.bumped' -and $_.envelope.Contains($tA) }).Count -ge 1)

# 8 permission boundary: a role-less user cannot triage (queue:manage:queue required;
# KIOSK holds that grant by V59 for its own check-in endpoint, so it is not the negative case here)
$nopermEmail = "noperm-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $nopermEmail; password = 'Passw0rd!123'; firstName = 'No'; lastName = "Perm$s" } $null | Out-Null
$noperm = (Api POST '/api/v1/auth/login' @{ username = $nopermEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'role-less user cannot triage (403)' ((Api POST "/api/v1/queue/entries/consult/$tC/triage-lite" @{ tag = 'red' } $noperm)._status -eq 403)

# 9 downtime window: reason required, one open window per hospital
Check 'open without reason rejected (400)' ((Api POST '/api/v1/downtime/windows' @{ hospitalId = $h1 } $rec)._status -eq 400)
$window = (Api POST '/api/v1/downtime/windows' @{ reason = "PG outage drill $s"; hospitalId = $h1 } $rec).data
Check 'downtime window opens' ($null -ne $window.id -and $window.open)
Check 'second open window rejected (DOWNTIME_ALREADY_OPEN)' ((Api POST '/api/v1/downtime/windows' @{ reason = 'second'; hospitalId = $h1 } $rec)._code -eq 'DOWNTIME_ALREADY_OPEN')
Check 'window listed for the hospital' (@((Api GET '/api/v1/downtime/windows' $null $rec).data).Count -eq 1)

# 10 paper-token re-entry: written token numbers preserved, duplicate rejected
$pd = (Api POST '/api/v1/patients' @{ firstName = 'Devika'; lastName = "Paper$s"; gender = 'female'; dateOfBirth = '1983-03-03'; phone = ('55' + (($stamp + 3) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
$pe = (Api POST '/api/v1/patients' @{ firstName = 'Eshan'; lastName = "Paper$s"; gender = 'male'; dateOfBirth = '1987-07-07'; phone = ('55' + (($stamp + 4) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
Check 'paper patients created' ($null -ne $pd.id -and $null -ne $pe.id)
$reentry = (Api POST '/api/v1/downtime/reentries' @{
  hospitalId = $h1
  tokens = @(
    @{ queueType = 'consult'; tokenNumber = 'DT-01'; patientId = $pd.id; patientName = 'Devika' }
    @{ queueType = 'consult'; tokenNumber = 'DT-02'; patientId = $pe.id; patientName = 'Eshan'; priority = 'urgent' }
  )
} $rec).data
Check 'both paper tokens re-entered' (@($reentry.tokens).Count -eq 2)
Check 'paper token number DT-01 preserved' (@($reentry.tokens | Where-Object { $_.tokenNumber -eq 'DT-01' }).Count -eq 1)
Check 'untagged paper token defaults to routine' (@($reentry.tokens | Where-Object { $_.tokenNumber -eq 'DT-01' -and $_.priority -eq 'routine' }).Count -eq 1)
Check 'duplicate paper token rejected (DOWNTIME_TOKEN_EXISTS)' ((Api POST '/api/v1/downtime/reentries' @{
  hospitalId = $h1
  tokens = @(@{ queueType = 'consult'; tokenNumber = 'DT-01'; patientId = $pd.id })
} $rec)._code -eq 'DOWNTIME_TOKEN_EXISTS')

# 11 paper check-in: pre-booked visit re-stamped checked-in, idempotent
$pf = (Api POST '/api/v1/patients' @{ firstName = 'Farah'; lastName = "Paper$s"; gender = 'female'; dateOfBirth = '1991-11-11'; phone = ('55' + (($stamp + 5) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
$visit = (Api POST '/api/v1/reception/visits' @{ patientId = $pf.id; hospitalId = $h1; reason = 'pre-booked before outage' } $admin).data.visit
Check 'visit starts registered (paper check-in pending)' ($visit.status -eq 'registered')
Api POST '/api/v1/downtime/reentries' @{ hospitalId = $h1; visitIds = @($visit.id) } $rec | Out-Null
Check 'paper check-in restored (checked-in)' ((Api GET "/api/v1/reception/visits/$($visit.id)" $null $rec).data.status -eq 'checked-in')

# 12 close the window; reclose rejected; nothing lost on the board
$closed = (Api PATCH "/api/v1/downtime/windows/$($window.id)/close" @{} $rec).data
Check 'window closes with endedAt' (-not $closed.open -and $null -ne $closed.endedAt)
Check 'reclose rejected (DOWNTIME_NOT_OPEN)' ((Api PATCH "/api/v1/downtime/windows/$($window.id)/close" @{} $rec)._code -eq 'DOWNTIME_NOT_OPEN')
$boardAfter = @((Api GET '/api/v1/queue/board?queueType=consult' $null $rec).data)
$tokensAfter = @($boardAfter | ForEach-Object { $_.tokenNumber })
Check "nothing lost: paper + live tokens still on the board ($($tokensAfter.Count) rows)" (($tokensAfter -contains 'DT-01') -and ($tokensAfter -contains 'DT-02') -and ($tokensAfter -contains $tC))

Write-Output "== 01.09 demo: $($script:pass) passed, $($script:fail) failed =="
if ($script:fail -gt 0) { exit 1 }
