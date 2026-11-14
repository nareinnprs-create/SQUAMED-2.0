# 00.06 Master data exit-demo slice (hospital-scoped registries + price lists).
# Usage: pwsh -File scripts/demo-00.06-master-data.ps1
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

# 1. admin login + fresh hospital
$admin = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = "admin@squamed.local"; password = "Admin@12345" } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
Check "admin login" ($null -ne $admin) "admin token missing"
$auth = @{ Authorization = "Bearer $admin" }

$hid = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers $auth `
  -ContentType "application/json" -Body (@{ name = "MD Demo $suffix"; code = "MD-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
Check "hospital created" ($null -ne $hid) "hospital id missing"

# 2. vendor registry: create pinned to hospital, duplicate code rejected, update works
$vendor = (Invoke-RestMethod -Method Post -Uri "$base/master-data/vendors" -Headers $auth -ContentType "application/json" `
  -Body (@{ code = "VN-$suffix"; name = "MedSupply $suffix"; contactPerson = "Rep"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "vendor created hospital-pinned" ($vendor.hospitalId -eq $hid) "got hospital=$($vendor.hospitalId)"
$dup = Invoke-WebRequest -Method Post -Uri "$base/master-data/vendors" -Headers $auth -ContentType "application/json" `
  -Body (@{ code = "VN-$suffix"; name = "Dup"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck
Check "duplicate vendor code rejected" ($dup.StatusCode -eq 400) ("HTTP $($dup.StatusCode): $($dup.Content)")
$venUpd = (Invoke-RestMethod -Method Put -Uri "$base/master-data/vendors/$($vendor.id)" -Headers $auth -ContentType "application/json" `
  -Body (@{ phone = "55123456" } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "vendor updated" ($venUpd.phone -eq "55123456") "phone=$($venUpd.phone)"

# 3. equipment registry linked to vendor
$equip = (Invoke-RestMethod -Method Post -Uri "$base/master-data/equipment" -Headers $auth -ContentType "application/json" `
  -Body (@{ code = "EQ-$suffix"; name = "Ventilator $suffix"; category = "ICU"; vendorId = $vendor.id; serialNumber = "SN-$suffix"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "equipment linked to vendor" ($equip.vendorId -eq $vendor.id) "vendorId=$($equip.vendorId)"

# 4. employee master: demographics + credentials
$emp = (Invoke-RestMethod -Method Post -Uri "$base/master-data/employees" -Headers $auth -ContentType "application/json" `
  -Body (@{ code = "EMP-$suffix"; firstName = "Asha"; lastName = "Rao"; gender = "female"; dateOfBirth = "1988-03-03"; `
      designation = "Consultant"; qualification = "MBBS, MD"; licenseNumber = "LIC-$suffix"; licenseExpiry = "2027-06-30"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "employee demographics+credentials" ($emp.dateOfBirth -eq "1988-03-03" -and $emp.licenseNumber -eq "LIC-$suffix") "got $($emp | ConvertTo-Json -Depth 2)"
$empSearch = (Invoke-RestMethod -Method Get -Uri "$base/master-data/employees?hospitalId=$hid&search=Asha" -Headers $auth -SkipHttpErrorCheck).data
Check "employee search" ($empSearch.Count -ge 1 -and $empSearch[0].code -eq "EMP-$suffix") "rows=$($empSearch.Count)"

# 5. price list + item on a charge item, upsert re-prices
$ci = (Invoke-RestMethod -Method Post -Uri "$base/master-data/charge-items" -Headers $auth -ContentType "application/json" `
  -Body (@{ code = "CI-$suffix"; name = "Demo Charge $suffix"; priceQar = 100.0 } | ConvertTo-Json) -SkipHttpErrorCheck).data
$pl = (Invoke-RestMethod -Method Post -Uri "$base/master-data/price-lists" -Headers $auth -ContentType "application/json" `
  -Body (@{ code = "PL-$suffix"; name = "Standard $suffix"; currency = "QAR"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data
$null = Invoke-RestMethod -Method Post -Uri "$base/master-data/price-lists/$($pl.id)/items" -Headers $auth -ContentType "application/json" `
  -Body (@{ chargeItemId = $ci.id; priceQar = 85.5 } | ConvertTo-Json) -SkipHttpErrorCheck
$items = (Invoke-RestMethod -Method Get -Uri "$base/master-data/price-lists/$($pl.id)/items" -Headers $auth -SkipHttpErrorCheck).data
Check "price list item created" ($items.Count -eq 1 -and $items[0].priceQar -eq 85.5) "items=$($items.Count) price=$($items[0].priceQar)"

# 6. tenant isolation: second hospital sees no rows of the first
$hid2 = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers $auth `
  -ContentType "application/json" -Body (@{ name = "MD Demo B $suffix"; code = "MB-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
$others = (Invoke-RestMethod -Method Get -Uri "$base/master-data/vendors?hospitalId=$hid2" -Headers $auth -SkipHttpErrorCheck).data
Check "cross-hospital isolation" ($others.Count -eq 0) "rows=$($others.Count)"

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
