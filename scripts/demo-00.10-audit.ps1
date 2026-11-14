# Exit-demo slice for section 00.10 (Audit): ADR-9 hash chain, auditor viewer data,
# on-demand chain verify, tamper detection + audit.chain_broken event, WORM export.
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
    $code = $_.Exception.Response.StatusCode.value__
    return @{ _status = $code }
  }
}

Write-Output "== 00.10 audit demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Audit Demo $s"; code = "AD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)

# 3 auditor registered (READ_ONLY_VIEWER gets audit:read via V49)
$email = "audit-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = 'Aud'; lastName = "Itor$s"; roles = @('READ_ONLY_VIEWER'); hospitalId = $h } $null | Out-Null
$audLogin = Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null
$auditor = $audLogin.data.accessToken
Check 'auditor login' ($null -ne $auditor)

# 4 auditor reads audit log via the viewer API
$logs = Api GET '/api/v1/audit/logs?limit=25' $null $auditor
Check 'auditor reads audit logs' ($null -ne $logs.data -and $logs.data.Count -gt 0)

# 5 chained rows carry hash fields
$chained = @($logs.data | Where-Object { $_.rowHash })
Check 'audit rows hash-chained (rowHash + chainSeq)' ($chained.Count -gt 0 -and $chained[0].chainSeq -gt 0)

# 6 auditor runs on-demand chain verify (clean for this fresh tenant; other tenants may carry
# legacy/tampered state in the long-lived dev DB)
$verify = Api POST '/api/v1/audit/verify' @{} $auditor
$ownBreaches = @($verify.data.breaches | Where-Object { $_.hospitalId -eq $h })
Check 'chain verify ok for fresh tenant' ($verify.data.checked -gt 0 -and $ownBreaches.Count -eq 0)

# 7 tamper a chained row directly in the dev DB
$tamperRow = $chained | Select-Object -First 1
docker exec squamed-postgres psql -U squamed -d squamed -c "UPDATE audit.audit_log SET response_payload = 'TAMPERED-DEMO' WHERE id = '$($tamperRow.id)';" 2>$null | Out-Null
Check 'row tampered in dev DB' ($LASTEXITCODE -eq 0)

# 8 verify now reports a breach for the tampered tenant
$breach = Api POST '/api/v1/audit/verify' @{} $auditor
$h1Breach = @($breach.data.breaches | Where-Object { $_.reason -like '*HASH_MISMATCH*' })
Check 'tamper detected (HASH_MISMATCH breach)' ($breach.data.ok -eq $false -and $h1Breach.Count -gt 0)

# 9 audit.chain_broken event lands in the outbox
Start-Sleep -Seconds 3
$outbox = Api GET '/api/v1/admin/events/outbox?limit=100' $null $admin
$alert = @($outbox.data | Where-Object { $_.eventType -eq 'audit.chain_broken' })
Check 'audit.chain_broken in outbox' ($alert.Count -gt 0)

# 10 doctor denied audit read
$docEmail = "audit-doc-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $docEmail; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Chain$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$docLogin = Api POST '/api/v1/auth/login' @{ username = $docEmail; password = 'Passw0rd!123' } $null
$denied = Api GET '/api/v1/audit/logs' $null $docLogin.data.accessToken
Check 'doctor denied audit read (403)' ($denied._status -eq 403)

# 11 admin runs WORM export
$export = Api POST '/api/v1/audit/export' @{} $admin
Check 'WORM export returns object key' ($export.data.exported -gt 0 -and $export.data.objectKey -like '*audit*')

# 12 event catalog lists audit.chain_broken (13 v1 types)
$catalog = Api GET '/api/v1/events/catalog' $null $null
$broken = @($catalog.data | Where-Object { $_.name -eq 'AuditChainBroken' -and $_.status -eq 'EMITTED' })
Check 'catalog has AuditChainBroken EMITTED (13 v1 types)' ($broken.Count -eq 1 -and $catalog.data.Count -eq 13)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
