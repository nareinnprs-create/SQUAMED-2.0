# Exit-demo slice for section 01.05 (Patient 360 v1): document upload/list/download
# round-trip via MinIO-backed storage (pdf accepted, txt rejected), consents with
# effective status in the 360 aggregate (granted, past-expiresAt -> expired, withdraw
# wins -> withdrawn), 360 aggregate carrying documents + consents, and cross-tenant
# denial on the new document endpoints + the aggregate. Runs against the app on
# port 8080 (pwsh). ASCII only.
param([string]$Base = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
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
      return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers
  } catch {
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

function UploadDocument($path, $token, $filePath, $name, $category, $type) {
  # curl.exe declares the multipart part content type explicitly; pwsh -Form
  # guesses from the extension and sends octet-stream for .pdf.
  $out = Join-Path $env:TEMP "up-resp-$([guid]::NewGuid().ToString('N').Substring(0,8)).json"
  $code = curl.exe -s -o $out -w "%{http_code}" -X POST -H "Authorization: Bearer $token" -F "file=@$filePath;type=$type" -F "name=$name" -F "category=$category" "$Base$path"
  $body = if ((Get-Item $out -ErrorAction SilentlyContinue).Length -gt 0) { Get-Content $out -Raw | ConvertFrom-Json } else { $null }
  Remove-Item $out -ErrorAction SilentlyContinue
  return @{ _status = [int]$code; data = $body.data }
}

function DownloadDocument($path, $token) {
  try {
    $resp = Invoke-WebRequest -Uri "$Base$path" -Headers @{ Authorization = "Bearer $token" }
    return @{ status = $resp.StatusCode; bytes = $resp.Content }
  } catch {
    return @{ status = $_.Exception.Response.StatusCode.value__; bytes = [byte[]]@() }
  }
}

Write-Output "== 01.05 Patient 360 v1 demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 two fresh hospitals + one doctor each (foreign doctor for tenancy)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h1 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "P360 Demo $s"; code = "P5-$s"; country = 'Qatar' } $admin).data.id
$h2 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "P360 F $s"; code = "P5F-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h1 -and $null -ne $h2)
$doc1Email = "doc1-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $doc1Email; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "One$s"; roles = @('DOCTOR'); hospitalId = $h1 } $null | Out-Null
$doc1 = (Api POST '/api/v1/auth/login' @{ username = $doc1Email; password = 'Passw0rd!123' } $null).data.accessToken
$doc2Email = "doc2-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $doc2Email; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Two$s"; roles = @('DOCTOR'); hospitalId = $h2 } $null | Out-Null
$doc2 = (Api POST '/api/v1/auth/login' @{ username = $doc2Email; password = 'Passw0rd!123' } $null).data.accessToken
Check 'both doctors log in' ($null -ne $doc1 -and $null -ne $doc2)

# 3 patient in hospital 1
$paId = (Api POST '/api/v1/patients' @{ firstName = 'P360'; lastName = "Demo$s"; gender = 'male'; dateOfBirth = '1990-04-04' } $doc1).data.patient.id
Check 'patient registered with MRN' ($null -ne $paId)

# 4 document upload (pdf) -> metadata
$tmp = Join-Path $env:TEMP "demo-0105-$s"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$pdfPath = Join-Path $tmp 'discharge-summary.pdf'
[IO.File]::WriteAllBytes($pdfPath, [Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n%demo-0105`n"))
$up = UploadDocument "/api/v1/patients/$paId/documents" $doc1 $pdfPath "Discharge summary $s" 'discharge' 'application/pdf'
$doc = $up.data
Check 'pdf upload accepted' ($null -ne $doc.id)
Check 'document metadata stored' ($doc.name -eq "Discharge summary $s" -and $doc.category -eq 'discharge' -and $doc.contentType -eq 'application/pdf' -and $doc.sizeBytes -gt 0)

# 5 document list
$list = (Api GET "/api/v1/patients/$paId/documents" $null $doc1).data
Check 'document list contains the upload' ($list.Count -eq 1 -and $list[0].id -eq $doc.id)

# 6 unsupported content type rejected
$txtPath = Join-Path $tmp 'notes.txt'
[IO.File]::WriteAllText($txtPath, 'not a pdf')
$bad = UploadDocument "/api/v1/patients/$paId/documents" $doc1 $txtPath 'Notes' 'discharge' 'text/plain'
Check 'txt upload rejected 400' ($bad._status -eq 400)

# 7 consents: granted, expired (past expiresAt), withdraw wins
$granted = (Api POST '/api/v1/consents' @{ patientId = $paId; purpose = 'treatment'; signedByName = "P360 Demo$s" } $doc1).data
Check 'treatment consent granted' ($granted.status -eq 'granted')
$expired = (Api POST '/api/v1/consents' @{ patientId = $paId; purpose = 'data_sharing'; expiresAt = '2020-01-01T00:00:00Z' } $doc1).data
Check 'past-expiresAt consent surfaces expired' ($expired.status -eq 'expired')
$wd = (Api POST "/api/v1/consents/$($granted.id)/withdraw" @{ reason = 'opt out' } $doc1).data
Check 'withdraw wins -> withdrawn' ($wd.status -eq 'withdrawn')

# 8 360 aggregate carries documents + consents with effective statuses
$agg = (Api GET "/api/v1/patient360/$paId" $null $doc1).data
Check '360 aggregate carries the document' ($agg.documents.Count -eq 1 -and $agg.documents[0].name -eq "Discharge summary $s")
$consentMap = @{}
foreach ($c in $agg.consents) { $consentMap[$c.purpose] = $c.status }
Check '360 consent statuses (withdrawn wins, expiry applied)' ($consentMap['treatment'] -eq 'withdrawn' -and $consentMap['data_sharing'] -eq 'expired')

# 9 download round-trip bytes match
$dl = DownloadDocument "/api/v1/patients/$paId/documents/$($doc.id)/download" $doc1
$orig = [IO.File]::ReadAllBytes($pdfPath)
Check 'download 200 + bytes match upload' ($dl.status -eq 200 -and [Convert]::ToBase64String($dl.bytes) -eq [Convert]::ToBase64String($orig))

# 10 cross-tenant denials (hospital-2 doctor)
Check 'foreign document list 403' ((Api GET "/api/v1/patients/$paId/documents" $null $doc2)._status -eq 403)
Check 'foreign document download 403' ((DownloadDocument "/api/v1/patients/$paId/documents/$($doc.id)/download" $doc2).status -eq 403)
Check 'foreign patient360 403' ((Api GET "/api/v1/patient360/$paId" $null $doc2)._status -eq 403)
Check 'foreign document upload 403' ((UploadDocument "/api/v1/patients/$paId/documents" $doc2 $pdfPath 'Intruder' 'discharge' 'application/pdf')._status -eq 403)

# 11 own-tenant reads still allowed
Check 'own document list allowed' ($null -ne (Api GET "/api/v1/patients/$paId/documents" $null $doc1).data)
Check 'own patient360 allowed' ($null -ne (Api GET "/api/v1/patient360/$paId" $null $doc1).data)

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
Write-Output "== 01.05 demo: $($script:pass) passed, $($script:fail) failed =="
if ($script:fail -gt 0) { exit 1 }
