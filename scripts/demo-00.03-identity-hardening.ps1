# 00.03 Identity hardening exit-demo slice (device registry + kid rotation groundwork).
# Usage: powershell -File scripts/demo-00.03-identity-hardening.ps1
# Requires the app running on localhost:8081 (compose stack up).
$ErrorActionPreference = "Stop"
$base = "http://localhost:8081/api/v1"
$pass = 0
$fail = 0

function Check($name, $cond, $detail) {
  if ($cond) { $script:pass++; Write-Host "PASS $name" }
  else { $fail++; Write-Host "FAIL $name :: $detail" }
}

# 1. unique user
$suffix = Get-Date -Format "yyyyMMddHHmmss"
$email = "device-demo-$suffix@test.local"
$reg = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body (@{
  email = $email; password = "Passw0rd!123"; firstName = "Dev"; lastName = "Ice"
} | ConvertTo-Json) -SkipHttpErrorCheck
Check "register" ($reg.success -eq $true) ($reg | ConvertTo-Json -Depth 3)

# 2. login (device auto-registered from User-Agent fingerprint)
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = $email; password = "Passw0rd!123" } | ConvertTo-Json) -SkipHttpErrorCheck
$token = $login.data.accessToken
Check "login" ($null -ne $token -and $token.Length -gt 20) ($login | ConvertTo-Json -Depth 3)

# 3. list devices — the login's UA must appear as a device
$devices = Invoke-RestMethod -Method Get -Uri "$base/auth/devices" -Headers @{ Authorization = "Bearer $token" } -SkipHttpErrorCheck
$deviceId = $devices.data[0].id
Check "device listed" ($null -ne $deviceId) ($devices | ConvertTo-Json -Depth 4)

# 4. revoke the device (kills the session bound to it)
$revoke = Invoke-RestMethod -Method Delete -Uri "$base/auth/devices/$deviceId" `
  -Headers @{ Authorization = "Bearer $token" } -SkipHttpErrorCheck
Check "device revoked" ($revoke.data.revoked -eq $true) ($revoke | ConvertTo-Json -Depth 3)

# 5. refresh with the killed session's refresh token must be rejected
$refresh = Invoke-RestMethod -Method Post -Uri "$base/auth/refresh" -ContentType "application/json" `
  -Body (@{ refreshToken = $login.data.refreshToken } | ConvertTo-Json) -SkipHttpErrorCheck
Check "refresh rejected after revoke" ($refresh.data.status -eq "INVALID_CREDENTIALS") ($refresh | ConvertTo-Json -Depth 3)

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
