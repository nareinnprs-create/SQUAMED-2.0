# Exit-demo slice for section 01.02 (Queue & token engine): config-driven priority
# classes, daily token sequences, estimated wait population, board ordering, call-next,
# transfer with fresh target token, stateless re-print + QR queue id, emergency bump,
# RBAC and cross-tenant denial. Runs against the app on port 8081 (pwsh). ASCII only.
# The <2s WS bump latency is proven in the PIT (queue.bumped publish -> WS receipt).
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

Write-Output "== 01.02 queue token engine demo (8081) =="

# 1 admin login
$login = Api POST '/api/v1/auth/login' @{ username = 'admin@squamed.local'; password = 'Admin@12345' } $null
$admin = $login.data.accessToken
Check 'admin login' ($null -ne $admin)

# 2 fresh hospitals (token sequences are per-hospital per-day) + receptionists
$s = [guid]::NewGuid().ToString('N').Substring(0, 8)
$h = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Queue Demo $s"; code = "QD-$s"; country = 'Qatar' } $admin).data.id
$hF = (Api POST '/api/v1/hospital-config/hospitals' @{ name = "Queue F $s"; code = "QDF-$s"; country = 'Qatar' } $admin).data.id
Check 'hospitals created' ($null -ne $h -and $null -ne $hF)
$recEmail = "queue-demo-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recEmail; password = 'Passw0rd!123'; firstName = 'Rec'; lastName = "Demo$s"; roles = @('RECEPTIONIST'); hospitalId = $h } $null | Out-Null
$rec = (Api POST '/api/v1/auth/login' @{ username = $recEmail; password = 'Passw0rd!123' } $null).data.accessToken
$recFEmail = "queue-foreign-$s@test.local"
Api POST '/api/v1/auth/register' @{ email = $recFEmail; password = 'Passw0rd!123'; firstName = 'For'; lastName = "Eign$s"; roles = @('RECEPTIONIST'); hospitalId = $hF } $null | Out-Null
$recF = (Api POST '/api/v1/auth/login' @{ username = $recFEmail; password = 'Passw0rd!123' } $null).data.accessToken
Check 'receptionists log in' ($null -ne $rec -and $null -ne $recF)

# 3 patients (admin is not hospital-bound, so the explicit hospitalId pins the rows)
$pa = (Api POST '/api/v1/patients' @{ firstName = 'Queue'; lastName = "A$s"; gender = 'female'; dateOfBirth = '1990-02-02'; hospitalId = $h } $admin).data.patient.id
$pb = (Api POST '/api/v1/patients' @{ firstName = 'Queue'; lastName = "B$s"; gender = 'male'; dateOfBirth = '1991-03-03'; hospitalId = $h } $admin).data.patient.id
$pc = (Api POST '/api/v1/patients' @{ firstName = 'Queue'; lastName = "C$s"; gender = 'female'; dateOfBirth = '1992-04-04'; hospitalId = $h } $admin).data.patient.id
$pd = (Api POST '/api/v1/patients' @{ firstName = 'Queue'; lastName = "D$s"; gender = 'male'; dateOfBirth = '1993-05-05'; hospitalId = $h } $admin).data.patient.id
Check 'patients created' ($null -ne $pa -and $null -ne $pb -and $null -ne $pc -and $null -ne $pd)

# 4 queue config doc: vip ranked ABOVE emergency proves the board is config-driven
# (built-in fallback would put emergency first); avg 10 min feeds the wait estimate.
$cfg = Api PUT "/api/v1/hospital-config/hospitals/$h/config/queue" @{
  queues = @(
    @{ code = 'consult'; name = 'Consultation'; strategy = 'priority'; avgConsultMinutes = 10; priorityClasses = @('vip', 'emergency', 'routine') },
    @{ code = 'lab'; name = 'Laboratory'; strategy = 'fifo'; avgConsultMinutes = 5 }
  )
} $admin
Check 'queue config published' ($null -ne $cfg.data.id)

# 5 tokens + estimated wait (waiting-ahead count x avg consult minutes: 0/10/20)
$a = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pa; priority = 'routine' } $rec).data
$b = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pb; priority = 'vip' } $rec).data
$c = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pc; priority = 'emergency' } $rec).data
Check 'daily token sequence C-01' ($a.tokenNumber -eq 'C-01')
Check 'estimated wait 0/10/20 (avg 10)' ($a.estimatedWaitMinutes -eq 0 -and $b.estimatedWaitMinutes -eq 10 -and $c.estimatedWaitMinutes -eq 20)
Check 'QR queue id payload' ($null -ne $a.qrPayload -and $a.qrPayload -like "SQM:${h}:consult:C-01:*")

# 6 board order follows the CONFIG priorityClasses (vip before emergency)
$board = (Api GET '/api/v1/queue/board?queueType=consult' $null $rec).data
Check 'config-driven board order [C-02, C-03, C-01]' ((($board | ForEach-Object { $_.tokenNumber }) -join ',') -eq 'C-02,C-03,C-01')

# 7 call-next pulls the highest-ranked waiting entry and stamps the call
$next = (Api POST '/api/v1/queue/entries/call-next' @{ queueType = 'consult' } $rec).data
Check 'callNext honors config order (C-02 vip)' ($next.tokenNumber -eq 'C-02' -and $next.status -eq 'called')

# 8 transfer: waiting entry moves to lab with a fresh token from the target sequence
$tr = (Api POST '/api/v1/queue/entries/consult/C-01/transfer' @{ queueType = 'lab' } $rec).data
Check 'transfer issues fresh target token L-01' ($tr.tokenNumber -eq 'L-01' -and $tr.queueType -eq 'lab')
$labBoard = (Api GET '/api/v1/queue/board?queueType=lab' $null $rec).data
Check 'transferred entry on the lab board' ((($labBoard | ForEach-Object { $_.tokenNumber }) -join ',') -eq 'L-01')

# 9 state guard: a called entry cannot transfer (409 QUEUE_ENTRY_NOT_WAITING)
$badTr = Api POST '/api/v1/queue/entries/consult/C-02/transfer' @{ queueType = 'lab' } $rec
Check 'called entry transfer rejected (409)' ($badTr._status -eq 409)

# 10 re-print is stateless: same token, QR payload intact
$rp = (Api GET '/api/v1/queue/entries/lab/L-01/reprint' $null $rec).data
Check 're-print stateless + QR payload' ($rp.tokenNumber -eq 'L-01' -and $rp.qrPayload -like "SQM:${h}:lab:L-01:*")

# 11 emergency bump: priority raised to emergency (displays get queue.bumped over WS)
$d = (Api POST '/api/v1/queue/entries' @{ queueType = 'consult'; patientId = $pd; priority = 'routine' } $rec).data
$bp = (Api POST '/api/v1/queue/entries/consult/C-04/bump' $null $rec).data
Check 'bump raises priority to emergency' ($bp.priority -eq 'emergency')

# 12 event fabric: bumped/called/transferred stamped to the outbox for displays
$outbox = (Api GET '/api/v1/admin/events/outbox?limit=50' $null $admin).data
$ev = @($outbox | Where-Object { $_.eventType -like 'queue.*' })
Check 'queue.* events in the outbox' ($ev.Count -ge 3 -and $null -ne ($ev | Where-Object { $_.eventType -eq 'queue.bumped' }))

# 13 RBAC: receptionist manages, another hospital's receptionist is walled off
$fRead = Api GET '/api/v1/queue/entries/lab/L-01' $null $recF
$fBoard = Api GET '/api/v1/queue/board?queueType=consult' $null $recF
$fBump = Api POST '/api/v1/queue/entries/consult/C-04/bump' $null $recF
Check 'cross-tenant read denied' ($fRead._status -eq 400)
Check 'cross-tenant board shows no foreign rows (200 empty)' (@($fBoard.data).Count -eq 0)
Check 'cross-tenant bump denied' ($fBump._status -eq 400)

# 14 catalog carries the three queue events
$catalog = Api GET '/api/v1/events/catalog' $null $null
$queueEvents = @($catalog.data | Where-Object { $_.name -like 'Queue*' })
Check 'catalog carries queue.* events (3)' ($queueEvents.Count -eq 3)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
