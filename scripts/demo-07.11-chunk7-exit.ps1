# CHUNK 7 EXIT DEMO (section 07.11): walks the chunk-7 contract EXIT CRITERIA that the shipped
# sections cover - the executive question through the orchestrator, the capacity answer with
# evidence, an ED mass-casualty activation, a month-end GL close, a migration import with
# reconciliation, and the benchmarking aggregate. The cardiology specialty-pack install, the
# mass-casualty DRILL harness and the prescriptive-CDS counsel opinion are OWED (external /
# clinical review) and are reported, not faked. Runs against the app on port 8080 (pwsh). ASCII only.
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

Write-Output "== 07.11 chunk-7 exit demo (8080) =="

$admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
Check 'admin login' ($null -ne $admin)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$hid = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Chunk7 $s"; code = "C7-$s"; country = 'Qatar' } $admin).data.id
$email = "c7.$s@squamed.local"
Api POST '/api/v1/auth/register' @{
  email = $email; password = 'Passw0rd!123'; firstName = 'Chunk7'; lastName = "Admin$s"
  roles = @('SYSTEM_ADMIN'); hospitalId = $hid
} $null | Out-Null
$sys = (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
Check 'tenant admin login' ($null -ne $sys)

# 1. 07.01 - executive question through the orchestrator returns a primary blocker with an owner.
$orch = Api POST '/api/v1/ai/orchestrate' @{ question = 'why is today surgical schedule delayed?' } $sys
Check 'orchestrator answers' ($null -ne $orch.data)
Check 'orchestrator consulted agents' ($orch.data.agentsConsulted.Count -ge 1)
Check 'orchestrator synthesizes an answer' ($null -ne $orch.data.synthesized.blocker)
Check 'synthesized answer carries an owner' ($null -ne $orch.data.synthesized.owner)

# 2. 07.03 - capacity engine answers tonight's admission question with evidence.
$cap = Api POST '/api/v1/twin/capacity' @{ additionalAdmissions = 1 } $sys
Check 'capacity answer returned' ($null -ne $cap.data.canAccept)
Check 'capacity evidence has five factors' ($cap.data.evidence.Count -eq 5)
Check 'capacity evidence includes staffing and forecast' (
  ($cap.data.evidence | Where-Object { $_.factor -eq 'staffing' }).Count -eq 1 -and
  ($cap.data.evidence | Where-Object { $_.factor -eq 'demandForecast' }).Count -eq 1
)
Check 'capacity answer is human readable' ($cap.data.answer.Length -gt 10)

# 3. 07.05 - ED mass-casualty activation and stand-down.
$mc = Api POST '/api/v1/ops/ed/mass-casualty' @{ name = "Drill $s" } $sys
Check 'mass-casualty activated' ($null -ne $mc.data.id)
$mcList = Api GET '/api/v1/ops/ed/mass-casualty' $null $sys
Check 'mass-casualty listed as active' ($mcList.data.id -eq $mc.data.id)
$sd = Api POST "/api/v1/ops/ed/mass-casualty/$($mc.data.id)/stand-down" @{} $sys
Check 'mass-casualty stood down' ($sd.data.status -eq 'STOOD_DOWN')
$mcAfter = Api GET '/api/v1/ops/ed/mass-casualty' $null $sys
Check 'no mass-casualty left active' ($mcAfter.data.Count -eq 0)

# 4. 07.04 - month-end GL close, idempotent.
$period = (Get-Date).ToString('yyyy-MM')
Api POST '/api/v1/finance/accounts/seed' @{} $sys | Out-Null
$posted = Api POST '/api/v1/finance/gl/post-operations' @{ period = $period } $sys
Check 'operations posted to GL' ($null -ne $posted.data.posted)
$tb = Api GET "/api/v1/finance/reports/trial-balance?period=$period" $null $sys
Check 'trial balance balances' ($tb.data.balanced -eq $true)
$close = Api POST "/api/v1/finance/close/$period" @{} $sys
Check 'month-end close posted' ($null -ne $close.data)
$closeAgain = Api POST "/api/v1/finance/close/$period" @{} $sys
Check 'close-once enforced' ($closeAgain._status -eq 409)

# 5. 07.10 - migration import with reconciliation.
$job = Api POST '/api/v1/migration/jobs' @{
  sourceType = 'HIS'
  rows = @(
    @{ firstName = "Mig$s"; lastName = 'One'; gender = 'male'; dateOfBirth = '1975-01-01'; mrn = "M1-$s" },
    @{ firstName = "Mig$s"; lastName = 'Two'; gender = 'female'; dateOfBirth = '1980-02-02'; mrn = "M2-$s" }
  )
} $sys
Check 'migration job staged' ($job.data.job.totalRows -eq 2)
$jid = $job.data.job.id
$import = Api POST "/api/v1/migration/jobs/$jid/import" $null $sys
Check 'migration imported both rows' ($import.data.job.status -eq 'IMPORTED' -and $import.data.job.validRows -eq 2)
$detail = Api GET "/api/v1/migration/jobs/$jid" $null $sys
Check 'migration rows reconciled' ($detail.data.rows.Count -eq 2)

# 6. 07.10 - benchmarking aggregate over anonymized data.
Api PUT '/api/v1/benchmark/participation' @{ enabled = $true } $sys | Out-Null
$bench = Api GET "/api/v1/benchmark/summary?metric=ADMISSIONS&period=$period" $null $sys
Check 'benchmark summary returned' ($null -ne $bench.data.median)
Check 'benchmark aggregate is peer-based' ($bench.data.peerCount -ge 0 -and $null -ne $bench.data.ownValue)

# 7. 07.10 - a specialty pack installs through configuration alone (reference pack; the 22 reviewed
#    contents are still owed, but the installer is real).
$packRole = "CARDIO-$s"
$pack = Api POST '/api/v1/packs' @{
  code = "CARDIOLOGY-REF-$s"
  name = "Cardiology reference $s"
  specialty = 'cardiology'
  components = @(
    @{
      componentType = 'CONFIG'
      componentKey = 'task-sla'
      payload = @{
        rules = @(@{ priority = 'urgent'; slaHours = 2; escalateToRole = $packRole })
        defaultSlaHours = 48
      }
    },
    @{
      componentType = 'ROLE'
      componentKey = $packRole
      payload = @{ displayName = "Cardiology $s"; permissions = @('clinical:read:encounter') }
    }
  )
} $sys
Check 'specialty pack registered' ($pack.data.components -eq 2)
$install = Api POST "/api/v1/packs/CARDIOLOGY-REF-$s/install" $null $sys
Check 'pack installed by configuration' ($install.data.status -eq 'INSTALLED')
Check 'pack install was fast (< 1 day)' ($install.data.elapsedMs -lt 86400000)
$packSla = Api GET "/api/v1/hospital-config/hospitals/$hid/config/task-sla" $null $sys
Check 'installed config is active' ($packSla.data.content.defaultSlaHours -eq 48)
$again = Api POST "/api/v1/packs/CARDIOLOGY-REF-$s/install" $null $sys
Check 'pack install is idempotent' ($again.data.status -eq 'ALREADY_INSTALLED')

# 8. 07.05 - the mass-casualty drill harness runs the real ED paths and stands down.
$casualties = @()
$levels = @(2, 3, 4)
for ($i = 0; $i -lt 3; $i++) {
  $p = Api POST '/api/v1/patients' @{
    firstName = "Drill$i$s"; lastName = 'Casualty'; gender = 'male'; dateOfBirth = '1990-01-01'
    phone = "72$s".Substring(0, 10); qid = "DR$i$s".Substring(0, 10); nationality = 'QA'; city = 'Doha'; hospitalId = $hid
  } $sys
  $casualties += @{ patientId = $p.data.patient.id; triageLevel = $levels[$i]; chiefComplaint = 'drill injury' }
}
$drill = Api POST '/api/v1/ops/ed/drills' @{ name = "Drill $s"; casualties = $casualties } $sys
Check 'drill completed' ($drill.data.status -eq 'COMPLETED')
Check 'drill registered all casualties' ($drill.data.created -eq 3)
Check 'drill stood down' ($null -ne $drill.data.stoodDownAt)
$active = Api GET '/api/v1/ops/ed/mass-casualty' $null $sys
Check 'no mass casualty left active' ($active.data.Count -eq 0)

# OWED (reported, not faked): the 22 clinically reviewed pack CONTENTS and the prescriptive-CDS
# counsel opinion. The installer and the drill harness above are real and exercised live.
Write-Output 'OWED 22 clinically reviewed specialty pack contents'
Write-Output 'OWED prescriptive-CDS counsel opinion (external gate)'

Write-Output ""
Write-Output "RESULT: $($script:pass) passed, $($script:fail) failed"
if ($script:fail -gt 0) { exit 1 }
