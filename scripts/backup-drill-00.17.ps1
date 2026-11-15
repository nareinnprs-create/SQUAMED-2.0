# 00.17 backup + restore drill - 09-INFRASTRUCTURE section 4 + chunk-00 exit line:
# backup taken, restored to a scratch database, restore time measured and reported,
# restored data verified against the source (row counts + content checksums), scratch dropped.
# Uses the running compose postgres (docker exec) - no extra tooling required. ASCII only.
$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0

function Check($name, $ok, $detail = '') {
  if ($ok) { $script:pass++; Write-Output "PASS: $name" }
  else { $script:fail++; Write-Output "FAIL: $name $detail" }
}

function Psql($sql) {
  docker exec squamed-postgres psql -U squamed -d squamed -t -A -c $sql
}

Write-Output "== 00.17 backup + restore drill =="

# 1 pre-flight: database reachable, source row counts captured
$dbUp = (docker exec squamed-postgres pg_isready -U squamed -d squamed) -match 'accepting connections'
Check 'postgres reachable' ($dbUp -eq $true)
$srcPatients = Psql 'SELECT count(*) FROM patient.patient_record'
$srcUsers = Psql 'SELECT count(*) FROM identity."user"'
$srcAudit = Psql 'SELECT count(*) FROM audit.audit_log'
Check 'source row counts captured' ([long]$srcPatients -gt 0 -and [long]$srcUsers -gt 0 -and [long]$srcAudit -gt 0) "patients=$srcPatients users=$srcUsers audit=$srcAudit"

# 2 backup: pg_dump custom format, timed
$dumpStart = Get-Date
$null = docker exec squamed-postgres pg_dump -U squamed -d squamed -Fc -f /tmp/squamed-drill.dump
$dumpMs = ((Get-Date) - $dumpStart).TotalMilliseconds
$dumpBytes = docker exec squamed-postgres sh -c 'stat -c %s /tmp/squamed-drill.dump'
Check 'backup created (custom format)' ([long]$dumpBytes -gt 1000000) "bytes=$dumpBytes time=$([math]::Round($dumpMs / 1000, 1))s"

# 3 restore: scratch database, timed (drop leftovers first)
$scratch = 'squamed_drill_0017'
$null = docker exec squamed-postgres psql -U squamed -d postgres -c "DROP DATABASE IF EXISTS $scratch;"
$restoreStart = Get-Date
$null = docker exec squamed-postgres psql -U squamed -d postgres -c "CREATE DATABASE $scratch OWNER squamed;"
$restoreLog = docker exec squamed-postgres pg_restore -U squamed -d $scratch --no-owner --exit-on-error /tmp/squamed-drill.dump 2>&1
$restoreMs = ((Get-Date) - $restoreStart).TotalMilliseconds
Write-Output ("restore time: {0:F1} s (into scratch db {1})" -f ($restoreMs / 1000), $scratch)
Check 'restore completed without errors' ($LASTEXITCODE -eq 0) ($restoreLog | Select-Object -First 3)

function RPsql($sql) {
  docker exec squamed-postgres psql -U squamed -d $scratch -t -A -c $sql
}

# 4 verify: row counts + content checksums (md5 over ordered ids) match the source
$rstPatients = RPsql 'SELECT count(*) FROM patient.patient_record'
$rstUsers = RPsql 'SELECT count(*) FROM identity."user"'
$rstAudit = RPsql 'SELECT count(*) FROM audit.audit_log'
Check 'patient count matches' ($rstPatients -eq $srcPatients) "src=$srcPatients restored=$rstPatients"
Check 'user count matches' ($rstUsers -eq $srcUsers) "src=$srcUsers restored=$rstUsers"
Check 'audit log count matches' ($rstAudit -eq $srcAudit) "src=$srcAudit restored=$rstAudit"
$srcMd5 = Psql 'SELECT md5(string_agg(id::text, '','' ORDER BY id)) FROM patient.patient_record'
$rstMd5 = RPsql 'SELECT md5(string_agg(id::text, '','' ORDER BY id)) FROM patient.patient_record'
Check 'patient ids content-identical (md5)' ($srcMd5 -eq $rstMd5) "src=$($srcMd5.Substring(0,8)) restored=$($rstMd5.Substring(0,8))"
$srcChain = Psql 'SELECT count(*) FROM audit.chain_head'
$rstChain = RPsql 'SELECT count(*) FROM audit.chain_head'
Check 'audit chain heads restored' ($srcChain -eq $rstChain -and [long]$rstChain -gt 0) "heads=$rstChain"
$srcSchema = Psql "SELECT count(*) FROM information_schema.tables WHERE table_schema IN ('identity','patient','audit','tasks','events','consents','notifications')"
$rstSchema = RPsql "SELECT count(*) FROM information_schema.tables WHERE table_schema IN ('identity','patient','audit','tasks','events','consents','notifications')"
Check 'schema object count matches' ($srcSchema -eq $rstSchema) "src=$srcSchema restored=$rstSchema"

# 5 WORM evidence + scratch cleanup
$exportBuckets = Psql "SELECT count(*) FROM audit.export_cursor"
Write-Output "export cursors (WORM continuity): $exportBuckets"
$null = docker exec squamed-postgres psql -U squamed -d postgres -c "DROP DATABASE IF EXISTS $scratch;"
$null = docker exec squamed-postgres psql -U squamed -d postgres -c "DROP DATABASE IF EXISTS squamed_restore_probe;"
Check 'scratch database dropped' ([string]::IsNullOrEmpty((Psql "SELECT 1 FROM pg_database WHERE datname = '$scratch'")))

Write-Output ""
Write-Output ("backup size: {0} bytes, backup time: {1:F1} s, restore time: {2:F1} s" -f $dumpBytes, ($dumpMs / 1000), ($restoreMs / 1000))
Write-Output "== backup drill: $pass passed, $fail failed =="
if ($fail -gt 0) { exit 1 }
