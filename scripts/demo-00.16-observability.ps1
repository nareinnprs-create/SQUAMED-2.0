# 00.16 observability demo: metrics + traces + alerts wiring on the dev compose stack.
# Backend boots on 8081 (8080 is the mythic_graphql docker on this machine); the web app is
# untouched by this section. Exits 1 on any failed check.
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

Write-Output "== 00.16 observability demo (backend $backendPort, no web) =="

# 1 observability stack up (recreates grafana with the 00.16 provisioning mounts)
docker compose -f $compose up -d otel-collector alertmanager grafana 2>&1 | Out-Null
Start-Sleep -Seconds 8

# 2 containers running
$cs = docker ps --format '{{.Names}}' | Out-String
Check 'otel-collector container up' ($cs -match 'squamed-otel-collector')
Check 'alertmanager container up' ($cs -match 'squamed-alertmanager')
Check 'grafana container up' ($cs -match 'squamed-grafana')
Check 'prometheus container up' ($cs -match 'squamed-prometheus')

# 3 collector OTLP endpoints reachable on the host
$g = Test-NetConnection -ComputerName localhost -Port 4317 -WarningAction SilentlyContinue
Check 'collector OTLP grpc :4317' $g.TcpTestSucceeded
$g = Test-NetConnection -ComputerName localhost -Port 4318 -WarningAction SilentlyContinue
Check 'collector OTLP http :4318' $g.TcpTestSucceeded

# 4 alertmanager + prometheus healthy. Retry: the Docker Desktop port proxy
# (wslrelay vs docker.backend both listen on the published ports) intermittently
# resets a connection; a later try lands on the healthy proxy (round 3 hit this).
$amOk = $false; $pmOk = $false
foreach ($i in 1..5) {
  if (-not $amOk) { $amOk = ((curl.exe -s -m 5 -o NUL -w "%{http_code}" http://localhost:9093/-/ready) -eq '200') }
  if (-not $pmOk) { $pmOk = ((curl.exe -s -m 5 -o NUL -w "%{http_code}" http://localhost:9090/-/ready) -eq '200') }
  if ($amOk -and $pmOk) { break }
  Start-Sleep -Seconds 3
}
Check 'alertmanager ready' $amOk
Check 'prometheus ready' $pmOk

# 5 prometheus has the 00.16 alert + recording rules (same proxy flake - retry)
$wanted = @('SloAvailabilityBurnFast','SloAvailabilityBurnSlow','SloLatencyBurnFast','SloLatencyBurnSlow','AuditChainBreached','AuditVerifyStale','DlqNonEmpty','squamed:slo:http_error_ratio:5m','squamed:slo:latency_bad_ratio:5m')
$missing = @('pending')
foreach ($i in 1..5) {
  $rules = curl.exe -s -m 10 http://localhost:9090/api/v1/rules | Out-String
  $missing = @($wanted | Where-Object { $rules -notmatch [regex]::Escape($_) })
  if ($missing.Count -eq 0) { break }
  Start-Sleep -Seconds 4
}
Check 'SLO burn + audit + DLQ alert rules loaded' ($missing.Count -eq 0) ($missing -join ',')

# 6 backend on 8081 (boot when the probe fails)
if (-not (curl.exe -s -o NUL -f "http://localhost:$backendPort/api/v1/events/catalog" 2>$null) -or $LASTEXITCODE -ne 0) {
  Write-Output "booting backend (SERVER_PORT=$backendPort bootRun, OTEL -> localhost:4317)..."
  Kill-PortOwnerTree $backendPort
  $env:SERVER_PORT = "$backendPort"
  $env:SPRING_PROFILES_ACTIVE = 'dev'
  $env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"
  $env:OTEL_EXPORTER_OTLP_PROTOCOL = 'grpc'
  Start-Process -FilePath "$backendRoot\gradlew.bat" -ArgumentList 'bootRun','--no-daemon','-q' `
    -WorkingDirectory $backendRoot -WindowStyle Hidden `
    -RedirectStandardOutput "$env:TEMP\backend-0016-demo.log" `
    -RedirectStandardError "$env:TEMP\backend-0016-demo.err.log"
}
$up = $false
$deadline = (Get-Date).AddSeconds(240)
while ((Get-Date) -lt $deadline) {
  $code = curl.exe -s -o NUL -w "%{http_code}" "http://localhost:$backendPort/api/v1/events/catalog"
  if ($code -eq '200') { $up = $true; break }
  Start-Sleep -Seconds 5
}
Check "backend up on $backendPort" $up
if (-not $up) { exit 1 }

# 7 RED metrics exposed without auth (00.16: prometheus scrape endpoint)
$m = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
Check 'RED http_server_requests histogram exposed' ($m -match 'http_server_requests_seconds_bucket')
Check 'JVM base metrics exposed' ($m -match 'jvm_memory_used_bytes')

# 8 admin login + business traffic: task -> dispatched + WS relayed counters
$body = '{"username":"admin@squamed.local","password":"Admin@12345"}'
$login = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/auth/login" -H 'Content-Type: application/json' --data-raw $body | ConvertFrom-Json
$token = $login.data.accessToken
Check 'admin login' ($null -ne $token -and $token.Length -gt 20)
if (-not $token) { exit 1 }
$h = ((curl.exe -s "http://localhost:$backendPort/api/v1/hospital-config/hospitals" -H "Authorization: Bearer $token" | ConvertFrom-Json).data | Select-Object -First 1).id
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$taskBody = '{"title":"obs demo ' + $suffix + '","hospitalId":"' + $h + '"}'
$task = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/tasks" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-raw $taskBody | ConvertFrom-Json
Check 'task created (drives the event fabric)' ($null -ne $task.data.id)
$slaBody = '{"title":"obs sla ' + $suffix + '","hospitalId":"' + $h + '","dueAt":"2026-01-01T00:00:00Z"}'
$slaTask = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/tasks" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-raw $slaBody | ConvertFrom-Json
$null = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/tasks/sla/scan" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}'
Start-Sleep -Seconds 3
$m = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
Check 'events dispatched counter with type tag' ($m -match 'squamed_events_dispatched_total\{type="task\.created"')
Check 'WS relayed counter with type tag' ($m -match 'squamed_ws_relayed_total\{type=')
Check 'SLA breach counter incremented' ($m -match 'squamed_sla_breaches_total')

# 9 gauges present
foreach ($gauge in @('squamed_events_dlq_depth','squamed_integration_errors','squamed_audit_chain_breaches','squamed_audit_chain_seconds_since_verify','squamed_ws_sessions')) {
  Check "gauge $gauge present" ($m -match $gauge)
}

# 10 DLQ depth flips when a FAILED outbox row exists (seeded via psql)
$null = docker exec squamed-postgres psql -U squamed -d squamed -c "INSERT INTO events.event_outbox (event_id, event_type, occurred_at, source, entity_type, status, attempts, max_attempts) VALUES (gen_random_uuid(), 'demo.dlq_seed', NOW(), 'demo', 'ObservabilityProbe', 'FAILED', 8, 8);" 2>$null
Start-Sleep -Seconds 2
$m = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$depthLine = ($m -split "`n" | Select-String '^squamed_events_dlq_depth').Line
$depth = 0.0
if ($depthLine) { $depth = [double]($depthLine.Trim() -split '\s+')[-1] }
Check 'outbox DLQ depth > 0 with FAILED row' ($depth -ge 1.0) ("depth=$depth")

# 11 audit breach gauge flips on tamper and restores (PIT-mirrored fresh tenant).
# Platform-admin rows carry hospital_id NULL (SYSTEM chain, skipped by verify) - so
# register a doctor in a fresh hospital: the audited write must land in a TENANT chain.
$fh = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/hospital-config/hospitals" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-raw ('{"name":"Obs Demo H ' + $suffix + '","code":"OBS-DEMO-' + $suffix + '","country":"Qatar"}') | ConvertFrom-Json
$fhId = $fh.data.id
$regBody = '{"email":"obs-demo-' + $suffix + '@test.local","password":"Passw0rd!123","firstName":"Obs","lastName":"Demo' + $suffix + '","roles":["DOCTOR"],"hospitalId":"' + $fhId + '"}'
$null = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/auth/register" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" --data-raw $regBody
$dlogin = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/auth/login" -H 'Content-Type: application/json' --data-raw ('{"username":"obs-demo-' + $suffix + '@test.local","password":"Passw0rd!123"}') | ConvertFrom-Json
$dtok = $dlogin.data.accessToken
$patientBody = '{"firstName":"Obs","lastName":"DemoPatient' + $suffix + '","gender":"female","dateOfBirth":"1985-05-10","phone":"' + ("5511$suffix").Substring(0,12) + '","qid":"' + ("P2$suffix").Substring(0,11) + '","nationality":"Qatar","city":"Doha"}'
$null = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/patients" -H 'Content-Type: application/json' -H "Authorization: Bearer $dtok" --data-raw $patientBody
$null = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/audit/verify" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}'
$mg = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$b0 = [double]((($mg -split "`n" | Select-String '^squamed_audit_chain_breaches ' | Select-Object -First 1).Line).Trim() -split '\s+')[-1]
$row = docker exec squamed-postgres psql -U squamed -d squamed -t -A -c "SELECT id || '|' || coalesce(response_payload, '') FROM audit.audit_log WHERE hospital_id = '$fhId' AND chain_seq IS NOT NULL AND response_payload IS NOT NULL ORDER BY created_at DESC LIMIT 1;"
$parts = $row -split '\|', 2
$null = docker exec squamed-postgres psql -U squamed -d squamed -c ("UPDATE audit.audit_log SET response_payload = 'TAMPERED-DEMO-" + $suffix + "' WHERE id = '" + $parts[0] + "';")
$null = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/audit/verify" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}'
Start-Sleep -Seconds 2
$mg = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$bTamper = [double]((($mg -split "`n" | Select-String '^squamed_audit_chain_breaches ' | Select-Object -First 1).Line).Trim() -split '\s+')[-1]
Check 'audit breach gauge gains exactly the fresh tenant after tamper' ($bTamper -eq ($b0 + 1.0)) ("b0=$b0 after=$bTamper")
$orig = $parts[1].Replace("'", "''")
$null = docker exec squamed-postgres psql -U squamed -d squamed -c ("UPDATE audit.audit_log SET response_payload = '" + $orig + "' WHERE id = '" + $parts[0] + "';")
$null = curl.exe -s -X POST "http://localhost:$backendPort/api/v1/audit/verify" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d '{}'
$mg = curl.exe -s "http://localhost:$backendPort/actuator/prometheus" | Out-String
$bRest = [double]((($mg -split "`n" | Select-String '^squamed_audit_chain_breaches ' | Select-Object -First 1).Line).Trim() -split '\s+')[-1]
Check 'audit breach gauge returns to baseline after restore' ($bRest -eq $b0) ("b0=$b0 restored=$bRest")

# 12 traces reach the collector through tail sampling (debug exporter prints spans)
Start-Sleep -Seconds 5
$logs = docker logs squamed-otel-collector --since 5m 2>&1 | Out-String
Check 'collector received spans (debug exporter output)' ($logs -match 'Trace ID|Spans')

# 13 grafana provisioning: datasource + dashboard visible via API. Retry - provisioning
# can lag a just-recreated container by a few seconds (round 1 hit that race).
$gf = curl.exe -s -u 'squamed:squamed_dev_password' 'http://localhost:3005/api/search?query=SQuaMED' | ConvertFrom-Json
Check 'grafana RED dashboard provisioned' ($null -ne $gf -and $gf.Count -ge 1 -and ($gf | Where-Object { $_.uid -eq 'squamed-red' }))
$dsOk = $false
foreach ($i in 1..6) {
  $ds = curl.exe -s -u 'squamed:squamed_dev_password' 'http://localhost:3005/api/datasources' | ConvertFrom-Json
  if ($null -ne $ds -and ($ds | Where-Object { $_.uid -eq 'prometheus' })) { $dsOk = $true; break }
  Start-Sleep -Seconds 5
}
Check 'grafana prometheus datasource provisioned' $dsOk

# cleanup: stop the demo backend (port owner tree only - matches the other demos)
Kill-PortOwnerTree $backendPort

Write-Output "== 00.16 demo: $passed passed, $failed failed =="
if ($failed -gt 0) { exit 1 }
