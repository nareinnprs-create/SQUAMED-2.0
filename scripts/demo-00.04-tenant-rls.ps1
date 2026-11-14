# 00.04 Tenancy & RLS exit-demo slice (server-side tenant resolution + cross-tenant denial).
# Usage: powershell -File scripts/demo-00.04-tenant-rls.ps1
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

# 1. admin login + two fresh hospitals
$admin = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = "admin@squamed.local"; password = "Admin@12345" } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
Check "admin login" ($null -ne $admin) "admin token missing"

$h1 = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers @{ Authorization = "Bearer $admin" } `
  -ContentType "application/json" -Body (@{ name = "Tenant A $suffix"; code = "TA-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
$h2 = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers @{ Authorization = "Bearer $admin" } `
  -ContentType "application/json" -Body (@{ name = "Tenant B $suffix"; code = "TB-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
Check "hospitals created" ($null -ne $h1 -and $null -ne $h2) "h1=$h1 h2=$h2"

# 2. doctor bound to hospital A at registration (tenancy source = user_role)
$email = "doctor-demo-$suffix@test.local"
$reg = Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" `
  -Body (@{ email = $email; password = "Passw0rd!123"; firstName = "Doc"; lastName = "Ten$suffix"; roles = @("DOCTOR"); hospitalId = $h1 } | ConvertTo-Json) -SkipHttpErrorCheck
Check "doctor registered into A" ($reg.success -eq $true) ($reg | ConvertTo-Json -Depth 3)

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = $email; password = "Passw0rd!123" } | ConvertTo-Json) -SkipHttpErrorCheck
$doc = $login.data.accessToken
Check "doctor login" ($null -ne $doc -and $doc.Length -gt 20) ($login | ConvertTo-Json -Depth 3)

# 3. patient create with NO hospital claim -> server resolves A from user_role
$p1 = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers @{ Authorization = "Bearer $doc" } `
  -ContentType "application/json" -Body (@{ firstName = "Tnt"; lastName = "IsoA$suffix"; gender = "male"; dateOfBirth = "1990-01-01" } | ConvertTo-Json) -SkipHttpErrorCheck).data.patient
Check "tenant resolved server-side" ($p1.hospitalId -eq $h1) "got $($p1.hospitalId), want $h1"

# 4. patient create CLAIMING hospital B -> claim overridden, still A
$p2 = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers @{ Authorization = "Bearer $doc" } `
  -ContentType "application/json" -Body (@{ firstName = "Tnt"; lastName = "IsoB$suffix"; gender = "female"; dateOfBirth = "1991-01-01"; hospitalId = $h2 } | ConvertTo-Json) -SkipHttpErrorCheck).data.patient
Check "client claim overridden" ($p2.hospitalId -eq $h1) "got $($p2.hospitalId), want $h1"

# 5. login CLAIMING hospital B -> token tenant stays A
$claimed = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = $email; password = "Passw0rd!123"; hospitalId = $h2 } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
$p3 = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers @{ Authorization = "Bearer $claimed" } `
  -ContentType "application/json" -Body (@{ firstName = "Tnt"; lastName = "IsoC$suffix"; gender = "male"; dateOfBirth = "1992-01-01" } | ConvertTo-Json) -SkipHttpErrorCheck).data.patient
Check "claimed login does not widen tenant" ($p3.hospitalId -eq $h1) "got $($p3.hospitalId), want $h1"

# 6. search filtered to hospital B -> no B rows leak
$search = Invoke-RestMethod -Method Get -Uri "$base/patients?hospitalId=$h2" -Headers @{ Authorization = "Bearer $doc" } -SkipHttpErrorCheck
$leak = @($search.data | Where-Object { $_.hospitalId -eq $h2 })
Check "cross-tenant search denied" ($leak.Count -eq 0) "$($leak.Count) B-tenant rows leaked"

# 7. foreign hospital-config read/write -> 403 (RBAC + tenant). -SkipHttpErrorCheck returns the
# parsed body only, so use Invoke-WebRequest for the numeric status + assert the tenant denial code.
$treeRes = Invoke-WebRequest -Method Get -Uri "$base/hospital-config/hospitals/$h2/tree" -Headers @{ Authorization = "Bearer $doc" } -SkipHttpErrorCheck
$deptRes = Invoke-WebRequest -Method Post -Uri "$base/hospital-config/hospitals/$h2/departments" -Headers @{ Authorization = "Bearer $doc" } `
  -ContentType "application/json" -Body (@{ name = "Trespass"; code = "TR-$suffix"; type = "outpatient" } | ConvertTo-Json) -SkipHttpErrorCheck
Check "foreign tree 403" ($treeRes.StatusCode -eq 403 -and $treeRes.Content -match "TENANT_FORBIDDEN") ("HTTP $($treeRes.StatusCode): $($treeRes.Content)")
Check "foreign department write 403" ($deptRes.StatusCode -eq 403 -and $deptRes.Content -match "TENANT_FORBIDDEN") ("HTTP $($deptRes.StatusCode): $($deptRes.Content)")

# 8. platform admin keeps unrestricted access to both tenants
$t1 = Invoke-RestMethod -Method Get -Uri "$base/hospital-config/hospitals/$h1/tree" -Headers @{ Authorization = "Bearer $admin" } -SkipHttpErrorCheck
$t2 = Invoke-RestMethod -Method Get -Uri "$base/hospital-config/hospitals/$h2/tree" -Headers @{ Authorization = "Bearer $admin" } -SkipHttpErrorCheck
Check "admin sees both tenants" ($t1.success -eq $true -and $t2.success -eq $true) ($t1 | ConvertTo-Json -Depth 2)

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
