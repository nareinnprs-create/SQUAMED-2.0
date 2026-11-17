# Exit-demo slice for section 01.07 (Voice announcements): zones bound to service
# points, announcement rules (repeat + languages + zone), call-time fan-out of
# announcement.enqueued via the event outbox (repeat doubling for emergency,
# counter resolution from the service-point name, zone volume), manual enqueue,
# no-rule defaults, cross-tenant denials and validation guards. Runs against the
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
      return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers
  } catch {
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

function OutboxRow($admin, $eventType, $needle) {
  $rows = (Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin).data
  return $rows | Where-Object { $_.eventType -eq $eventType -and $_.envelope.Contains($needle) } | Select-Object -First 1
}

Write-Output "== 01.07 Voice announcements demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 two fresh hospitals + one receptionist each (foreign receptionist for tenancy)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h1 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "VA Demo $s"; code = "VA-$s"; country = 'Qatar' } $admin).data.id
$hF = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "VA F $s"; code = "VAF-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h1 -and $null -ne $hF)
$rec1Email = "rec1-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $rec1Email; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "One$s"; roles = @('RECEPTIONIST'); hospitalId = $h1 } $null | Out-Null
$rec1 = (Api POST '/api/v1/auth/login' @{ username = $rec1Email; password = 'Passw0rd!123' } $null).data.accessToken
$recFEmail = "recF-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recFEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Foreign$s"; roles = @('RECEPTIONIST'); hospitalId = $hF } $null | Out-Null
$recF = (Api POST '/api/v1/auth/login' @{ username = $recFEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'both receptionists log in' ($null -ne $rec1 -and $null -ne $recF)

# 3 counter + audio zone bound to it + rule (repeat 3, en,hi, scoped to the zone)
$sp = (Api POST '/api/v1/queue/service-points' @{ name = 'Counter 1'; code = 'SP1'; queueType = 'consult'; counterNumber = '1' } $rec1).data
Check 'service point created' ($sp.name -eq 'Counter 1' -and $sp.code -eq 'SP1')
$zone = (Api POST '/api/v1/queue/zones' @{ name = 'Main Display'; servicePointId = 'SP1'; volume = 90 } $rec1).data
Check 'zone created with volume' ($null -ne $zone.id -and $zone.name -eq 'Main Display' -and $zone.volume -eq 90)
$rule = (Api POST '/api/v1/queue/announcement-rules' @{ queueType = 'consult'; repeatCount = 3; languages = 'en,hi'; zoneId = $zone.id } $rec1).data
Check 'rule created (repeat 3, en,hi, zone)' ($rule.repeatCount -eq 3 -and $rule.languages -eq 'en,hi' -and $rule.zoneId -eq $zone.id)

# 4 add patient to the consult queue at Counter 1 and call the token
# patients are created by admin (RECEPTIONIST lacks patient:create -> 403); entries/rules/zones stay on receptionist tokens
$pa = (Api POST '/api/v1/patients' @{ firstName = 'VA'; lastName = "Demo$s"; gender = 'male'; dateOfBirth = '1990-07-07'; hospitalId = $h1 } $admin).data.patient
$entry = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa.id; servicePointId = 'SP1' } $rec1).data
$token1 = $entry.tokenNumber
Check 'entry added with token' ($null -ne $token1)
$call = Api PATCH "/api/v1/queue/entries/consult/$token1/call" $null $rec1
Check 'token called' ($call.data.status -eq 'called')

# 5 announcement.enqueued in the outbox: rule + zone resolution applied
$env1 = ((OutboxRow $admin 'announcement.enqueued' $token1).envelope | ConvertFrom-Json)
Check 'announcement.enqueued emitted' ($env1.eventType -eq 'announcement.enqueued' -and $env1.source -eq 'announcement-service' -and $env1.entityType -eq 'queue-entry')
Check 'rule repeat 3 applied' ($env1.payload.repeatCount -eq 3)
Check 'rule zone scoped the announcement' ($env1.payload.zoneId -eq $zone.id -and $env1.payload.zoneName -eq 'Main Display')
Check 'counter resolved from service-point name' ($env1.payload.counter -eq 'Counter 1')
Check 'zone volume 90 applied' ($env1.payload.volume -eq 90)
Check 'languages en,hi drive TTS texts' ($env1.payload.languages.Count -eq 2 -and $env1.payload.texts.en.Contains($token1) -and $env1.payload.texts.hi.Contains($token1))

# 6 queue.called enriched for displays
$envC = ((OutboxRow $admin 'queue.called' $token1).envelope | ConvertFrom-Json)
Check 'queue.called carries counter + priority' ($envC.payload.servicePointName -eq 'Counter 1' -and $envC.payload.servicePointId -eq 'SP1' -and $envC.payload.priority -eq 'routine')

# 7 emergency call doubles the rule repeat (3 -> 6)
$pa2 = (Api POST '/api/v1/patients' @{ firstName = 'VA'; lastName = "Emerg$s"; gender = 'female'; dateOfBirth = '1985-02-02'; hospitalId = $h1 } $admin).data.patient
$entry2 = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa2.id; priority = 'emergency'; servicePointId = 'SP1' } $rec1).data
$token2 = $entry2.tokenNumber
Api PATCH "/api/v1/queue/entries/consult/$token2/call" $null $rec1 | Out-Null
$env2 = ((OutboxRow $admin 'announcement.enqueued' $token2).envelope | ConvertFrom-Json)
Check 'emergency doubles repeat (3 -> 6)' ($env2.payload.repeatCount -eq 6 -and $env2.payload.priority -eq 'emergency')

# 8 manual enqueue: default repeat 2, zone volume applied, texts echoed
$manual = (Api POST '/api/v1/queue/announcements' @{ texts = @{ en = 'Please proceed to the pharmacy' }; zoneId = $zone.id } $rec1).data
Check 'manual enqueue defaults + echo' ($manual.repeatCount -eq 2 -and $manual.zoneName -eq 'Main Display' -and $manual.volume -eq 90 -and $manual.texts.en -eq 'Please proceed to the pharmacy')

# 9 no-rule hospital: defaults (repeat 2, en+hi, every zone, volume 80, counter = queue type)
$paF = (Api POST '/api/v1/patients' @{ firstName = 'VA'; lastName = "Foreign$s"; gender = 'male'; dateOfBirth = '1991-03-03'; hospitalId = $hF } $admin).data.patient
$entryF = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $paF.id } $recF).data
$tokenF = $entryF.tokenNumber
Api PATCH "/api/v1/queue/entries/consult/$tokenF/call" $null $recF | Out-Null
$envF = ((OutboxRow $admin 'announcement.enqueued' $tokenF).envelope | ConvertFrom-Json)
Check 'no-rule defaults (repeat 2, en+hi, volume 80)' ($envF.payload.repeatCount -eq 2 -and $envF.payload.languages.Count -eq 2 -and $envF.payload.volume -eq 80)
Check 'no zone bound -> announced in every zone' ($null -eq $envF.payload.PSObject.Properties['zoneId'] -and $null -eq $envF.payload.PSObject.Properties['zoneName'])
Check 'counter falls back to queue type' ($envF.payload.counter -eq 'consult')

# 10 tenant isolation: zones/rules/enqueue are hospital-scoped
Check 'foreign zone list empty' (((Api GET '/api/v1/queue/zones' $null $recF).data).Count -eq 0)
Check 'foreign rule referencing our zone 400' ((Api POST '/api/v1/queue/announcement-rules' @{ queueType = 'consult'; zoneId = $zone.id } $recF)._status -eq 400)
Check 'foreign enqueue into our zone 400' ((Api POST '/api/v1/queue/announcements' @{ texts = @{ en = 'x' }; zoneId = $zone.id } $recF)._status -eq 400)

# 11 validation guards
Check 'zone name required 400' ((Api POST '/api/v1/queue/zones' @{ servicePointId = 'SP1' } $rec1)._status -eq 400)
Check 'zone volume 0-100 enforced' ((Api POST '/api/v1/queue/zones' @{ name = 'Loud'; volume = 150 } $rec1)._status -eq 400)
Check 'rule repeat 1-10 enforced' ((Api POST '/api/v1/queue/announcement-rules' @{ queueType = 'consult'; repeatCount = 11 } $rec1)._status -eq 400)
Check 'announcement text required' ((Api POST '/api/v1/queue/announcements' @{ texts = @{ en = ' ' } } $rec1)._status -eq 400)
Check 'enqueue repeat 1-10 enforced' ((Api POST '/api/v1/queue/announcements' @{ texts = @{ en = 'x' }; repeatCount = 0 } $rec1)._status -eq 400)

Write-Output "== 01.07 demo: $($script:pass) passed, $($script:fail) failed =="
if ($script:fail -gt 0) { exit 1 }
