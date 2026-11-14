# 00.07 Patient master exit-demo slice (unknown flow + ABHA identifiers + relationships + photos + tenant guard).
# Usage: pwsh -File scripts/demo-00.07-patient-master.ps1
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
  -ContentType "application/json" -Body (@{ name = "PM Demo $suffix"; code = "PD-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
Check "hospital created" ($null -ne $hid) "hospital id missing"

# 2. unknown patient: sentinel identity + pending status
$unknown = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers $auth -ContentType "application/json" `
  -Body (@{ unknownPatient = $true; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data.patient
Check "unknown patient registered" ($unknown.isUnknown -and $unknown.recordsStatus -eq "pending" -and $unknown.dateOfBirth -eq "1900-01-01") `
  "got isUnknown=$($unknown.isUnknown) status=$($unknown.recordsStatus) dob=$($unknown.dateOfBirth)"
$patId = $unknown.id

# 3. identification via PUT clears the unknown state
$identified = (Invoke-RestMethod -Method Put -Uri "$base/patients/$patId" -Headers $auth -ContentType "application/json" `
  -Body (@{ firstName = "Asha"; lastName = "Rao"; dateOfBirth = "1990-05-05"; gender = "female" } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "patient identified" (!$identified.isUnknown -and $identified.recordsStatus -eq "active" -and $identified.dateOfBirth -eq "1990-05-05") `
  "got isUnknown=$($identified.isUnknown) status=$($identified.recordsStatus)"

# 4. ABHA identifiers: malformed rejected, valid stored, duplicates rejected
# ABHA number is unique per run: the dev DB persists rows, a fixed number would collide on re-run.
$abhaNumber = "90" + $suffix.Substring(2)  # "90" + 12 digits = 14 digits per ABDM regex
$bad = Invoke-WebRequest -Method Post -Uri "$base/patients/$patId/identifiers" -Headers $auth -ContentType "application/json" `
  -Body (@{ idType = "ABHA"; idValue = "1234" } | ConvertTo-Json) -SkipHttpErrorCheck
Check "malformed ABHA rejected" ($bad.StatusCode -eq 400) ("HTTP $($bad.StatusCode): $($bad.Content)")
$abha = (Invoke-RestMethod -Method Post -Uri "$base/patients/$patId/identifiers" -Headers $auth -ContentType "application/json" `
  -Body (@{ idType = "ABHA"; idValue = $abhaNumber } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "ABHA identifier stored" ($abha.idValue -eq $abhaNumber) "got $($abha.idValue)"
$dup = Invoke-WebRequest -Method Post -Uri "$base/patients/$patId/identifiers" -Headers $auth -ContentType "application/json" `
  -Body (@{ idType = "ABHA"; idValue = $abhaNumber } | ConvertTo-Json) -SkipHttpErrorCheck
Check "duplicate identifier rejected" ($dup.StatusCode -ge 400 -and $dup.StatusCode -lt 500) ("HTTP $($dup.StatusCode)")
$search = (Invoke-RestMethod -Method Get -Uri "$base/patients?query=$abhaNumber" -Headers $auth -SkipHttpErrorCheck).data
Check "identifier search resolves" ($search.Count -ge 1 -and $search[0].id -eq $patId) "rows=$($search.Count)"

# 5. guardian relationship: created, self-link rejected, listed in reverse
$related = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers $auth -ContentType "application/json" `
  -Body (@{ firstName = "Ravi"; lastName = "Rao"; dateOfBirth = "1965-01-01"; gender = "male"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data.patient
$rel = (Invoke-RestMethod -Method Post -Uri "$base/patients/$patId/relationships" -Headers $auth -ContentType "application/json" `
  -Body (@{ relatedPatientId = $related.id; relationshipType = "guardian"; isPrimaryGuardian = $true } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "guardian relationship created" ($rel.relatedPatientId -eq $related.id -and $rel.isPrimaryGuardian) "got $($rel | ConvertTo-Json -Depth 2)"
$self = Invoke-WebRequest -Method Post -Uri "$base/patients/$patId/relationships" -Headers $auth -ContentType "application/json" `
  -Body (@{ relatedPatientId = $patId; relationshipType = "guardian" } | ConvertTo-Json) -SkipHttpErrorCheck
Check "self-link rejected" ($self.StatusCode -eq 400) ("HTTP $($self.StatusCode)")
$rev = (Invoke-RestMethod -Method Get -Uri "$base/patients/$($related.id)/relationships" -Headers $auth -SkipHttpErrorCheck).data
Check "reverse relationship listed" ($rev.Count -eq 1 -and $rev[0].reverse) "rows=$($rev.Count) reverse=$($rev[0].reverse)"

# 6. photo: upload jpeg, download identical bytes, unsupported type rejected
$jpeg = [byte[]](0xFF, 0xD8, 0x00, 0x01, 0x02, 0x03, 0xFF, 0xD9)
$boundary = [System.Guid]::NewGuid().ToString()
$bodyStart = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"photo.jpg`"`r`nContent-Type: image/jpeg`r`n`r`n"
$bodyEnd = "`r`n--$boundary--`r`n"
$ms = New-Object System.IO.MemoryStream
$ms.Write([System.Text.Encoding]::ASCII.GetBytes($bodyStart), 0, $bodyStart.Length)
$ms.Write($jpeg, 0, $jpeg.Length)
$ms.Write([System.Text.Encoding]::ASCII.GetBytes($bodyEnd), 0, $bodyEnd.Length)
$parts = $ms.ToArray()
$up = Invoke-WebRequest -Method Post -Uri "$base/patients/$patId/photo" -Headers $auth `
  -ContentType "multipart/form-data; boundary=$boundary" -Body $parts -SkipHttpErrorCheck
Check "photo uploaded" ($up.StatusCode -eq 200 -and $up.Content.Contains("photos/")) ("HTTP $($up.StatusCode): $($up.Content)")
$down = Invoke-WebRequest -Method Get -Uri "$base/patients/$patId/photo" -Headers $auth -SkipHttpErrorCheck
Check "photo bytes roundtrip" ($down.StatusCode -eq 200 -and $down.Content.Length -eq $jpeg.Length) `
  "HTTP $($down.StatusCode) len=$($down.Content.Length) expected=$($jpeg.Length)"

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
