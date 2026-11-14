# 00.09 Event fabric exit-demo slice (transactional outbox -> dispatcher -> Kafka/Redis -> scoped WS push + DLQ replay).
# Usage: pwsh -File scripts/demo-00.09-events.ps1
# Requires the app running on localhost:8081 (compose stack up): SERVER_PORT=8081 .\gradlew.bat bootRun
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081/api/v1"
$pass = 0
$fail = 0

function Check($name, $cond, $detail) {
  if ($cond) { $script:pass++; Write-Host "PASS $name" }
  else { $script:fail++; Write-Host "FAIL $name :: $detail" }
}

function Read-WsFrames($ws, $ms) {
  $frames = New-Object System.Collections.Generic.List[string]
  $cts = [System.Threading.CancellationTokenSource]::new($ms)
  $buffer = [byte[]]::new(65536)
  try {
    while (-not $cts.Token.IsCancellationRequested -and $ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
      $task = $ws.ReceiveAsync([ArraySegment[byte]]::new($buffer), $cts.Token)
      try { $task.Wait() } catch { break }
      if ($task.Result.Count -eq 0) { break }
      $frames.Add([System.Text.Encoding]::UTF8.GetString($buffer, 0, $task.Result.Count))
    }
  } catch { }
  $cts.Dispose()
  $frames
}

$suffix = Get-Date -Format "yyyyMMddHHmmss"

# 1. admin login + two fresh hospitals
$admin = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = "admin@squamed.local"; password = "Admin@12345" } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
Check "admin login" ($null -ne $admin) "admin token missing"
$auth = @{ Authorization = "Bearer $admin" }

$hid1 = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers $auth `
  -ContentType "application/json" -Body (@{ name = "Events Demo A $suffix"; code = "EA-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
$hid2 = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers $auth `
  -ContentType "application/json" -Body (@{ name = "Events Demo B $suffix"; code = "EB-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
Check "two hospitals created" ($null -ne $hid1 -and $null -ne $hid2) "h1=$hid1 h2=$hid2"

# 2. doctor u1 in H1, doctor u3 in H2
function Register-DemoDoctor($hid) {
  $email = "doctor-$suffix-$($hid.Substring(0, 6))@demo.local"
  Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -SkipHttpErrorCheck `
    -Body (@{ email = $email; password = "Passw0rd!123"; firstName = "Doc"; lastName = "Tor"; roles = @("DOCTOR"); hospitalId = $hid } | ConvertTo-Json) | Out-Null
  (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
    -Body (@{ username = $email; password = "Passw0rd!123" } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
}
$u1 = Register-DemoDoctor $hid1
$u3 = Register-DemoDoctor $hid2
Check "doctors logged in" ($null -ne $u1 -and $null -ne $u3) "tokens missing"
$u1Id = (Invoke-RestMethod -Method Get -Uri "$base/auth/me" -Headers @{ Authorization = "Bearer $u1" } -SkipHttpErrorCheck).data.userId
$u3Id = (Invoke-RestMethod -Method Get -Uri "$base/auth/me" -Headers @{ Authorization = "Bearer $u3" } -SkipHttpErrorCheck).data.userId

# 3. WebSocket sessions via /realtime alias
$ws1 = [System.Net.WebSockets.ClientWebSocket]::new()
$ws1.ConnectAsync([Uri]"ws://localhost:8081/realtime?token=$u1", [System.Threading.CancellationToken]::None).Wait()
$ws3 = [System.Net.WebSockets.ClientWebSocket]::new()
$ws3.ConnectAsync([Uri]"ws://localhost:8081/realtime?token=$u3", [System.Threading.CancellationToken]::None).Wait()
Check "ws sessions connected" ($ws1.State -eq "Open" -and $ws3.State -eq "Open") "ws1=$($ws1.State) ws3=$($ws3.State)"

# 4. task created in H1 assigned to u1: outbox -> dispatcher -> Redis -> scoped WS frame on ws1
$task = (Invoke-RestMethod -Method Post -Uri "$base/tasks" -Headers $auth -ContentType "application/json" `
  -Body (@{ title = "Events demo $suffix"; hospitalId = $hid1; assigneeId = $u1Id } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "task created" ($null -ne $task.id) "task id missing"
$frames1 = Read-WsFrames $ws1 8000
$taskCreated = $frames1 | Where-Object { $_ -like '*"type":"event.received"*task.created*' } | Select-Object -First 1
Check "u1 received task.created frame" ($null -ne $taskCreated -and $taskCreated -like "*$($task.id)*") "frames=$($frames1.Count) last=$($frames1 | Select-Object -Last 1)"

# 5. cross-hospital filter: assigning to u3 (H2) must NOT reach u3's H2 session
Invoke-RestMethod -Method Post -Uri "$base/tasks/$($task.id)/assign" -Headers $auth -ContentType "application/json" `
  -Body (@{ assigneeId = $u3Id } | ConvertTo-Json) -SkipHttpErrorCheck | Out-Null
$frames3 = Read-WsFrames $ws3 8000
$crossLeak = $frames3 | Where-Object { $_ -like '*task.assigned*' }
Check "cross-hospital task.assigned filtered from u3" ($null -eq $crossLeak) "leak=$crossLeak"

# 6. outbox row carries the stamped envelope v1 (actor + correlation)
$outbox = (Invoke-RestMethod -Method Get -Uri "$base/admin/events/outbox?limit=50" -Headers $auth -SkipHttpErrorCheck).data
$row = $outbox | Where-Object { $_.eventType -eq "task.created" -and $_.envelope -like "*$($task.id)*" } | Select-Object -First 1
$env = if ($null -ne $row) { $row.envelope | ConvertFrom-Json } else { $null }
Check "outbox row SENT with stamped envelope" ($null -ne $row -and $row.status -eq "SENT" -and $env.actorType -eq "user" `
  -and $env.correlationId -ne $null -and $env.version -eq 1) "status=$($row.status) env=$($row.envelope)"

# 7. integration error DLQ: malformed ADT acked AE, recorded, replay fails again with attempt count 2
$malformed = "MSH|^~\&|SENDING|FAC|SQUAMED|CORE|20260101||ADT^A04|CTRL$suffix|P|2.5`rEVN|A04|20260101`rPID|||^^^NOID||Missing^Pid3||19800101|M"
$hl7 = (Invoke-RestMethod -Method Post -Uri "$base/hl7/messages" -Headers $auth -ContentType "application/json" `
  -Body (@{ raw = $malformed } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "malformed ADT acked AE" ($hl7.ackCode -eq "AE") "ackCode=$($hl7.ackCode)"
Start-Sleep -Seconds 1
$errors = (Invoke-RestMethod -Method Get -Uri "$base/admin/events/errors?integration=hl7&status=OPEN" -Headers $auth -SkipHttpErrorCheck).data
$errRow = $errors | Where-Object { $_.error -like "*PID-3*" } | Select-Object -First 1
Check "integration error recorded" ($null -ne $errRow -and $errRow.replayCount -eq 1) "rows=$($errors.Count) count=$($errRow.replayCount)"
$replayed = (Invoke-RestMethod -Method Post -Uri "$base/admin/events/errors/$($errRow.id)/replay" -Headers $auth -SkipHttpErrorCheck).data
Check "replay increments attempts, still failing" ($replayed.replayCount -eq 2 -and $replayed.ok -eq $false) "count=$($replayed.replayCount) ok=$($replayed.ok)"

# 8. cursors + catalog
$cursors = (Invoke-RestMethod -Method Get -Uri "$base/admin/events/cursors" -Headers $auth -SkipHttpErrorCheck).data
$names = ($cursors | ForEach-Object { $_.consumer }) -join ","
Check "consumer cursors tracked" ($names -like "*dispatcher*" -and $names -like "*notification-bridge*") "cursors=$names"
$catalog = (Invoke-RestMethod -Method Get -Uri "$base/events/catalog" -SkipHttpErrorCheck).data
Check "event catalog lists v1 set" ($catalog.Count -eq 12) "entries=$($catalog.Count)"

# 9. permission: doctor token denied event ops
$denied = Invoke-WebRequest -Method Get -Uri "$base/admin/events/outbox" -Headers @{ Authorization = "Bearer $u1" } -SkipHttpErrorCheck
Check "doctor denied event ops" ($denied.StatusCode -eq 403) "HTTP $($denied.StatusCode)"

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
