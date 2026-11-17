# Exit-demo slice for section 01.06 (TV display app): whole-hospital display board
# grouped per counter - current/next per counter, call-next serving the emergency
# token first, priority-ranked next list, department names resolved server-side,
# unassigned entries omitted, NO patient identifiers on the public payload, and
# cross-tenant isolation. Runs against the app on port 8080 (pwsh). ASCII only.
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

Write-Output "== 01.06 TV display demo (8080) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 two fresh hospitals + one receptionist each (foreign receptionist for tenancy)
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h1 = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "TV Demo $s"; code = "TV-$s"; country = 'Qatar' } $admin).data.id
$hF = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "TV F $s"; code = "TVF-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h1 -and $null -ne $hF)
$rec1Email = "rec1-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $rec1Email; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "One$s"; roles = @('RECEPTIONIST'); hospitalId = $h1 } $null | Out-Null
$rec1 = (Api POST '/api/v1/auth/login' @{ username = $rec1Email; password = 'Passw0rd!123' } $null).data.accessToken
$recFEmail = "recF-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recFEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Foreign$s"; roles = @('RECEPTIONIST'); hospitalId = $hF } $null | Out-Null
$recF = (Api POST '/api/v1/auth/login' @{ username = $recFEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'both receptionists log in' ($null -ne $rec1 -and $null -ne $recF)

# 3 department (admin only - RECEPTIONIST has no hospital-config read; the board
# resolves department names server-side so receptionist tokens get dept headers)
$dept = (Api POST "/api/v1/hospital-config/hospitals/$h1/departments" @{ name = "Cardiology $s"; code = "CARD-$s"; type = 'clinical' } $admin).data
Check 'department created' ($null -ne $dept.id)

# 4 two counters
$sp1 = (Api POST '/api/v1/queue/service-points' @{ name = 'Counter 1'; code = 'SP1'; queueType = 'consult'; counterNumber = '1' } $rec1).data
$sp2 = (Api POST '/api/v1/queue/service-points' @{ name = 'Counter 2'; code = 'SP2'; queueType = 'consult'; counterNumber = '2' } $rec1).data
Check 'counters created' ($sp1.name -eq 'Counter 1' -and $sp2.name -eq 'Counter 2')

# 5 tokens: routine + urgent + emergency on Counter 1, one routine on Counter 2, one UNASSIGNED
# patients are created by admin (RECEPTIONIST lacks patient:create -> 403)
$pa1 = (Api POST '/api/v1/patients' @{ firstName = 'TV'; lastName = "One$s"; gender = 'male'; dateOfBirth = '1990-01-01'; hospitalId = $h1 } $admin).data.patient
$pa2 = (Api POST '/api/v1/patients' @{ firstName = 'TV'; lastName = "Two$s"; gender = 'female'; dateOfBirth = '1985-02-02'; hospitalId = $h1 } $admin).data.patient
$pa3 = (Api POST '/api/v1/patients' @{ firstName = 'TV'; lastName = "Three$s"; gender = 'male'; dateOfBirth = '1980-03-03'; hospitalId = $h1 } $admin).data.patient
$pa4 = (Api POST '/api/v1/patients' @{ firstName = 'TV'; lastName = "Four$s"; gender = 'female'; dateOfBirth = '1992-04-04'; hospitalId = $h1 } $admin).data.patient
$pa5 = (Api POST '/api/v1/patients' @{ firstName = 'TV'; lastName = "Five$s"; gender = 'male'; dateOfBirth = '1995-05-05'; hospitalId = $h1 } $admin).data.patient
$token1 = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa1.id; servicePointId = 'SP1'; departmentId = $dept.id } $rec1).data.tokenNumber
$token2 = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa2.id; priority = 'urgent'; servicePointId = 'SP1'; departmentId = $dept.id } $rec1).data.tokenNumber
$token3 = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa3.id; priority = 'emergency'; servicePointId = 'SP1'; departmentId = $dept.id } $rec1).data.tokenNumber
$token4 = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa4.id; servicePointId = 'SP2'; departmentId = $dept.id } $rec1).data.tokenNumber
$tokenU = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa5.id } $rec1).data.tokenNumber
Check 'entries added with tokens' ($null -ne $token1 -and $null -ne $token2 -and $null -ne $token3 -and $null -ne $token4 -and $null -ne $tokenU)

# 6 call-next: the emergency token is served first
$call = (Api POST '/api/v1/queue/entries/call-next' @{ queueType = 'consult' } $rec1).data
Check 'call-next serves the emergency token first' ($call.tokenNumber -eq $token3 -and $call.priority -eq 'emergency')

# 7 the board: grouped per counter, current/next, dept names, privacy
$board = (Api GET '/api/v1/queue/display-board' $null $rec1).data
Check 'board fetched on a receptionist token' ($null -ne $board.generatedAt -and $null -ne $board.counters)
Check 'board has exactly 2 counters (unassigned omitted)' ($board.counters.Count -eq 2)
Check 'counters sorted by name' ($board.counters[0].servicePointName -eq 'Counter 1' -and $board.counters[1].servicePointName -eq 'Counter 2')
$c1 = $board.counters[0]
Check 'department name resolved server-side' ($c1.departmentName -eq "Cardiology $s" -and $c1.departmentId -eq $dept.id)
Check 'counter 1 current = called emergency token' ($c1.current.tokenNumber -eq $token3 -and $c1.current.priority -eq 'emergency')
Check 'counter 1 next priority-ranked (urgent before routine)' ($c1.next[0].tokenNumber -eq $token2 -and $c1.next[1].tokenNumber -eq $token1)
$c2 = $board.counters[1]
Check 'counter 2 next = its waiting token' ($c2.next.Count -eq 1 -and $c2.next[0].tokenNumber -eq $token4)
Check 'counter 2 has no current yet' ($null -eq $c2.current)

# 8 privacy: the public payload carries no patient names or ids
$boardJson = $board | ConvertTo-Json -Depth 8
Check 'no patient names on the board' (-not $boardJson.Contains('TV One') -and -not $boardJson.Contains('patientName'))
Check 'no patient ids on the board' (-not $boardJson.Contains($pa1.id))
Check 'unassigned token omitted entirely' (-not $boardJson.Contains($tokenU))

# 9 cross-tenant: another hospital's display sees an empty board
$foreignBoard = (Api GET '/api/v1/queue/display-board' $null $recF).data
Check 'foreign board is empty' ($foreignBoard.counters.Count -eq 0)

# 10 auth: the board requires a token
Check 'board without token 401' ((Api GET '/api/v1/queue/display-board' $null $null)._status -eq 401)

Write-Output "== 01.06 demo: $($script:pass) passed, $($script:fail) failed =="
if ($script:fail -gt 0) { exit 1 }
