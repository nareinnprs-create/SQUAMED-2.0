# CHUNK 6 EXIT DEMO (section 06.09): walks the chunk-6 contract EXIT CRITERIA that the shipped
# sections cover - a second hospital added to a group with inherited config (no code), the
# consent-gated cross-hospital share adversarial pass, a group referral, degraded mode with the
# offline whitelist and an idempotent replay, retention/erasure, sandbox seeding, the patient
# portal, procurement -> stock, and a workforce roster. The 1,500-user load test has been run (NFR
# unverified - host-dominated), and the datacenter-kill drill is still OWED (needs a second region).
# Runs against the app on port 8080 (pwsh). ASCII only.
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
      return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $headers
  } catch {
    $status = $_.Exception.Response.StatusCode.value__
    $respBody = ''
    try { $respBody = $_.ErrorDetails.Message } catch { }
    return @{ _status = $status; _body = $respBody }
  }
}

Write-Output "== 06.09 chunk-6 exit demo (8080) =="

# 1 admin, a group, hospital A (existing) and hospital B (the new hospital)
$admin = (Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null).data.accessToken
Check 'admin login' ($null -ne $admin)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$hA = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Group A $s"; code = "GA-$s"; country = 'Qatar' } $admin).data.id
$hB = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Group B $s"; code = "GB-$s"; country = 'Qatar' } $admin).data.id
$hC = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Outside $s"; code = "GC-$s"; country = 'Qatar' } $admin).data.id
$group = (Api POST '/api/v1/group/groups' @{ code = "G-$s"; name = "Group $s" } $admin).data
Api POST "/api/v1/group/groups/$($group.id)/hospitals/$hA" @{} $admin | Out-Null
Api POST "/api/v1/group/groups/$($group.id)/hospitals/$hB" @{} $admin | Out-Null
Check 'second hospital attached to the group' ((Api GET "/api/v1/group/groups/$($group.id)" $null $admin).data.hospitals.Count -eq 2)

# 2 second hospital configured from the group default (no code), with a local override
$hours = @{ timezone = 'Asia/Qatar'; days = @(@{ day = 'mon'; open = '09:00'; close = '17:00' }) }
Api PUT "/api/v1/group/groups/$($group.id)/config" @{ type = 'working-hours'; content = $hours } $admin | Out-Null
$inherited = (Api GET "/api/v1/group/config/working-hours?hospitalId=$hB" $null $admin).data
Check 'hospital B inherits the group working hours' ($inherited.source -eq 'group')
$wardA = (Api POST "/api/v1/hospital-config/hospitals/$hA/wards" @{ name = "Ward A $s"; code = "WA-$s"; type = 'general' } $admin).data.id
$wardB = (Api POST "/api/v1/hospital-config/hospitals/$hB/wards" @{ name = "Ward $s"; code = "WB-$s"; type = 'general' } $admin).data.id
$bedB = (Api POST "/api/v1/hospital-config/hospitals/$hB/beds" @{ label = "B-$s"; type = 'standard'; status = 'available'; wardId = $wardB } $admin).data.id
Check 'hospital B gets a ward + bed with no code' ($null -ne $wardB -and $null -ne $bedB)

function Staff($role, $tag, $hospitalId) {
  $email = "$tag-g6-$s@test.local"
  Api POST '/api/v1/auth/register' @{ email = $email; password = 'Passw0rd!123'; firstName = $tag; lastName = "Exit$s"; roles = @($role); hospitalId = $hospitalId } $null | Out-Null
  return (Api POST '/api/v1/auth/login' @{ username = $email; password = 'Passw0rd!123' } $null).data.accessToken
}
$ga = Staff 'GROUP_ADMIN' 'ga' $hA
$gb = Staff 'GROUP_ADMIN' 'gb' $hB
$gc = Staff 'GROUP_ADMIN' 'gc' $hC
$gov = Staff 'SYSTEM_ADMIN' 'gov' $hA
Check 'group admins + hospital-A platform admin log in' ($null -ne $ga -and $null -ne $gb -and $null -ne $gc -and $null -ne $gov)

# 3 same patient identity in both hospitals; consent-gated share adversarial pass
$stamp = (Get-Date).Ticks
function Patient($tag, $hospitalId, $qid) {
  return (Api POST '/api/v1/patients' @{ firstName = "Cross$tag"; lastName = "Exit$s"; gender = 'female'; dateOfBirth = '1980-03-03'; phone = ('66' + ($stamp % 10000000000).ToString('D10')); qid = $qid; nationality = 'QA'; city = 'Doha'; hospitalId = $hospitalId } $admin).data.patient
}
$qid = ("C6$s").PadRight(11, '0')
$pa = Patient 'a' $hA $qid
$pb = Patient 'b' $hB $qid
$candidates = (Api GET "/api/v1/group/patients/$($pa.id)/share-candidates?toHospitalId=$hB" $null $ga).data
Check 'group EMPI finds the same QID in hospital B' ((@($candidates | Where-Object { $_.patientId -eq $pb.id })).Count -eq 1)
$consent = (Api POST '/api/v1/consents' @{ hospitalId = $hA; patientId = $pa.id; purpose = 'data_sharing'; scope = 'cross_hospital'; signedByName = 'Patient' } $admin).data
$beforeShare = Api GET "/api/v1/group/patients/$($pa.id)/access?hospitalId=$hB" $null $gb
Check 'no share = access denied (403)' ($beforeShare._status -eq 403)
$share = (Api POST "/api/v1/group/patients/$($pa.id)/share" @{ toHospitalId = $hB; consentId = $consent.id } $ga).data
Check 'consent-gated share active' ($share.status -eq 'ACTIVE')
$access = (Api GET "/api/v1/group/patients/$($pa.id)/access?hospitalId=$hB" $null $gb).data
Check 'hospital B reads the shared record' ($access.shared -eq $true -and $access.mrn -eq $pa.mrn)
$outside = Api GET "/api/v1/group/patients/$($pa.id)/access?hospitalId=$hC" $null $gc
Check 'hospital outside the group is denied' ($outside._status -eq 403)
$crossGroup = Api POST "/api/v1/group/patients/$($pa.id)/share" @{ toHospitalId = $hC; consentId = $consent.id } $ga
Check 'cannot share outside the group' ($crossGroup._status -eq 400)
Api POST "/api/v1/group/shares/$($share.id)/revoke" @{} $ga | Out-Null
$revoked = Api GET "/api/v1/group/patients/$($pa.id)/access?hospitalId=$hB" $null $gb
Check 'revoked share denies access again' ($revoked._status -eq 403)

# 4 group referral A -> B accepted
$referral = (Api POST '/api/v1/group/referrals' @{ patientId = $pa.id; toHospitalId = $hB; reason = 'Cardiology opinion' } $ga).data
$inbox = (Api GET '/api/v1/group/referrals?box=inbox' $null $gb).data
Check 'referral lands in the receiving hospital inbox' ((@($inbox | Where-Object { $_.id -eq $referral.id })).Count -eq 1)
$accepted = (Api PATCH "/api/v1/group/referrals/$($referral.id)/status" @{ status = 'ACCEPTED' } $gb).data
Check 'receiving hospital accepts the referral' ($accepted.status -eq 'ACCEPTED')

# 5 degraded mode on hospital A: identity/finance blocked, clinical append queued, replay idempotent
Api POST '/api/v1/resilience/degraded/activate' @{ reason = 'datacenter drill' } $gov | Out-Null
$identity = (Api POST '/api/v1/resilience/degraded/check' @{ module = 'identity'; operation = 'write'; appendOnly = $false } $gov).data
$emar = (Api POST '/api/v1/resilience/degraded/check' @{ module = 'emar'; operation = 'administer'; appendOnly = $true } $gov).data
Check 'identity writes blocked while degraded' ($identity.mode -eq 'BLOCKED')
Check 'eMAR administrations queue while degraded' ($emar.mode -eq 'QUEUED')
foreach ($i in 1..3) {
  Api POST '/api/v1/resilience/degraded/queue' @{ module = 'emar'; operation = 'administer'; idempotencyKey = "g6-dose-$s-$i"; payload = @{ doseId = "d$i" }; appendOnly = $true; occurredAt = (Get-Date).ToUniversalTime().ToString('o') } $gov | Out-Null
}
$replay = (Api POST '/api/v1/resilience/degraded/replay' @{} $gov).data
$replay2 = (Api POST '/api/v1/resilience/degraded/replay' @{} $gov).data
Check 'queued writes replayed once (3, ledger 3)' ($replay.queued -eq 3 -and $replay.ledger -eq 3)
Check 'second replay applies nothing (no duplicates)' ($replay2.queued -eq 0 -and $replay2.ledger -eq 3)
Api POST '/api/v1/resilience/degraded/release' @{} $gov | Out-Null
Check 'degraded mode released' ((Api GET '/api/v1/resilience/degraded' $null $gov).data.active -eq $false)

# 6 retention/erasure + sandbox
Api POST '/api/v1/governance/retention/policies' @{ entityType = 'patient_photo'; retentionDays = 365; legalBasis = 'DPDP non-clinical'; clinical = $false } $gov | Out-Null
$erasure = (Api POST '/api/v1/governance/erasure' @{ subjectType = 'patient_photo'; subjectId = $pa.id; reason = 'exit drill' } $gov).data
Check 'non-clinical erasure completes' ($erasure.status -eq 'COMPLETED')
Api POST "/api/v1/governance/sandbox/hospitals/$hB/enable" @{} $gov | Out-Null
$seed = (Api POST "/api/v1/governance/sandbox/hospitals/$hB/seed" @{ patients = 3 } $gov).data
Check 'sandbox hospital seeds 3 synthetic patients' ($seed.created -eq 3 -and $seed.sampleMrn -like 'SBX-*')

# 7 patient portal on hospital B (own data only)
$pbUser = Staff 'PATIENT' 'pb' $hB
$pbUserId = (Api GET '/api/v1/auth/me' $null $pbUser).data.userId
Api POST '/api/v1/portal/accounts' @{ patientId = $pb.id; userId = $pbUserId; abhaNumber = '99-0000-1111-2222' } $gov | Out-Null
$me = (Api GET '/api/v1/portal/me' $null $pbUser).data
Check 'portal shows the linked patient with ABHA' ($me.mrn -eq $pb.mrn -and $me.abhaNumber -eq '99-0000-1111-2222')
$portalBills = (Api GET '/api/v1/portal/bills' $null $pbUser).data
Check 'portal bills are own-data only (none yet)' ($portalBills.Count -eq 0)

# 8 ops on hospital A: procurement -> stock, CSSD cycle, ED encounter
$req = (Api POST '/api/v1/ops/procurement/requisitions' @{ department = 'Theatre'; lines = @(@{ itemCode = "G6-$s"; itemName = 'Sterile pack'; quantity = 5; unitPriceQar = 20.0 }) } $gov).data
Api POST "/api/v1/ops/procurement/requisitions/$($req.id)/status" @{ status = 'SUBMITTED' } $gov | Out-Null
Api POST "/api/v1/ops/procurement/requisitions/$($req.id)/status" @{ status = 'APPROVED' } $gov | Out-Null
$po = (Api POST "/api/v1/ops/procurement/requisitions/$($req.id)/order" @{} $gov).data
Api POST "/api/v1/ops/procurement/orders/$($po.id)/receive" @{ lines = @(@{ itemCode = "G6-$s"; quantity = 5 }) } $gov | Out-Null
$stock = (Api GET '/api/v1/ops/procurement/stock' $null $gov).data | Where-Object { $_.itemCode -eq "G6-$s" }
Check 'procurement indent -> PO -> GRN -> stock 5' ($stock.quantityOnHand -eq 5)
$tray = (Api POST '/api/v1/ops/cssd/trays' @{ code = "T6-$s"; name = 'Exit tray' } $gov).data
$cycle = (Api POST "/api/v1/ops/cssd/trays/$($tray.id)/cycles" @{} $gov).data
$done = (Api POST "/api/v1/ops/cssd/cycles/$($cycle.id)/complete" @{ result = 'PASS' } $gov).data
Check 'CSSD tray sterilized (PASS)' ($done.status -eq 'COMPLETED')
$ed = (Api POST '/api/v1/ops/ed/encounters' @{ patientId = $pa.id; triageLevel = 3; chiefComplaint = 'Ankle injury' } $gov).data
Check 'ED encounter triaged' ($ed.status -eq 'WAITING' -and $ed.triageLevel -eq 3)

# 9 workforce roster for hospital A
$emp = (Api POST '/api/v1/master-data/employees' @{ hospitalId = $hA; code = "E6-$s"; firstName = 'Exit'; lastName = 'Nurse'; designation = 'Staff Nurse' } $admin).data.id
$today = Get-Date
$monday = $today.AddDays(-(([int]$today.DayOfWeek + 6) % 7))
$roster = (Api POST '/api/v1/hr/rosters' @{ weekStart = $monday.ToString('yyyy-MM-dd') } $gov).data
Api POST "/api/v1/hr/rosters/$($roster.id)/shifts" @{ employeeId = $emp; shiftDate = $today.ToString('yyyy-MM-dd'); shiftCode = 'EARLY'; role = 'NURSE'; wardId = $wardA } $gov | Out-Null
$published = (Api POST "/api/v1/hr/rosters/$($roster.id)/publish" @{} $gov).data
Check 'roster published with a nurse shift' ($published.status -eq 'PUBLISHED' -and $published.shifts.Count -eq 1)

# 10 the event trail carries the chunk-6 events
$outbox = (Api GET '/api/v1/admin/events/outbox?limit=300' $null $admin).data
Check 'share/referral/erasure events emitted' ((@($outbox | Where-Object { $_.eventType -in @('record.shared_consented', 'group.referral_accepted', 'governance.erasure_completed') })).Count -ge 3)

# 11 owed (infrastructure) - reported, never faked
Write-Output 'OWED datacenter-kill drill: 20 min degraded ward -> failover, RTO/RPO evidence pack (needs a second region)'
Write-Output 'OWED 06.02 video SDK + payment link, 06.07 ABDM flows (guideline rules, BI and outbound FHIR are built)'


Write-Output "== result: $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
