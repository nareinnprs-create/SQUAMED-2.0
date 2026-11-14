# Exit-demo slice for section 00.11 (Task engine): subject links, manual escalation,
# SLA rules from hospital config (task-sla doc), scheduled-monitor scan, inbox data path.
# Runs against the app on port 8081 (pwsh). ASCII only.
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

Write-Output "== 00.11 task engine demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Tasks Demo $s"; code = "TD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)

# 3 doctor registered (task:manage granted to DOCTOR in V50)
$email = "tasks-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Tasks$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doctor = (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
Check 'doctor login' ($null -ne $doctor)

# 4 subject: a patient to attach
$pat = (Api POST '/api/v1/patients' @{ firstName = 'Task'; lastName = "Demo$s"; gender = 'male'; dateOfBirth = '1990-01-01'; phone = "55$s"; qid = "7$s"; nationality = 'Qatar'; city = 'Doha' } $doctor).data.patient
$patId = $pat.id
Check 'patient created (subject)' ($null -ne $patId)

# 5 task with subject links (patient + department placeholder)
$dept = [guid]::NewGuid()
$task = (Api POST '/api/v1/tasks' @{ title = "Verify merged record $s"; priority = 'urgent'; assigneeId = (Api GET '/api/v1/auth/me' $null $doctor).data.userId; dueAt = (Get-Date).AddHours(-1).ToString('o'); links = @(@{ subjectType = 'patient'; subjectId = $patId }, @{ subjectType = 'department'; subjectId = $dept }) } $doctor).data
Check 'task with subject links' ($null -ne $task.id -and $task.links.Count -eq 2)

# 6 invalid subject type rejected
$bad = Api POST '/api/v1/tasks' @{ title = "bad $s"; links = @(@{ subjectType = 'banana'; subjectId = $patId }) } $doctor
Check 'invalid subject type rejected (400)' ($bad._status -eq 400)

# 7 lifecycle: assign -> execute -> escalate
$docId = (Api GET '/api/v1/auth/me' $null $doctor).data.userId
Api POST "/api/v1/tasks/$($task.id)/assign" @{ assigneeId = $docId } $doctor | Out-Null
Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'in_progress' } $doctor | Out-Null
$esc = (Api POST "/api/v1/tasks/$($task.id)/escalate" @{ reason = 'second opinion' } $doctor).data
Check 'manual escalation (escalated, count 1)' ($esc.status -eq 'escalated' -and $esc.escalationCount -eq 1)

# 8 task.escalated event in the outbox
Start-Sleep -Seconds 3
$outbox = Api GET '/api/v1/admin/events/outbox?limit=100' $null $admin
$escEvent = @($outbox.data | Where-Object { $_.eventType -eq 'task.escalated' -and $_.hospitalId -eq $h })
Check 'task.escalated in outbox' ($escEvent.Count -gt 0)

# 9 overdue monitor lists the past-due task
$overdue = Api GET '/api/v1/tasks/overdue' $null $doctor
Check 'overdue monitor lists past-due task' (@($overdue.data | Where-Object { $_.id -eq $task.id }).Count -eq 1)

# 10 SLA rules from config: urgent breaches instantly, escalation role = DOCTOR
Api PUT "/api/v1/hospital-config/hospitals/$h/config/task-sla" @{ rules = @(@{ priority = 'urgent'; slaHours = 0; escalateToRole = 'DOCTOR' }); defaultSlaHours = 72 } $admin | Out-Null
$urgent = (Api POST '/api/v1/tasks' @{ title = "SLA urgent $s"; priority = 'urgent'; assigneeId = $docId; hospitalId = $h } $doctor).data
$normal = (Api POST '/api/v1/tasks' @{ title = "SLA normal $s"; priority = 'normal'; assigneeId = $docId; hospitalId = $h } $doctor).data
$scan = (Api POST '/api/v1/tasks/sla/scan' @{} $doctor).data
Check 'SLA scan escalates config breach' ($scan.escalated -ge 1 -and (Api GET "/api/v1/tasks/$($urgent.id)" $null $doctor).data.status -eq 'escalated')

# 11 normal task stays under the default SLA
Check 'normal task under default SLA' ((Api GET "/api/v1/tasks/$($normal.id)" $null $doctor).data.status -eq 'pending')

# 12 doctor completes the escalated task
$done = (Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'completed' } $doctor).data
Check 'escalated task completes' ($done.status -eq 'completed' -and $null -ne $done.completedAt)

# 13 catalog lists TaskEscalated EMITTED (13 v1 types)
$catalog = Api GET '/api/v1/events/catalog' $null $null
$escalatedEntry = @($catalog.data | Where-Object { $_.name -eq 'TaskEscalated' -and $_.status -eq 'EMITTED' })
Check 'catalog TaskEscalated EMITTED (13 v1 types)' ($escalatedEntry.Count -eq 1 -and $catalog.data.Count -eq 13)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
