# Exit-demo slice for section 00.12 (Consent + notifications): purpose-scoped consent
# capture (eSign-ready signature, per-channel opt-ins), withdrawal, expiry derivation,
# consent-gated outbound sends, DND hours from config, dispatch ledger, event fan-out.
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
    return @{ _status = $_.Exception.Response.StatusCode.value__ }
  }
}

Write-Output "== 00.12 consent + notifications demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospital
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Consent Demo $s"; code = "CD-$s"; country = 'Qatar' } $admin).data.id
Check 'hospital created' ($null -ne $h)

# 3 doctor registered (consent:manage + notification:send granted to DOCTOR in V51)
$email = "consent-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = 'Doc'; lastName = "Consent$s"; roles = @('DOCTOR'); hospitalId = $h } $null | Out-Null
$doctor = (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
Check 'doctor login' ($null -ne $doctor)
$docId = (Api GET '/api/v1/auth/me' $null $doctor).data.userId

# 4 patient (consent subject)
$patId = (Api POST '/api/v1/patients' @{ firstName = 'Consent'; lastName = "Demo$s"; gender = 'female'; dateOfBirth = '1991-03-03'; phone = "56$s"; qid = "6$s"; nationality = 'Qatar'; city = 'Doha' } $doctor).data.patient.id
Check 'patient created' ($null -ne $patId)

# 5 capture COMMUNICATIONS consent: eSign-ready signature + sms/email opt-ins
$consent = (Api POST '/api/v1/consents' @{ patientId = $patId; purpose = 'communications'; scope = 'appointment reminders'; signatureData = 'data:image/png;base64,iVBORw0KGgo='; signedByName = "Consent Demo$s"; channelOptIns = @(@{ channel = 'sms'; optedIn = $true }, @{ channel = 'email'; optedIn = $true }) } $doctor).data
$consentId = $consent.id
Check 'consent captured (granted, signature, 2 opt-ins)' ($consent.status -eq 'granted' -and $consent.signaturePresent -and $consent.channelOptIns.Count -eq 2)

# 6 invalid purpose rejected
$bad = Api POST '/api/v1/consents' @{ patientId = $patId; purpose = 'banana' } $doctor
Check 'invalid purpose rejected (400)' ($bad._status -eq 400)

# 7 consent.captured event in the outbox (poll up to 30s; dispatcher lag)
$capturedEvent = @()
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 2
  $outbox = Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin
  $capturedEvent = @($outbox.data | Where-Object { $_.eventType -eq 'consent.captured' -and $_.hospitalId -eq $h -and $_.status -eq 'SENT' })
  if ($capturedEvent.Count -gt 0) { break }
}
Check 'consent.captured in outbox' ($capturedEvent.Count -gt 0)

# 8 DND hours from config: window always covers "now" (UTC) -> sms send defers
$hour = [DateTime]::UtcNow.Hour
Api PUT "/api/v1/hospital-config/hospitals/$h/config/dnd-hours" @{ enabled = $true; startHour = $hour; endHour = ($hour + 1) % 24 } $admin | Out-Null
$dnd = (Api POST '/api/v1/notifications/send' @{ patientId = $patId; channel = 'sms'; type = 'appointment'; title = "DND block $s"; body = 'visit update'; recipientAddress = '+974560000000' } $doctor).data
Check 'sms inside DND window defers' ($dnd.status -eq 'DEFERRED_DND')

# 9 DND off: unconfigured sms provider -> honest skip (E2 pending)
Api PUT "/api/v1/hospital-config/hospitals/$h/config/dnd-hours" @{ enabled = $false; startHour = 0; endHour = 0 } $admin | Out-Null
$skipped = (Api POST '/api/v1/notifications/send' @{ patientId = $patId; channel = 'sms'; type = 'appointment'; title = "No provider $s"; body = 'visit update'; recipientAddress = '+974560000000' } $doctor).data
Check 'sms without provider skips honestly' ($skipped.status -eq 'SKIPPED_NO_PROVIDER')

# 10 email without SMTP host -> honest skip
$emailReq = (Api POST '/api/v1/notifications/send' @{ patientId = $patId; channel = 'email'; type = 'appointment'; title = "No smtp $s"; body = 'report ready'; recipientAddress = "consent-$s@test.local" } $doctor).data
Check 'email without SMTP config skips honestly' ($emailReq.status -eq 'SKIPPED_NO_SMTP_CONFIG')

# 11 in_app dispatch writes the in-app row directly
$inApp = (Api POST '/api/v1/notifications/send' @{ patientId = $patId; recipientId = $docId; channel = 'in_app'; type = 'general'; title = "In-app hello $s"; body = 'direct dispatch' } $doctor).data
$inbox = Api GET '/api/v1/notifications' $null $doctor
Check 'in_app sent + lands in doctor inbox' ($inApp.status -eq 'SENT' -and @($inbox.data | Where-Object { $_.title -eq "In-app hello $s" }).Count -gt 0)

# 12 withdrawal: consent.withdrawn in outbox + gate closes
$withdrawn = (Api POST "/api/v1/consents/$consentId/withdraw" @{ reason = 'patient opted out' } $doctor).data
$wdEvent = @()
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 2
  $outbox = Api GET '/api/v1/admin/events/outbox?limit=200' $null $admin
  $wdEvent = @($outbox.data | Where-Object { $_.eventType -eq 'consent.withdrawn' -and $_.hospitalId -eq $h -and $_.status -eq 'SENT' })
  if ($wdEvent.Count -gt 0) { break }
}
$blocked = (Api POST '/api/v1/notifications/send' @{ patientId = $patId; channel = 'sms'; type = 'appointment'; title = "After withdraw $s"; body = 'visit update'; recipientAddress = '+974560000000' } $doctor).data
Check 'withdrawal closes the channel gate' ($withdrawn.status -eq 'withdrawn' -and $wdEvent.Count -gt 0 -and $blocked.status -eq 'BLOCKED_NO_CONSENT')

# 13 expiry derived at read time (no sweep): past expiresAt reads expired + gate blocks
$expired = (Api POST '/api/v1/consents' @{ patientId = $patId; purpose = 'communications'; expiresAt = (Get-Date).ToUniversalTime().AddMinutes(-1).ToString('o'); channelOptIns = @(@{ channel = 'sms'; optedIn = $true }) } $doctor).data
$expiredBlock = (Api POST '/api/v1/notifications/send' @{ patientId = $patId; channel = 'sms'; type = 'appointment'; title = "After expiry $s"; body = 'visit update'; recipientAddress = '+974560000000' } $doctor).data
Check 'expired consent derived + gate blocks' ($expired.status -eq 'expired' -and $expiredBlock.status -eq 'BLOCKED_NO_CONSENT')

# 14 dispatch ledger records every gate outcome
$ledger = Api GET "/api/v1/notifications/requests?patientId=$patId" $null $doctor
$ledgerStatuses = @($ledger.data | ForEach-Object { $_.status }) | Sort-Object -Unique
$need = @('SENT', 'BLOCKED_NO_CONSENT', 'DEFERRED_DND', 'SKIPPED_NO_PROVIDER', 'SKIPPED_NO_SMTP_CONFIG')
$allPresent = ($need | Where-Object { $ledgerStatuses -contains $_ }).Count -eq 5
Check 'ledger lists all gate outcomes' $allPresent

# 15 catalog lists consent.* EMITTED (13 v1 types)
$catalog = Api GET '/api/v1/events/catalog' $null $null
$consentEntries = @($catalog.data | Where-Object { $_.name -in @('ConsentCaptured', 'ConsentWithdrawn') -and $_.status -eq 'EMITTED' })
Check 'catalog consent.* EMITTED (13 v1 types)' ($consentEntries.Count -eq 2 -and $catalog.data.Count -eq 13)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
