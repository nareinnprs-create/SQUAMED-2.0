# Exit-demo slice for section 01.08 (Kiosk-lite): self-service check-in with
# ABHA number / ABHA address / mobile / QR, token slip with QR payload and
# wayfinding department, idempotent re-check-in, tenancy isolation, the
# KIOSK role's narrow grants, and a 50-check-in soak loop (all 2xx, all
# distinct tokens). Runs against the app on port 8080 (pwsh). ASCII only.
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
      return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8) -TimeoutSec 30
    }
    return Invoke-RestMethod -Method $method -Uri "$Base$path" -Headers $headers -TimeoutSec 30
  } catch {
    $status = $_.Exception.Response.StatusCode.value__
    $code = $null
    try { $code = ($_.ErrorDetails.Message | ConvertFrom-Json).error.code } catch {}
    if (-not $code) {
      try {
        $stream = $_.Exception.Response.GetResponseStream()
        if ($stream) { $code = ([IO.StreamReader]$stream).ReadToEnd() | ConvertFrom-Json | ForEach-Object { $_.error.code } }
      } catch {}
    }
    return @{ _status = $status; _code = $code }
  }
}

Write-Output "== 01.08 kiosk-lite demo (8080) =="

# 1 admin login (+ admin user id for appointment doctorId)
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
$adminId = $login.data.userId
if (-not $adminId) { $adminId = $login.data.user.id }
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital + foreign hospital, KIOSK users on each, a receptionist for grant checks
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h1 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Kiosk Demo $s"; code = "KD-$s"; country = 'Qatar' } $admin).data.id
$hF = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Kiosk F $s"; code = "KF-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h1 -and $null -ne $hF)

$kioskEmail = "kiosk-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $kioskEmail; password = 'Passw0rd!123'; firstName = 'Kiosk'; lastName = "Term$s"; roles = @('KIOSK'); hospitalId = $h1 } $null | Out-Null
$kiosk = (Api POST '/api/v1/auth/login' @{ username = $kioskEmail; password = 'Passw0rd!123' } $null).data.accessToken
$kFEmail = "kioskF-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $kFEmail; password = 'Passw0rd!123'; firstName = 'Kiosk'; lastName = "Foreign$s"; roles = @('KIOSK'); hospitalId = $hF } $null | Out-Null
$kioskF = (Api POST '/api/v1/auth/login' @{ username = $kFEmail; password = 'Passw0rd!123' } $null).data.accessToken
$recEmail = "rec-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Demo$s"; roles = @('RECEPTIONIST'); hospitalId = $h1 } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'kiosk, foreign kiosk and receptionist log in' ($null -ne $kiosk -and $null -ne $kioskF -and $null -ne $rec)

# 3 department with wayfinding text: KIOSK may read the catalog, RECEPTIONIST may not
$deptName = "Kiosk Dept $s"
$dept = (Api POST "/api/v1/hospital-config/hospitals/$h1/departments" @{ name = $deptName; code = "KDP-$s"; type = 'outpatient'; description = 'First floor, past the pharmacy' } $admin).data
Check 'department created' ($null -ne $dept.id)
Check 'receptionist cannot list departments (403)' ((Api GET "/api/v1/hospital-config/hospitals/$h1/departments" $null $rec)._status -eq 403)
$kioskDepts = @((Api GET "/api/v1/hospital-config/hospitals/$h1/departments" $null $kiosk).data) | Where-Object { $_.id -eq $dept.id }
Check 'kiosk lists departments (queue:hospital-config read grant)' ($kioskDepts.Count -ge 1 -and $kioskDepts[0].description -eq 'First floor, past the pharmacy')

# 4 patients: ABHA+address+phone, mobile-only, two on one phone, one without a today appointment
$stamp = (Get-Date).Ticks
$abhaNumber = '90' + ($stamp % 1000000000000).ToString('D12')
$abhaAddress = "kiosk.rao.$($stamp % 10000000000)@abdm"
$unknownAbha = '91' + (($stamp + 7) % 1000000000000).ToString('D12')
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Kiran'; lastName = "Rao$s"; gender = 'male'; dateOfBirth = '1988-03-03'; phone = ('55' + ($stamp % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
Api POST "/api/v1/patients/$($pa.id)/identifiers" @{ idType = 'ABHA'; idValue = $abhaNumber } $admin | Out-Null
Api POST "/api/v1/patients/$($pa.id)/identifiers" @{ idType = 'ABHA_ADDRESS'; idValue = $abhaAddress } $admin | Out-Null
Check 'ABHA patient created with both identifiers' ($null -ne $pa.id)
$pm = (Api POST '/api/v1/patients' @{ firstName = 'Mira'; lastName = "Nair$s"; gender = 'female'; dateOfBirth = '1992-07-07'; phone = ('55' + (($stamp + 1) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
$sharedPhone = ('77' + ($stamp % 10000000000).ToString('D10'))
$pAmb1 = (Api POST '/api/v1/patients' @{ firstName = 'Am'; lastName = "Big$s"; gender = 'male'; dateOfBirth = '1970-01-01'; phone = $sharedPhone; hospitalId = $h1 } $admin).data.patient
$pAmb2 = (Api POST '/api/v1/patients' @{ firstName = 'Am2'; lastName = "Big$s"; gender = 'male'; dateOfBirth = '1971-01-01'; phone = $sharedPhone; hospitalId = $h1 } $admin).data.patient
$pl = (Api POST '/api/v1/patients' @{ firstName = 'Late'; lastName = "Comer$s"; gender = 'female'; dateOfBirth = '1980-02-02'; phone = ('55' + (($stamp + 2) % 10000000000).ToString('D10')); hospitalId = $h1 } $admin).data.patient
Check 'mobile, ambiguous and no-appointment patients created' ($null -ne $pm.id -and $null -ne $pAmb1.id -and $null -ne $pAmb2.id -and $null -ne $pl.id)

function AddAppointment($patientId) {
  return (Api POST '/api/v1/appointments' @{
    hospitalId = $h1
    patientId = $patientId
    departmentId = $dept.id
    doctorId = $adminId
    appointmentType = 'OPD'
    scheduledStart = (Get-Date).AddHours(1).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    scheduledEnd = (Get-Date).AddHours(2).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    durationMinutes = 30
    reason = 'kiosk demo'
    priority = 'normal'
  } $admin).data.id
}
$apptAbha = AddAppointment $pa.id
AddAppointment $pm.id | Out-Null
Check 'today appointments booked' ($null -ne $apptAbha)

# 5 ABHA check-in: slip carries token, appointment, wayfinding and the QR payload
$slip = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha'; identifier = $abhaNumber } $kiosk).data
$today = Get-Date -Format 'yyyy-MM-dd'
Check 'ABHA check-in returns token C-01' ($slip.token.tokenNumber -eq 'C-01')
Check 'slip references today appointment' (($null -ne $slip) -and ($null -ne $apptAbha) -and ($slip.appointmentId -eq $apptAbha))
Check 'slip carries wayfinding department' ($slip.departmentName -eq $deptName)
$qrOk = ($null -ne $slip) -and ($slip.token.qrPayload -match "^SQM:$([regex]::Escape($h1)):consult:C-01:\d{4}-\d{2}-\d{2}$")
Check 'slip QR payload is the queue id' $qrOk
Check 'slip shows patient name' ("$($slip.patientName)".Contains("Rao$s"))
Check 'kiosk reads the queue board (queue:read grant)' ($null -ne (Api GET '/api/v1/queue/board?queueType=consult' $null $kiosk).data)

# 6 idempotent re-check-in: same token, no duplicate entry
$again = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha'; identifier = $abhaNumber } $kiosk).data
Check 're-check-in reuses live entry (C-01)' (($null -ne $again) -and ($again.token.tokenNumber -eq 'C-01'))
Check 'no duplicate queue entry' (($null -ne $again) -and (@((Api GET '/api/v1/queue/board?queueType=consult' $null $kiosk).data).Count -eq 1))

# 7 mobile path
$pmPhone = (Api GET "/api/v1/patients/$($pm.id)" $null $admin).data.phone
$slipM = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'mobile'; identifier = $pmPhone } $kiosk).data
Check 'mobile check-in gets next token C-02' ($slipM.token.tokenNumber -eq 'C-02')

# 8 QR re-entry: same token, queue untouched
$boardBefore = @((Api GET '/api/v1/queue/board?queueType=consult' $null $kiosk).data).Count
$slipQr = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'qr'; identifier = $slip.token.qrPayload } $kiosk).data
$boardAfter = @((Api GET '/api/v1/queue/board?queueType=consult' $null $kiosk).data).Count
Check 'QR re-entry re-prints C-01' (($null -ne $slipQr) -and ($slipQr.token.tokenNumber -eq 'C-01'))
Check 'QR re-entry does not change the queue' (($null -ne $slipQr) -and ($boardBefore -eq $boardAfter) -and ($boardAfter -gt 0))

# 9 QR tenancy and validation
Check 'foreign hospital QR rejected (KIOSK_QR_INVALID)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'qr'; identifier = "SQM:$hF:consult:C-01:$today" } $kiosk)._code -eq 'KIOSK_QR_INVALID')
Check 'garbage QR rejected (KIOSK_QR_INVALID)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'qr'; identifier = 'junk' } $kiosk)._code -eq 'KIOSK_QR_INVALID')
Check 'foreign kiosk cannot resolve this hospital patient' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha'; identifier = $abhaNumber } $kioskF)._code -eq 'KIOSK_PATIENT_NOT_FOUND')

# 10 identifier resolution failures
Check 'unknown ABHA (KIOSK_PATIENT_NOT_FOUND)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha'; identifier = $unknownAbha } $kiosk)._code -eq 'KIOSK_PATIENT_NOT_FOUND')
$plPhone = (Api GET "/api/v1/patients/$($pl.id)" $null $admin).data.phone
Check 'no appointment today (KIOSK_NO_APPOINTMENT)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'mobile'; identifier = $plPhone } $kiosk)._code -eq 'KIOSK_NO_APPOINTMENT')
Check 'unsupported type (KIOSK_IDENTIFIER_TYPE_INVALID)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'passport'; identifier = 'X' } $kiosk)._code -eq 'KIOSK_IDENTIFIER_TYPE_INVALID')
Check 'short ABHA rejected (ABHA_NUMBER_INVALID)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha'; identifier = '1234' } $kiosk)._code -eq 'ABHA_NUMBER_INVALID')
Check 'malformed ABHA address rejected (ABHA_ADDRESS_INVALID)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha_address'; identifier = 'bad address' } $kiosk)._code -eq 'ABHA_ADDRESS_INVALID')
$slipAddr = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'abha_address'; identifier = $abhaAddress } $kiosk).data
Check 'ABHA address check-in works' ($null -ne $slipAddr.token.tokenNumber)
Check 'shared phone ambiguous (KIOSK_IDENTIFIER_AMBIGUOUS)' ((Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'mobile'; identifier = $sharedPhone } $kiosk)._code -eq 'KIOSK_IDENTIFIER_AMBIGUOUS')

# 11 the KIOSK role stays read-scoped on patients
Check 'kiosk cannot write patients (403)' ((Api POST '/api/v1/patients' @{ firstName = 'No'; lastName = "Write$s"; gender = 'male'; dateOfBirth = '1990-01-01' } $kiosk)._status -eq 403)

# 12 soak: 50 check-ins in a row, all 2xx, all distinct, each token resolvable on reprint
$tokens = @()
$soakFails = 0
for ($i = 1; $i -le 50; $i++) {
  $phoneRaw = ("55$s$i" -replace '-', '')
  if ($phoneRaw.Length -gt 12) { $phoneRaw = $phoneRaw.Substring($phoneRaw.Length - 12) }
  $pb = (Api POST '/api/v1/patients' @{ firstName = "Burst$i"; lastName = "Kiosk$s"; gender = 'male'; dateOfBirth = '1985-05-05'; phone = $phoneRaw; hospitalId = $h1 } $admin).data.patient
  $pbPhone = (Api GET "/api/v1/patients/$($pb.id)" $null $admin).data.phone
  AddAppointment $pb.id | Out-Null
  $slipB = (Api POST '/api/v1/kiosk/check-in' @{ identifierType = 'mobile'; identifier = $pbPhone } $kiosk).data
  $reprint = Api GET "/api/v1/queue/entries/consult/$($slipB.token.tokenNumber)/reprint" $null $kiosk
  if ($null -eq $slipB.token.tokenNumber -or $null -ne $reprint._status) { $soakFails++ }
  $tokens += $slipB.token.tokenNumber
}
Check "soak: 50 check-ins all succeeded ($($tokens.Count) slips, $soakFails failures)" ($tokens.Count -eq 50 -and $soakFails -eq 0)
Check 'soak: all 50 tokens distinct' (($tokens | Select-Object -Unique).Count -eq 50)

Write-Output "== 01.08 demo: $($script:pass) passed, $($script:fail) failed =="
if ($script:fail -gt 0) { exit 1 }
