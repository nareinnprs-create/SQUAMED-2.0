# Exit-demo slice for section 01.03 (Reception workspace): walk-in visit + check-in,
# visitor/attendant passes (per-hospital VPS- sequence, return + 409 guard), consult-fee
# receipts (per-hospital RCP- sequence, void keeps audit trail + 409 guard), patient
# calling via the queue endpoints, cancel with reason + VISIT_CANCELLED guard, RBAC and
# cross-tenant denial. Runs against the app on port 8081 (pwsh). ASCII only.
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

Write-Output "== 01.03 reception workspace demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospitals (pass/receipt sequences are per-hospital) + receptionists
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Recep Demo $s"; code = "RD-$s"; country = 'Qatar' } $admin).data.id
$hF = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Recep F $s"; code = "RDF-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h -and $null -ne $hF)
$recEmail = "recep-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Demo$s"; roles = @('RECEPTIONIST'); hospitalId = $h } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$recFEmail = "recep-foreign-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recFEmail; password = 'Passw0rd!123'; firstName = 'For'; lastName = "Eign$s"; roles = @('RECEPTIONIST'); hospitalId = $hF } $null | Out-Null
$recF = (Api POST '/api/v1/auth/login' @{ username = $recFEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'receptionists log in' ($null -ne $rec -and $null -ne $recF)

# 3 walk-in registration fast-path: patient lookup then visit create + check-in
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Recep'; lastName = "A$s"; gender = 'female'; dateOfBirth = '1990-02-02'; hospitalId = $h } $admin).data.patient.id
$lookup = Api GET "/api/v1/patients?query=A$s" $null $rec
Check 'patient lookup (registration fast-path)' (@($lookup.data).Count -ge 1)
$v = (Api POST '/api/v1/reception/visits' @{ patientId = $pa; visitType = 'outpatient'; reason = 'reception demo' } $rec).data.visit
Check 'walk-in visit registered (VN- number)' ($null -ne $v.id -and $v.visitNumber -like 'VN-*')
$ci = Api PATCH "/api/v1/reception/visits/$($v.id)/check-in" $null $rec
Check 'visit checked in' ($ci.data.status -eq 'checked-in')

# 4 visitor/attendant passes: per-hospital VPS- sequence, return, 409 re-return
$p1 = (Api POST "/api/v1/reception/visits/$($v.id)/passes" @{ visitorName = 'Ali Attendant'; relation = 'attendant'; phone = '+9745000001' } $rec).data
Check 'first pass VPS-0001' ($p1.passNumber -eq 'VPS-0001' -and $p1.status -eq 'active')
$p2 = (Api POST "/api/v1/reception/visits/$($v.id)/passes" @{ visitorName = 'Sara Visitor'; relation = 'visitor' } $rec).data
Check 'second pass VPS-0002' ($p2.passNumber -eq 'VPS-0002')
$ret = (Api PATCH "/api/v1/reception/passes/$($p1.id)/return" $null $rec).data
Check 'pass returned (returnedAt stamped)' ($ret.status -eq 'returned' -and $null -ne $ret.returnedAt)
$reRet = Api PATCH "/api/v1/reception/passes/$($p1.id)/return" $null $rec
Check 're-return rejected (409)' ($reRet._status -eq 409)
$passes = (Api GET "/api/v1/reception/visits/$($v.id)/passes" $null $rec).data
Check 'passes listed (2)' (@($passes).Count -eq 2)

# 5 consult-fee receipts: per-hospital RCP- sequence, void keeps the row, 409 re-void
$r1 = (Api POST "/api/v1/reception/visits/$($v.id)/receipts" @{ amountQar = 500; mode = 'cash' } $rec).data
Check 'first receipt RCP-0001' ($r1.receiptNumber -eq 'RCP-0001' -and $r1.amountQar -eq 500)
$vd = (Api PATCH "/api/v1/reception/receipts/$($r1.id)/void" $null $rec).data
Check 'void keeps the row (status voided)' ($vd.status -eq 'voided' -and $null -ne $vd.voidedAt)
$reVd = Api PATCH "/api/v1/reception/receipts/$($r1.id)/void" $null $rec
Check 're-void rejected (409)' ($reVd._status -eq 409)
$r2 = (Api POST "/api/v1/reception/visits/$($v.id)/receipts" @{ amountQar = 300; mode = 'card' } $rec).data
Check 'receipt sequence continues RCP-0002' ($r2.receiptNumber -eq 'RCP-0002')
$receipts = (Api GET "/api/v1/reception/visits/$($v.id)/receipts" $null $rec).data
Check 'receipts listed (1 voided + 1 issued)' (@($receipts).Count -eq 2)

# 6 patient calling from the workspace: queue entry + call-next + specific call
$q = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa } $rec).data
Check 'workspace queue entry C-01' ($q.tokenNumber -eq 'C-01')
$next = (Api POST '/api/v1/queue/entries/call-next' @{ queueType = 'consult' } $rec).data
Check 'workspace call-next' ($next.tokenNumber -eq 'C-01' -and $next.status -eq 'called')

# 7 cancel with reason; closed visit refuses new passes/receipts
$cx = (Api PATCH "/api/v1/reception/visits/$($v.id)/cancel" @{ reason = 'no show' } $rec).data
Check 'visit cancelled with reason' ($cx.status -eq 'cancelled')
$latePass = Api POST "/api/v1/reception/visits/$($v.id)/passes" @{ visitorName = 'Late' } $rec
Check 'cancelled visit refuses pass (VISIT_CANCELLED)' ($latePass._status -eq 409)

# 8 foreign hospital proves the pass sequence is hospital-scoped (starts at VPS-0001)
$paF = (Api POST '/api/v1/patients' @{ firstName = 'Recep'; lastName = "F$s"; gender = 'male'; dateOfBirth = '1991-03-03'; hospitalId = $hF } $admin).data.patient.id
$vF = (Api POST '/api/v1/reception/visits' @{ patientId = $paF } $recF).data.visit
$pF = (Api POST "/api/v1/reception/visits/$($vF.id)/passes" @{ visitorName = 'Foreign Attendant' } $recF).data
Check 'foreign pass sequence starts VPS-0001' ($pF.passNumber -eq 'VPS-0001')

# 9 cross-tenant denial on visit + passes + receipts reads/writes
# (requireSameHospital -> 403 ACCESS_DENIED, the reception module's existing convention)
$fVisit = Api GET "/api/v1/reception/visits/$($v.id)" $null $recF
$fPasses = Api GET "/api/v1/reception/visits/$($v.id)/passes" $null $recF
$fReceipts = Api GET "/api/v1/reception/visits/$($v.id)/receipts" $null $recF
$fIssue = Api POST "/api/v1/reception/visits/$($v.id)/passes" @{ visitorName = 'X' } $recF
Check 'cross-tenant visit read denied' ($fVisit._status -eq 403)
Check 'cross-tenant passes read denied' ($fPasses._status -eq 403)
Check 'cross-tenant receipts read denied' ($fReceipts._status -eq 403)
Check 'cross-tenant pass issue denied' ($fIssue._status -eq 403)

# 10 RBAC: a role without reception perms cannot read visits
$nurseEmail = "nurse-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $nurseEmail; password = 'Passw0rd!123'; firstName = 'Nur'; lastName = "Se$s"; roles = @('NURSE'); hospitalId = $h } $null | Out-Null
$nurse = (Api POST '/api/v1/auth/login' @{ username = $nurseEmail; password = 'Passw0rd!123' } $null).data.accessToken
$nRead = Api GET '/api/v1/reception/visits' $null $nurse
Check 'nurse read denied (no reception:read:visit)' ($nRead._status -eq 403)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
