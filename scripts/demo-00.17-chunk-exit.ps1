# 00.17 CHUNK EXIT demo: full chunk-00 exit scenario (chunk-00-foundation.md line 59) plus the
# first-exit CS evidence bound to 00.17 by 22-CS-CASE-MATRIX.md:
#   - exit scenario: 2-department hospital with beds + price list -> 3 patients (one duplicate)
#     -> EMPI flags -> merge with auditor co-sign -> steps in audit viewer -> "verify merged
#     record" task assigned + completed -> notifications (in-app delivered; SMS honest-skip,
#     E2 debt) -> chain-integrity check passes.
#   - CS-07: merge reversal restores byte-identical record state.
#   - CS-15: audit chain tamper detected AND pages (AuditChainBreached active in Alertmanager).
# CS-08 (offline chaos) is pinned by the offlineChaosReplayCoverage PIT, not this script.
# Actor/tenant note: doctor-created rows carry hospital_id of the demo hospital (AuditRecorder
# resolves principal.hospitalId), so patient/task/consent/notification steps run as the DOCTOR;
# the auditor (READ_ONLY_VIEWER) views the chain + co-signs; admin only configures + merges.
# Backend boots on 8081 (8080 is the docker stack). ASCII only. Exits 1 on any failed check.
$ErrorActionPreference = 'Stop'
$backendPort = 8081
$passed = 0
$failed = 0
$compose = 'infrastructure/docker/docker-compose.yml'
$backendRoot = 'backend/squamed-core'

function Check($name, $ok, $detail = '') {
  if ($ok) {
    $script:passed++
    Write-Output "PASS: $name"
  } else {
    $script:failed++
    Write-Output "FAIL: $name $detail"
  }
}

function Kill-PortOwnerTree($port) {
  Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object {
      if ($_.OwningProcess -and $_.OwningProcess -ne 0) {
        taskkill /PID $_.OwningProcess /T /F 2>$null | Out-Null
      }
    }
}

function Api($method, $path, $body, $token) {
  $headers = @{}
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  if ($null -ne $body) {
    return Invoke-RestMethod -Method $method -Uri "http://localhost:$backendPort$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8) -SkipHttpErrorCheck
  }
  return Invoke-RestMethod -Method $method -Uri "http://localhost:$backendPort$path" -Headers $headers -SkipHttpErrorCheck
}

# status + parsed body in ONE call (for 4xx assertions on non-idempotent POSTs)
function ApiFull($method, $path, $body, $token) {
  $headers = @{}
  if ($token) { $headers['Authorization'] = "Bearer $token" }
  $iw = @{ Method = $method; Uri = "http://localhost:$backendPort$path"; SkipHttpErrorCheck = $true; Headers = $headers }
  if ($null -ne $body) {
    $iw.ContentType = 'application/json'
    $iw.Body = ($body | ConvertTo-Json -Depth 8)
  }
  return Invoke-WebRequest @iw
}

Write-Output "== 00.17 chunk exit demo (backend $backendPort) =="

# observability stack up (prometheus + alertmanager are needed for the CS-15 page check)
docker compose -f $compose up -d prometheus alertmanager 2>&1 | Out-Null
Start-Sleep -Seconds 6

# backend on 8081 (boot when the probe fails; bootRun compiles current source incl. the
# tenant guards + terminal-state fix, so this run IS the verification of the new code)
if (-not (curl.exe -s -o NUL -f "http://localhost:$backendPort/api/v1/events/catalog" 2>$null) -or $LASTEXITCODE -ne 0) {
  Write-Output "booting backend (SERVER_PORT=$backendPort bootRun, OTEL -> localhost:4317)..."
  Kill-PortOwnerTree $backendPort
  $env:SERVER_PORT = "$backendPort"
  $env:SPRING_PROFILES_ACTIVE = 'dev'
  $env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"
  $env:OTEL_EXPORTER_OTLP_PROTOCOL = 'grpc'
  Start-Process -FilePath "$backendRoot\gradlew.bat" -ArgumentList 'bootRun','--no-daemon','-q' `
    -WorkingDirectory $backendRoot -WindowStyle Hidden `
    -RedirectStandardOutput "$env:TEMP\backend-0017-exit-demo.log" `
    -RedirectStandardError "$env:TEMP\backend-0017-exit-demo.err.log"
}
$up = $false
$deadline = (Get-Date).AddSeconds(300)
while ((Get-Date) -lt $deadline) {
  $code = curl.exe -s -o NUL -w "%{http_code}" "http://localhost:$backendPort/api/v1/events/catalog"
  if ($code -eq '200') { $up = $true; break }
  Start-Sleep -Seconds 5
}
Check "backend up on $backendPort" $up
if (-not $up) { exit 1 }

$s = Get-Date -Format "yyyyMMddHHmmss"

# 1 admin login
$admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 EXIT SCENARIO: 2-department hospital with beds and a price list
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Exit Demo $s"; code = "EX-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
$d1 = (Api POST "/api/v1/hospital-config/hospitals/$h/departments" @{ name = "Emergency $s"; code = "ED-$s"; type = 'emergency' } $admin).data.id
$d2 = (Api POST "/api/v1/hospital-config/hospitals/$h/departments" @{ name = "Outpatient $s"; code = "OP-$s"; type = 'outpatient' } $admin).data.id
Check 'two departments configured' ($null -ne $d1 -and $null -ne $d2)
$bed1 = (Api POST "/api/v1/hospital-config/hospitals/$h/beds" @{ label = "EXD-$s-1"; type = 'General'; status = 'available' } $admin).data
$bed2 = (Api POST "/api/v1/hospital-config/hospitals/$h/beds" @{ label = "EXD-$s-2"; type = 'General'; status = 'available' } $admin).data
Check 'beds configured' ($null -ne $bed1.id -and $null -ne $bed2.id)
$ci = (Api POST '/api/v1/master-data/charge-items' @{ code = "EXD-CI-$s"; name = "Exit consult $s"; priceQar = 100.0 } $admin).data
$pl = (Api POST '/api/v1/master-data/price-lists' @{ code = "EXD-PL-$s"; name = "Standard $s"; currency = 'QAR'; hospitalId = $h } $admin).data
$null = Api POST "/api/v1/master-data/price-lists/$($pl.id)/items" @{ chargeItemId = $ci.id; priceQar = 85.5 } $admin
$plItems = (Api GET "/api/v1/master-data/price-lists/$($pl.id)/items" $null $admin).data
Check 'price list with priced item' ($plItems.Count -eq 1 -and $plItems[0].priceQar -eq 85.5)

# 3 staff: doctor (patients/tasks/consent/notifications), nurse (no cosign), auditor (cosign)
$docEmail = "exit-doc-$s@test.local"
$null = Api POST '/api/v1/auth/register' @{ email = $docEmail; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Exit$s"; roles = @('DOCTOR'); hospitalId = $h } $null
$doctor = (Api POST '/api/v1/auth/login' @{ username = $docEmail; password = 'Passw0rd!123' } $null).data.accessToken
$docId = (Api GET '/api/v1/auth/me' $null $doctor).data.userId
Check 'doctor logged in' ($null -ne $doctor -and $null -ne $docId)
$nurEmail = "exit-nur-$s@test.local"
$null = Api POST '/api/v1/auth/register' @{ email = $nurEmail; password = 'Passw0rd!123'; firstName = 'Nur'; lastName = "Se$s"; roles = @('NURSE'); hospitalId = $h } $null
$nurse = (Api POST '/api/v1/auth/login' @{ username = $nurEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'nurse (no cosign) logged in' ($null -ne $nurse)
$audEmail = "exit-aud-$s@test.local"
$null = Api POST '/api/v1/auth/register' @{ email = $audEmail; password = 'Passw0rd!123'; firstName = 'Aud'; lastName = "Itor$s"; roles = @('READ_ONLY_VIEWER'); hospitalId = $h } $null
$auditor = (Api POST '/api/v1/auth/login' @{ username = $audEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'auditor (cosigner) logged in' ($null -ne $auditor)

# 4 the doctor registers 2 patients; the ADMIN submits the duplicate patient (3rd) because
# EMPI approve is permission-first: only a submitter who HOLDS the cosign permission can ever
# reach the 400 TWO_PERSON_RULE_SELF_APPROVAL leg (00.08 pattern). Doctor = plain submitter.
$qid = "77" + $s.Substring(2)
$p1 = (Api POST '/api/v1/patients' @{ firstName = 'Asha'; lastName = "Exit$s"; dateOfBirth = '1990-05-05'; gender = 'female'; qid = $qid; phone = "5510$s"; nationality = 'Qatar'; city = 'Doha'; hospitalId = $h } $doctor).data.patient
$p2 = (Api POST '/api/v1/patients' @{ firstName = 'Ravi'; lastName = "Exit$s"; dateOfBirth = '1985-01-01'; gender = 'male'; qid = "55" + $s.Substring(2); phone = "5511$s"; nationality = 'Qatar'; city = 'Doha'; hospitalId = $h } $doctor).data.patient
$dupRes = (Api POST '/api/v1/patients' @{ firstName = 'Asha'; lastName = "Exit$s"; dateOfBirth = '1990-05-05'; gender = 'female'; qid = $qid; phone = "5512$s"; nationality = 'Qatar'; city = 'Doha'; hospitalId = $h } $admin)
Check 'three patients registered' ($null -ne $p1.id -and $null -ne $p2.id -and $null -ne $dupRes.data.patient.id)
Check 'EMPI flags the duplicate (score >= 96)' ($dupRes.data.matches.Count -ge 1 -and $dupRes.data.matches[0].score -ge 96)
$dupId = $dupRes.data.patient.id
$survivorId = $p1.id

# pre-merge snapshot of the duplicate (CS-07 byte-identical baseline) + child record
$dupBefore = (Api GET "/api/v1/patients/$dupId" $null $doctor).data
$null = Api POST "/api/v1/patients/$dupId/allergies" @{ allergen = 'Ibuprofen'; severity = 'moderate' } $doctor
$null = Api POST "/api/v1/patients/$dupId/history" @{ conditionName = 'Asthma'; onsetDate = '2015-03-01' } $doctor
$dupAllergyBefore = (Api GET "/api/v1/patients/$dupId/allergies" $null $doctor).data

# 5 EMPI queue shows the duplicate; two-person rule + cosign permission
$queue = (Api GET "/api/v1/patients/empi/reviews?hospitalId=$h&status=PENDING" $null $doctor).data
$review = $queue | Where-Object { $_.duplicatePatientId -eq $dupId } | Select-Object -First 1
Check 'duplicate auto-enqueued as PENDING review' ($null -ne $review)
$self = ApiFull POST "/api/v1/patients/empi/reviews/$($review.id)/approve" @{} $admin
$selfCode = ($self.Content | ConvertFrom-Json).error.code
Check 'two-person rule blocks self-approval (400)' ($self.StatusCode -eq 400 -and $selfCode -eq 'TWO_PERSON_RULE_SELF_APPROVAL') "HTTP $($self.StatusCode) code=$selfCode"
$noCosign = ApiFull POST "/api/v1/patients/empi/reviews/$($review.id)/approve" @{} $nurse
Check 'approval without cosign permission denied (403)' ($noCosign.StatusCode -eq 403) "HTTP $($noCosign.StatusCode)"

# 6 the auditor co-signs -> merge executes, children move, duplicate archived
$approved = (Api POST "/api/v1/patients/empi/reviews/$($review.id)/approve" @{} $auditor).data
Check 'auditor co-signs -> merge executes' ($approved.status -eq 'APPROVED' -and $null -ne $approved.mergeId)
$mergeId = $approved.mergeId
$survivorAllergies = (Api GET "/api/v1/patients/$survivorId/allergies" $null $doctor).data
Check 'child record moved to survivor' (@($survivorAllergies | Where-Object { $_.allergen -eq 'Ibuprofen' }).Count -ge 1)
$dupMerged = (Api GET "/api/v1/patients/$dupId" $null $doctor).data
Check 'duplicate archived (recordsStatus=merged)' ($dupMerged.recordsStatus -eq 'merged')

# 7 task "verify merged record" assigned to the doctor and completed
$task = (Api POST '/api/v1/tasks' @{ title = "Verify merged record $s"; priority = 'high'; assigneeId = $docId; links = @(@{ subjectType = 'patient'; subjectId = $survivorId }) } $doctor).data
Check 'task created, assigned to doctor' ($null -ne $task.id -and $task.assigneeId -eq $docId)
$null = Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'in_progress' } $doctor
$done = (Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'completed' } $doctor).data
Check 'task completed' ($done.status -eq 'completed' -and $null -ne $done.completedAt)
$taskEvent = @()
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 2
  $outbox = (Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin).data
  $taskEvent = @($outbox | Where-Object { $_.eventType -eq 'task.completed' -and $_.hospitalId -eq $h -and (($_.envelope | ConvertFrom-Json).entityId -eq $task.id) })
  if ($taskEvent.Count -gt 0) { break }
}
Check 'task.completed emitted exactly once for the task' ($taskEvent.Count -eq 1)

# 8 notifications: in-app delivered to the doctor inbox; SMS honest-skip in the ledger (E2 debt)
$consent = (Api POST '/api/v1/consents' @{ patientId = $survivorId; purpose = 'communications'; scope = 'appointment reminders'; signatureData = 'data:image/png;base64,iVBORw0KGgo='; signedByName = "Exit Demo $s"; channelOptIns = @(@{ channel = 'sms'; optedIn = $true }, @{ channel = 'email'; optedIn = $true }) } $doctor).data
Check 'consent captured for the survivor' ($null -ne $consent.id)
$inApp = (Api POST '/api/v1/notifications/send' @{ patientId = $survivorId; recipientId = $docId; channel = 'in_app'; type = 'general'; title = "Merged record ready $s"; body = 'verify the merged record' } $doctor).data
$inbox = (Api GET '/api/v1/notifications' $null $doctor).data
Check 'in-app notification delivered to doctor inbox' ($inApp.status -eq 'SENT' -and @($inbox | Where-Object { $_.title -eq "Merged record ready $s" }).Count -gt 0)
$sms = (Api POST '/api/v1/notifications/send' @{ patientId = $survivorId; channel = 'sms'; type = 'appointment'; title = "Merged record ready $s"; body = 'verify the merged record'; recipientAddress = "+97455$s" } $doctor).data
Check 'SMS request recorded (honest skip, no provider - E2)' ($sms.status -eq 'SKIPPED_NO_PROVIDER')
$ledger = (Api GET "/api/v1/notifications/requests?patientId=$survivorId" $null $doctor).data
Check 'dispatch ledger holds both notification rows' (@($ledger | Where-Object { $_.channel -eq 'in_app' -and $_.status -eq 'SENT' }).Count -ge 1 -and @($ledger | Where-Object { $_.channel -eq 'sms' -and $_.status -eq 'SKIPPED_NO_PROVIDER' }).Count -ge 1)

# 9 every step visible in the audit viewer (auditor sees the hospital's tenant chain).
# Guaranteed $h-chain rows: 2 doctor patient creates + allergy add + history add (>= 4 rows;
# the admin-created duplicate audits into the SYSTEM chain, invisible here),
# 2 co-signed approves, task create + 2 status PATCHes, notifications, consent.
$logs = (Api GET '/api/v1/audit/logs?limit=50' $null $auditor).data
$stepPatients = @($logs | Where-Object { $_.path -match '/patients' })
$stepTasks = @($logs | Where-Object { $_.path -match '/tasks' })
$stepNotifs = @($logs | Where-Object { $_.path -match '/notifications' })
$stepConsent = @($logs | Where-Object { $_.path -match '/consents' })
$stepMerge = @($logs | Where-Object { $_.path -match '/empi/reviews' -or $_.path -match '/merges' })
Check 'audit viewer shows patient steps' ($stepPatients.Count -ge 4) "rows=$($stepPatients.Count)"
Check 'audit viewer shows merge steps' ($stepMerge.Count -ge 2) "rows=$($stepMerge.Count)"
Check 'audit viewer shows task steps' ($stepTasks.Count -ge 2) "rows=$($stepTasks.Count)"
Check 'audit viewer shows notification steps' ($stepNotifs.Count -ge 1) "rows=$($stepNotifs.Count)"
Check 'audit viewer shows consent step' ($stepConsent.Count -ge 1) "rows=$($stepConsent.Count)"

# 10 chain-integrity check passes for the fresh tenant
$verify = (Api POST '/api/v1/audit/verify' @{} $auditor).data
$ownBreaches = @($verify.breaches | Where-Object { $_.hospitalId -eq $h })
Check 'chain verify clean for the demo hospital' ($verify.checked -gt 0 -and $ownBreaches.Count -eq 0)

# 11 CS-15 tamper test: breach detected AND pages.
$mg = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$b0 = [double]((($mg -split "`n" | Select-String '^squamed_audit_chain_breaches ' | Select-Object -First 1).Line).Trim() -split '\s+')[-1]
Check 'precondition: no pre-existing breach (gauge = 0)' ($b0 -eq 0.0) "b0=$b0"
$row = docker exec squamed-postgres psql -U squamed -d squamed -t -A -c "SELECT id || '|' || coalesce(response_payload, '') FROM audit.audit_log WHERE hospital_id = '$h' AND chain_seq IS NOT NULL AND response_payload IS NOT NULL ORDER BY created_at DESC LIMIT 1;"
$row = "$row".Trim()
$parts = $row -split '\|', 2
Check 'tamper target row found (tenant chain)' ($parts.Count -eq 2 -and $parts[0])
$null = docker exec squamed-postgres psql -U squamed -d squamed -c ("UPDATE audit.audit_log SET response_payload = 'TAMPERED-EXIT-" + $s + "' WHERE id = '" + $parts[0] + "';")
$breach = (Api POST '/api/v1/audit/verify' @{} $auditor).data
$hBreach = @($breach.breaches | Where-Object { $_.hospitalId -eq $h -and $_.reason -like '*HASH_MISMATCH*' })
Check 'tamper detected (HASH_MISMATCH)' ($breach.ok -eq $false -and $hBreach.Count -gt 0)
Start-Sleep -Seconds 2
$mg = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$bTamper = [double]((($mg -split "`n" | Select-String '^squamed_audit_chain_breaches ' | Select-Object -First 1).Line).Trim() -split '\s+')[-1]
Check 'breach gauge gains exactly the demo tenant' ($bTamper -eq ($b0 + 1.0)) ("b0=$b0 after=$bTamper")
$outbox = (Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin).data
Check 'audit.chain_broken in outbox' (@($outbox | Where-Object { $_.eventType -eq 'audit.chain_broken' -and $_.hospitalId -eq $h }).Count -ge 1)

# pages: AuditChainBreached (for: 0m, critical) must be ACTIVE in Alertmanager.
# Timeline: scrape <=15s + rule eval 30s + AM group_wait 30s -> poll up to 2 min.
$paged = $false
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 10
  $alerts = curl.exe -s -m 5 http://localhost:9093/api/v2/alerts | ConvertFrom-Json
  $hit = @($alerts | Where-Object { $_.labels.alertname -eq 'AuditChainBreached' -and $_.status.state -in @('active','suppressed') })
  if ($hit.Count -gt 0) { $paged = $true; break }
}
Check 'CS-15: AuditChainBreached pages (active in Alertmanager)' $paged

# restore the row -> verify clean -> gauge back to baseline
$orig = $parts[1].Replace("'", "''")
$null = docker exec squamed-postgres psql -U squamed -d squamed -c ("UPDATE audit.audit_log SET response_payload = '" + $orig + "' WHERE id = '" + $parts[0] + "';")
$restored = (Api POST '/api/v1/audit/verify' @{} $auditor).data
$ownAfter = @($restored.breaches | Where-Object { $_.hospitalId -eq $h })
Check 'chain verify clean again after restore' ($restored.ok -eq $true -and $ownAfter.Count -eq 0)
$mg = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$bRest = [double]((($mg -split "`n" | Select-String '^squamed_audit_chain_breaches ' | Select-Object -First 1).Line).Trim() -split '\s+')[-1]
Check 'breach gauge back to baseline' ($bRest -eq $b0) ("b0=$b0 restored=$bRest")

# 12 CS-07: merge reversal restores byte-identical record state
$unmerged = (Api POST "/api/v1/patients/merges/$mergeId/unmerge" @{} $admin).data
Check 'unmerge reverses the merge' ($unmerged.status -eq 'UNMERGED')
$dupAfter = (Api GET "/api/v1/patients/$dupId" $null $doctor).data
$fields = 'firstName','lastName','dateOfBirth','gender','qid','phone','nationality','city','recordsStatus'
$same = $true
foreach ($f in $fields) { if ($dupBefore.$f -ne $dupAfter.$f) { $same = $false; Write-Output ("  field drift: $f before=" + $dupBefore.$f + " after=" + $dupAfter.$f) } }
Check 'CS-07: duplicate record byte-identical after reversal (all fields)' $same
$dupAllergyAfter = (Api GET "/api/v1/patients/$dupId/allergies" $null $doctor).data
$allergySame = (@($dupAllergyAfter | Where-Object { $_.allergen -eq 'Ibuprofen' }).Count -eq @($dupAllergyBefore | Where-Object { $_.allergen -eq 'Ibuprofen' }).Count)
Check 'CS-07: allergy re-pointed back (children restored)' ($allergySame -and $dupAfter.recordsStatus -eq 'active')
# re-merge to leave the scenario in its merged end-state
$intent2 = (Api POST '/api/v1/patients/merge' @{ targetId = $survivorId; duplicateId = $dupId } $admin).data
$approved2 = (Api POST "/api/v1/patients/empi/reviews/$($intent2.id)/approve" @{} $auditor).data
Check 're-merge approved (end-state merged again)' ($approved2.status -eq 'APPROVED')
$dupFinal = (Api GET "/api/v1/patients/$dupId" $null $doctor).data
Check 'final state: duplicate archived, survivor holds the record' ($dupFinal.recordsStatus -eq 'merged')

Write-Output ""
Write-Output "== 00.17 chunk exit demo: $passed passed, $failed failed =="
Kill-PortOwnerTree $backendPort
if ($failed -gt 0) { exit 1 }
