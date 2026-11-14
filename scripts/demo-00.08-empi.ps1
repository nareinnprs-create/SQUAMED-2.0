# 00.08 EMPI exit-demo slice (review queue + two-person merge rule + reversibility + tenant pin).
# Usage: pwsh -File scripts/demo-00.08-empi.ps1
# Requires the app running on localhost:8081 (compose stack up): SERVER_PORT=8081 .\gradlew.bat bootRun
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
  -ContentType "application/json" -Body (@{ name = "EMPI Demo $suffix"; code = "EQ-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
Check "hospital created" ($null -ne $hid) "hospital id missing"

# 2. auditor registered in the same hospital (READ_ONLY_VIEWER holds patient:merge:cosign)
$auditorEmail = "auditor-$suffix@demo.local"
$regBody = @{ email = $auditorEmail; password = "Passw0rd!123"; firstName = "Aud"; lastName = "Itor"; roles = @("READ_ONLY_VIEWER"); hospitalId = $hid } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body $regBody -SkipHttpErrorCheck | Out-Null
$auditor = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = $auditorEmail; password = "Passw0rd!123"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
Check "auditor cosigner logged in" ($null -ne $auditor) "auditor token missing"
$aud = @{ Authorization = "Bearer $auditor" }

# 3. survivor + identical duplicate: deterministic QID match scores 100 and auto-enqueues a PENDING review
$qid = "88" + $suffix.Substring(2)  # unique per run, dev DB persists rows
$survivor = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers $auth -ContentType "application/json" `
  -Body (@{ firstName = "Asha"; lastName = "Rao"; dateOfBirth = "1990-05-05"; gender = "female"; qid = $qid; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data.patient
Check "survivor registered" ($null -ne $survivor.id) "survivor id missing"
$dup = (Invoke-RestMethod -Method Post -Uri "$base/patients" -Headers $auth -ContentType "application/json" `
  -Body (@{ firstName = "Asha"; lastName = "Rao"; dateOfBirth = "1990-05-05"; gender = "female"; qid = $qid; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "duplicate surfaces deterministic match" ($dup.matches.Count -ge 1 -and $dup.matches[0].score -ge 96) "score=$($dup.matches[0].score)"
$dupId = $dup.patient.id

Invoke-RestMethod -Method Post -Uri "$base/patients/$dupId/allergies" -Headers $auth -ContentType "application/json" `
  -Body (@{ allergen = "Ibuprofen"; severity = "moderate" } | ConvertTo-Json) -SkipHttpErrorCheck | Out-Null

# 4. review queue: auto-enqueued PENDING row for the duplicate
$queue = (Invoke-RestMethod -Method Get -Uri "$base/patients/empi/reviews?hospitalId=$hid&status=PENDING" -Headers $auth -SkipHttpErrorCheck).data
$review = $queue | Where-Object { $_.duplicatePatientId -eq $dupId } | Select-Object -First 1
Check "duplicate auto-enqueued as PENDING review" ($null -ne $review) "queue rows=$($queue.Count)"

# 5. two-person rule: submitter cannot approve, approver needs the cosign permission
$self = Invoke-WebRequest -Method Post -Uri "$base/patients/empi/reviews/$($review.id)/approve" -Headers $auth -SkipHttpErrorCheck
$selfCode = ($self.Content | ConvertFrom-Json).error.code
Check "two-person rule blocks self-approval" ($self.StatusCode -eq 400 -and $selfCode -eq "TWO_PERSON_RULE_SELF_APPROVAL") "HTTP $($self.StatusCode) code=$selfCode"

$doctorEmail = "doctor-$suffix@demo.local"
$docBody = @{ email = $doctorEmail; password = "Passw0rd!123"; firstName = "Doc"; lastName = "Tor"; roles = @("DOCTOR"); hospitalId = $hid } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body $docBody -SkipHttpErrorCheck | Out-Null
$doctor = (Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
  -Body (@{ username = $doctorEmail; password = "Passw0rd!123"; hospitalId = $hid } | ConvertTo-Json) -SkipHttpErrorCheck).data.accessToken
$noCosign = Invoke-WebRequest -Method Post -Uri "$base/patients/empi/reviews/$($review.id)/approve" -Headers @{ Authorization = "Bearer $doctor" } -SkipHttpErrorCheck
Check "approval without cosign permission denied" ($noCosign.StatusCode -eq 403) "HTTP $($noCosign.StatusCode)"

# 6. distinct cosigner approves -> merge executes, children move, duplicate archived
$approved = (Invoke-RestMethod -Method Post -Uri "$base/patients/empi/reviews/$($review.id)/approve" -Headers $aud -SkipHttpErrorCheck).data
Check "cosigner approves review" ($approved.status -eq "APPROVED" -and $null -ne $approved.mergeId) "status=$($approved.status) mergeId=$($approved.mergeId)"
$mergeId = $approved.mergeId
$allergies = (Invoke-RestMethod -Method Get -Uri "$base/patients/$($survivor.id)/allergies" -Headers $auth -SkipHttpErrorCheck).data
Check "allergy moved to survivor" ($allergies.Count -ge 1 -and $allergies[0].allergen -eq "Ibuprofen") "rows=$($allergies.Count)"
$dupDetail = (Invoke-RestMethod -Method Get -Uri "$base/patients/$dupId" -Headers $auth -SkipHttpErrorCheck).data
Check "duplicate archived not deleted" ($dupDetail.recordsStatus -eq "merged") "status=$($dupDetail.recordsStatus)"
$search = (Invoke-RestMethod -Method Get -Uri "$base/patients?query=$qid" -Headers $auth -SkipHttpErrorCheck).data
Check "archived duplicate hidden from search" ($search.Count -eq 1 -and $search[0].id -eq $survivor.id) "rows=$($search.Count)"

# 7. unmerge: reversible - children re-pointed back, duplicate restored
$unmerged = (Invoke-RestMethod -Method Post -Uri "$base/patients/merges/$mergeId/unmerge" -Headers $auth -SkipHttpErrorCheck).data
Check "unmerge reverses merge" ($unmerged.status -eq "UNMERGED") "status=$($unmerged.status)"
$dupRestored = (Invoke-RestMethod -Method Get -Uri "$base/patients/$dupId" -Headers $auth -SkipHttpErrorCheck).data
Check "duplicate restored active" ($dupRestored.recordsStatus -eq "active") "status=$($dupRestored.recordsStatus)"
$allergiesBack = (Invoke-RestMethod -Method Get -Uri "$base/patients/$dupId/allergies" -Headers $auth -SkipHttpErrorCheck).data
Check "allergy re-pointed to duplicate" ($allergiesBack.Count -ge 1 -and $allergiesBack[0].allergen -eq "Ibuprofen") "rows=$($allergiesBack.Count)"

# 8. re-merge same pair -> merge_version 2 in the versioned log
$intent2 = (Invoke-RestMethod -Method Post -Uri "$base/patients/merge" -Headers $auth -ContentType "application/json" `
  -Body (@{ targetId = $survivor.id; duplicateId = $dupId } | ConvertTo-Json) -SkipHttpErrorCheck).data
Invoke-RestMethod -Method Post -Uri "$base/patients/empi/reviews/$($intent2.id)/approve" -Headers $aud -SkipHttpErrorCheck | Out-Null
$logs = (Invoke-RestMethod -Method Get -Uri "$base/patients/merges?hospitalId=$hid" -Headers $auth -SkipHttpErrorCheck).data
$versions = ($logs | Sort-Object mergeVersion | Select-Object -ExpandProperty mergeVersion) -join ","
Check "merge log versioned per pair" ($versions -eq "1,2") "versions=$versions"

# 9. reject path: rejected review cannot be approved afterwards
$intent3 = (Invoke-RestMethod -Method Post -Uri "$base/patients/merge" -Headers $auth -ContentType "application/json" `
  -Body (@{ targetId = $survivor.id; duplicateId = $dupRestored.id } | ConvertTo-Json) -SkipHttpErrorCheck).data
$rejected = (Invoke-RestMethod -Method Post -Uri "$base/patients/empi/reviews/$($intent3.id)/reject" -Headers $auth -SkipHttpErrorCheck).data
Check "review rejected" ($rejected.status -eq "REJECTED") "status=$($rejected.status)"
$approveRejected = Invoke-WebRequest -Method Post -Uri "$base/patients/empi/reviews/$($intent3.id)/approve" -Headers $aud -SkipHttpErrorCheck
Check "rejected review cannot be approved" ($approveRejected.StatusCode -eq 400) "HTTP $($approveRejected.StatusCode)"

# 10. matcher tenant pin: probing the same QID from another hospital must not leak the survivor
$hid2 = (Invoke-RestMethod -Method Post -Uri "$base/hospital-config/hospitals" -Headers $auth `
  -ContentType "application/json" -Body (@{ name = "EMPI Demo B $suffix"; code = "ER-$suffix"; country = "Qatar" } | ConvertTo-Json) -SkipHttpErrorCheck).data.id
$cross = (Invoke-RestMethod -Method Post -Uri "$base/patients/match" -Headers $auth -ContentType "application/json" `
  -Body (@{ firstName = "Asha"; lastName = "Rao"; dateOfBirth = "1990-05-05"; gender = "female"; qid = $qid; hospitalId = $hid2 } | ConvertTo-Json) -SkipHttpErrorCheck).data
Check "matcher pinned to probing hospital" ($cross.Count -eq 0) "hits=$($cross.Count)"

Write-Host ""
Write-Host "Slice: $pass passed, $fail failed"
if ($fail -gt 0) { exit 1 }
