# 00.05 Org tree & config engine exit-demo slice (versioned validated JSONB config documents).
# Usage: pwsh -File scripts/demo-00.05-config-engine.ps1
# Requires the app running on localhost:8081 (compose stack up): SERVER_PORT=8081 .\gradlew.bat bootRun
# (host 8080 is occupied by an unrelated docker container, so the app boots on 8081)
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081/api/v1"
$pass = 0
$fail = 0

function Check($name, $cond, $detail) {
  if ($cond) { $script:pass++; Write-Host "PASS $name" }
  else { $script:fail++; Write-Host "FAIL $name :: $detail" }
}

$suffix = Get-Date -Format "yyyyMMddHHmmss"
$auth = @{ Authorization = "Bearer $null" }

# 1. admin login + fresh hospital
$admin = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = "admin@squamed.local"; password = "Admin@12345" } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
Check "admin login" ($null -ne $admin) "admin token missing"
$auth.Authorization = "Bearer $admin"

$hid = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers $auth `
  -ContentType "application/json" -Body (@{ name = "Cfg Demo $suffix"; code = "CD-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
Check "hospital created" ($null -ne $hid) "hospital id missing"

# 2. unknown config type rejected before validation
$unknown = Invoke-WebRequest -Method Put -Uri "$base/hospital-config/hospitals/$hid/config/nonexistent" -Headers $auth `
  -ContentType "application/json" -Body '{"x":1}' -SkipHttpErrorCheck
Check "unknown type rejected" ($unknown.StatusCode -eq 400 -and $unknown.Content -match "UNKNOWN_CONFIG_TYPE") ("HTTP $($unknown.StatusCode): $($unknown.Content)")

# 3. schema-invalid content rejected with violation detail
$invalid = Invoke-WebRequest -Method Put -Uri "$base/hospital-config/hospitals/$hid/config/working-hours" -Headers $auth `
  -ContentType "application/json" -Body (@{ timezone = "Asia/Kolkata"; days = @(@{ day = "funday"; open = "25:99"; close = "08:00" }) } | ConvertTo-Json -Depth 5) -SkipHttpErrorCheck
Check "invalid content rejected" ($invalid.StatusCode -eq 400 -and $invalid.Content -match "CONFIG_VALIDATION_FAILED") ("HTTP $($invalid.StatusCode): $($invalid.Content)")

# 4. valid publish -> v1 active
$wh1 = @{ timezone = "Asia/Kolkata"; days = @(@{ day = "mon"; open = "08:00"; close = "18:00" }) }
$v1 = (Invoke-RestMethod -Method Put -Uri "$base/hospital-config/hospitals/$hid/config/working-hours" -Headers $auth `
  -ContentType "application/json" -Body ($wh1 | ConvertTo-Json -Depth 5) -SkipHttpErrorCheck).data
Check "publish v1 active" ($v1.version -eq 1 -and $v1.status -eq "active") "got v$($v1.version) $($v1.status)"

# 5. second publish -> v2, first version superseded
$wh2 = @{ timezone = "Asia/Kolkata"; days = @(@{ day = "mon"; open = "09:00"; close = "17:00" }) }
$v2 = (Invoke-RestMethod -Method Put -Uri "$base/hospital-config/hospitals/$hid/config/working-hours" -Headers $auth `
  -ContentType "application/json" -Body ($wh2 | ConvertTo-Json -Depth 5) -SkipHttpErrorCheck).data
Check "publish v2" ($v2.version -eq 2 -and $v2.status -eq "active") "got v$($v2.version) $($v2.status)"

# 6. GET latest returns v2 content
$latest = (Invoke-RestMethod -Method Get -Uri "$base/hospital-config/hospitals/$hid/config/working-hours" -Headers $auth -SkipHttpErrorCheck).data
Check "latest is v2" ($latest.version -eq 2 -and $latest.content.days[0].open -eq "09:00") "got v$($latest.version) open=$($latest.content.days[0].open)"

# 7. version history: two rows, v1 superseded, missing version 4xx
$versions = (Invoke-RestMethod -Method Get -Uri "$base/hospital-config/hospitals/$hid/config/working-hours/versions" -Headers $auth -SkipHttpErrorCheck).data
$atV1 = (Invoke-RestMethod -Method Get -Uri "$base/hospital-config/hospitals/$hid/config/working-hours/versions/1" -Headers $auth -SkipHttpErrorCheck).data
$missing = Invoke-WebRequest -Method Get -Uri "$base/hospital-config/hospitals/$hid/config/working-hours/versions/99" -Headers $auth -SkipHttpErrorCheck
Check "version history" ($versions.Count -eq 2 -and $atV1.status -eq "superseded") "rows=$($versions.Count) v1=$($atV1.status)"
Check "missing version 4xx" ($missing.StatusCode -eq 400) ("HTTP $($missing.StatusCode): $($missing.Content)")

# 8. second type (holidays): invalid date rejected, valid publish accepted
$badHoliday = Invoke-WebRequest -Method Put -Uri "$base/hospital-config/hospitals/$hid/config/holidays" -Headers $auth `
  -ContentType "application/json" -Body (@{ year = 2026; holidays = @(@{ date = "2026-13-40"; name = "X" }) } | ConvertTo-Json -Depth 5) -SkipHttpErrorCheck
Check "invalid holiday date rejected" ($badHoliday.StatusCode -eq 400 -and $badHoliday.Content -match "CONFIG_VALIDATION_FAILED") ("HTTP $($badHoliday.StatusCode): $($badHoliday.Content)")
$holiday = (Invoke-RestMethod -Method Put -Uri "$base/hospital-config/hospitals/$hid/config/holidays" -Headers $auth `
  -ContentType "application/json" -Body (@{ year = 2026; holidays = @(@{ date = "2026-01-26"; name = "Republic Day" }) } | ConvertTo-Json -Depth 5) -SkipHttpErrorCheck).data
Check "valid holiday publish" ($holiday.version -eq 1 -and $holiday.content.holidays[0].name -eq "Republic Day") "got $($holiday | ConvertTo-Json -Depth 3)"

# 9. config.changed domain event reached Kafka topic
Start-Sleep -Seconds 2
$event = docker exec squamed-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic squamed.domain-events `
  --from-beginning --max-messages 2000 --timeout-ms 8000 2>$null | Select-String "config.changed" | Select-Object -First 1
Check "config.changed event published" ($null -ne $event -and $event -match "`"type`"`s*:`s*`"working-hours`"") "no config.changed in domain-events topic"

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
