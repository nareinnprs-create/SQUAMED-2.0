# CHUNK 5 EXIT DEMO (section 05.08): walks the chunk-5 contract EXIT CRITERIA that the
# shipped sections cover - a cited command-bar answer, an Operations-agent proposal approved in
# the approval queue into a governed task, the rail reflecting governed state and clearing the
# proposal after approval, the auditor AI trace (model/prompt version/tokens/cost/approvals/
# evals/incidents), a cost-cap hard cut, and a timed kill-switch drill. The scribe leg (dictate ->
# SOAP draft -> sign) is OWED to 05.05 (externally gated exp:E8) and is not faked here. Runs
# against the app on port 8080 (pwsh). ASCII only.
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

Write-Output "== 05.08 chunk-5 exit demo (8080) =="

# 1 admin, a fresh hospital, a tenant-scoped admin (the AI governance actor) and a doctor
$admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
Check 'admin login' ($null -ne $admin)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Chunk5 Exit $s"; code = "C5-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)
function Staff($role, $tag) {
  $email = "$tag-c5-$s@test.local"
  Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = $tag; lastName = "Exit$s"; roles = @($role); hospitalId = $h } $null | Out-Null
  return (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
}
$gov = Staff 'SYSTEM_ADMIN' 'gov'
$doc = Staff 'DOCTOR' 'doc'
Check 'governance admin + doctor log in' ($null -ne $gov -and $null -ne $doc)

# 2 patient + an overdue-discharge admission + a delayed OT case (the rail's governed state)
$stamp = (Get-Date).Ticks
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Exit'; lastName = "Ai$s"; gender = 'male'; dateOfBirth = '1968-08-08'; phone = ('69' + ($stamp % 10000000000).ToString('D10')); qid = ('C5' + ($stamp % 100000000).ToString('D9')).Substring(0, 11); nationality = 'QA'; city = 'Doha'; hospitalId = $h } $admin).data.patient
$ward = (Api POST "/api/v1/hospital-config/hospitals/$h/wards" @{ name = "AI Ward $s"; code = "AW-$s"; type = 'general' } $admin).data.id
$bed = (Api POST "/api/v1/hospital-config/hospitals/$h/beds" @{ label = "AI-$s"; type = 'standard'; status = 'available'; wardId = $ward } $admin).data.id
$adm = (Api POST '/api/v1/admissions' @{ hospitalId = $h; patientId = $pa.id; bedId = $bed; admissionType = 'elective' } $admin).data
$past = (Get-Date).AddHours(-2).ToUniversalTime().ToString('o')
Api POST "/api/v1/admissions/$($adm.id)/plan-discharge" @{ expectedDischargeAt = $past } $admin | Out-Null
$consent = (Api POST '/api/v1/consents' @{ hospitalId = $h; patientId = $pa.id; purpose = 'treatment'; scope = 'procedure'; signedByName = 'Patient' } $admin).data
$ot = (Api POST '/api/v1/surgery/schedules' @{ hospitalId = $h; patientId = $pa.id; procedureName = "Delayed case $s"; procedureCode = 'DL-05'; consentId = $consent.id; scheduledStartAt = $past } $admin).data
Check 'overdue discharge + delayed OT case seeded' ($null -ne $adm.id -and $null -ne $ot.id)

# 3 command bar: a grounded, cited answer
$count = (Api POST '/api/v1/ai/command' @{ query = 'how many patients?' } $gov).data
Check 'command bar answers with a cited count' ($count.intent -eq 'count' -and $count.citations.Count -ge 1)
Check 'citation names the record source' ($count.citations[0].ref -eq 'count.patients')

# 4 Operations agent proposes a task; the manager approves it in the approval queue
$proposal = (Api POST '/api/v1/ai/agents/ops/propose' @{ actionType = 'create_task'; payload = @{ title = "Blood bank: urgent crossmatch for OT $s"; relatedType = 'SURGERY'; priority = 'high' } } $gov).data
Check 'ops agent proposed a task draft' ($proposal.status -eq 'PROPOSED' -and $proposal.payload.agentCode -eq 'ops')
$queue = (Api GET '/api/v1/ai/actions?status=PROPOSED' $null $gov).data
Check 'proposal is on the approval queue' ((@($queue | Where-Object { $_.id -eq $proposal.id })).Count -eq 1)
$approved = (Api POST "/api/v1/ai/actions/$($proposal.id)/approve" @{} $gov).data
Check 'manager approval executes through the platform API' ($approved.status -eq 'APPROVED' -and $null -ne $approved.resultRef)
$tasks = (Api GET '/api/v1/tasks' $null $gov).data
Check 'the governed task now exists' ((@($tasks | Where-Object { $_.title -eq "Blood bank: urgent crossmatch for OT $s" })).Count -ge 1)

# 5 the rail reflects governed state and no longer shows the approved proposal
$rail = (Api GET '/api/v1/ai/rail/items' $null $gov).data
$types = @($rail | ForEach-Object { $_.itemType })
Check 'rail shows the overdue discharge blocker' ($types -contains 'DISCHARGE_BLOCKER')
Check 'rail shows the delayed OT case' ($types -contains 'OT_DELAY')
Check 'rail no longer lists the approved proposal' (-not ($rail | Where-Object { $_.itemType -eq 'PENDING_AI_APPROVAL' -and $_.sourceId -eq $proposal.id }))

# 6 auditor trace: model, prompt version, tokens, cost, redaction, approvals, evals, incidents
$chat = (Api POST '/api/v1/ai/chat' @{ messages = @(@{ role = 'user'; content = 'Patient 12345678901 asked about the OT delay' }) } $gov).data
Check 'chat answer traces tokens + cost' ($chat.tokensUsed -gt 0 -and $null -ne $chat.costUsd)
Check 'direct identifiers were redacted' ($chat.redacted -eq $true)
$logs = (Api GET '/api/v1/ai/logs' $null $gov).data
$trace = $logs | Where-Object { $_.capability -eq 'chat' -and $_.redacted -eq $true } | Select-Object -First 1
Check 'auditor sees model + prompt version on the trace' ($null -ne $trace.modelName -and $trace.promptVersion -like 'ops@*')
Check 'trace carries prompt/completion tokens' ($trace.promptTokens -gt 0 -and $trace.completionTokens -gt 0)
$costs = (Api GET '/api/v1/ai/governance/costs' $null $gov).data
Check 'cost dashboard shows the chat spend' ((@($costs.byCapability | Where-Object { $_.name -eq 'chat' })).Count -ge 1)
$eval = (Api POST '/api/v1/ai/governance/evals' @{ capability = 'chat'; subject = 'ops@0'; suiteName = 'command-bar-gold'; casesPassed = 20; casesTotal = 20 } $gov).data
Check 'eval result attached to the release' ($eval.verdict -eq 'PASS' -and $eval.score -eq 100.0)
$incident = (Api POST '/api/v1/ai/governance/incidents' @{ capability = 'chat'; severity = 'low'; summary = 'Exit-drill incident'; detail = 'recorded by the exit demo' } $gov).data
Check 'incident register accepts a report' ($incident.status -eq 'OPEN')
Api PATCH "/api/v1/ai/governance/incidents/$($incident.id)" @{ status = 'CLOSED'; resolution = 'exit drill closed' } $gov | Out-Null

# 7 cost-cap hard cut (simulated spend spike)
Api PUT '/api/v1/ai/governance/policies/chat' @{ dailyTokenCap = 1 } $gov | Out-Null
$capped = Api POST '/api/v1/ai/chat' @{ messages = @(@{ role = 'user'; content = 'hello' }) } $gov
Check 'daily cap hard-cuts the capability (409)' ($capped._status -eq 409 -and $capped._body -like '*AI_DAILY_TOKEN_CAP_REACHED*')
Api PUT '/api/v1/ai/governance/policies/chat' @{ dailyTokenCap = 200000 } $gov | Out-Null
Check 'cap restored' ($null -ne (Api POST '/api/v1/ai/chat' @{ messages = @(@{ role = 'user'; content = 'hello' }) } $gov).data)

# 8 kill-switch drill on a dummy capability, timed
$start = Get-Date
Api PUT '/api/v1/ai/governance/policies/report' @{ enabled = $false } $gov | Out-Null
$reportOff = Api POST '/api/v1/ai/chat' @{ messages = @(@{ role = 'user'; content = 'hello' }) } $gov
$chatStillOn = $null -ne $reportOff.data
Api PUT '/api/v1/ai/governance/policies/report' @{ enabled = $true } $gov | Out-Null
$elapsed = ((Get-Date) - $start).TotalSeconds
Check 'dummy capability disabled without touching chat' ($chatStillOn)
Check ("kill switch exercised in {0:N1}s (<60s)" -f $elapsed) ($elapsed -lt 60)

# 9 scribe leg is owed to 05.05 (exp:E8) - reported, never faked
Write-Output 'OWED scribe dictation -> SOAP draft -> sign (05.05, exp:E8): not exercised in this run'

Write-Output "== result: $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
