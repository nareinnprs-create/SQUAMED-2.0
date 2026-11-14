# Exit-demo slice for section 00.13 (Offline groundwork): server-side idempotency replay
# proof for the offline store-and-forward path (task completion with a client idempotency
# key replays byte-identical, exactly one task.completed event, 409 on payload drift),
# plus the web offline shell (service worker + /offline fallback page).
# Runs against the API on 8081 and the built web app on 3000 (pwsh). ASCII only.
$ErrorActionPreference = 'Stop'
$api = 'http://localhost:8081'
$web = 'http://localhost:3000'
$script:pass = 0
$script:fail = 0

function Check($name, $ok) {
  if ($ok) { $script:pass++; Write-Output "PASS $name" }
  else { $script:fail++; Write-Output "FAIL $name" }
}

function Api($method, $path, $body, $token, $headers) {
  $all = @{}
  if ($token) { $all['Authorization'] = "Bearer $token" }
  if ($headers) { foreach ($k in $headers.Keys) { $all[$k] = $headers[$k] } }
  try {
    if ($null -ne $body) {
      return Invoke-RestMethod -Method $method -Uri "$api$path" -Headers $all -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod -Method $method -Uri "$api$path" -Headers $all
  } catch {
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

Write-Output "== 00.13 offline groundwork demo (8081 + 3000) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Offline Demo $s"; code = "OD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)

# 3 doctor registered (task:manage granted to DOCTOR since V50)
$email = "offline-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Offline$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doctor = (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
Check 'doctor login' ($null -ne $doctor)
$docId = (Api GET '/api/v1/auth/me' $null $doctor).data.userId

# 4 task created
$task = (Api POST '/api/v1/tasks' @{ title = "Offline replay $s"; priority = 'normal'; assigneeId = $docId; hospitalId = $h } $doctor).data
Check 'task created' ($null -ne $task.id)

# 5 completion queued with a client idempotency key (the outbox flush sends it)
$key = "demo-offline-$s"
$first = Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'completed' } $doctor @{ 'Idempotency-Key' = $key }
Check 'first flush completes the task' ($first.data.status -eq 'completed')

# 6 the second flush replays the SAME key + payload: byte-identical stored response
$replay = Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'completed' } $doctor @{ 'Idempotency-Key' = $key }
Check 'replay byte-identical (completedAt unchanged, status kept)' ($replay.data.completedAt -eq $first.data.completedAt -and $replay.data.status -eq 'completed')

# 7 exactly one task.completed event despite the replay
$outbox = Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin
$events = @($outbox.data | Where-Object { $_.eventType -eq 'task.completed' -and $_.hospitalId -eq $h })
Check 'task.completed emitted exactly once' ($events.Count -eq 1)

# 8 same key, different payload -> 409 IDEMPOTENCY_CONFLICT
$conflict = Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'cancelled' } $doctor @{ 'Idempotency-Key' = $key }
Check 'payload drift under same key -> 409' ($conflict._status -eq 409)

# 9 contrast: a DIFFERENT key re-sends the same write and it DOES re-execute
#    (completedAt moves, a second event appears) - which is why the key must persist.
$again = Api PATCH "/api/v1/tasks/$($task.id)/status" @{ status = 'completed' } $doctor @{ 'Idempotency-Key' = "demo-offline-2-$s" }
$outbox = Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin
$events2 = @($outbox.data | Where-Object { $_.eventType -eq 'task.completed' -and $_.hospitalId -eq $h })
Check 'without the key the write re-executes (new completedAt + second event)' ($again.data.completedAt -ne $first.data.completedAt -and $events2.Count -eq 2)

# 10 web offline shell: service worker served with the offline cache version
$sw = Invoke-WebRequest -Uri "$web/sw.js" -UseBasicParsing -TimeoutSec 10
Check 'web /sw.js served (offline shell v1)' ($sw.StatusCode -eq 200 -and $sw.Content.Contains('squamed-offline-v1') -and $sw.Content.Contains("/offline"))

# 11 web /offline fallback page served
$offline = Invoke-WebRequest -Uri "$web/offline" -UseBasicParsing -TimeoutSec 10
Check 'web /offline fallback page served' ($offline.StatusCode -eq 200 -and $offline.Content.Contains('You are offline'))

# 12 web app serves the shell
$root = Invoke-WebRequest -Uri "$web/" -UseBasicParsing -TimeoutSec 10
Check 'web root serves the app shell' ($root.StatusCode -eq 200 -and $root.Content.Contains('SQUAMED'))

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
