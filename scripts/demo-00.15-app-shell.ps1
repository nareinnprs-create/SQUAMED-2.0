# Exit-demo slice for section 00.15 (App shell): nav rail, hospital selector,
# universal search (OpenSearch), patient-context bar, task inbox, notifications
# bell, user/security menu with the en/hi language toggle, sign out - driven
# through the real UI (Playwright chromium) against live backend 8081 + web 3100.
#
# Ports: host 3000 is squatted by an unrelated docker container (mythic_react)
# and host 8080 by mythic_graphql (noted in 00.04), so the demo serves the web
# production build on 3100, rebuilt with NEXT_PUBLIC_API_BASE_URL=8081, and the
# backend CORS list extended with 3100. Port owners of 3100/8081 are killed
# after the run (pm2 and docker stay untouched). ASCII only.
$ErrorActionPreference = 'Stop'
$script:pass = 0
$script:fail = 0
$webPort = 3100
$backendPort = 8081
$nextBin = Resolve-Path "..\node_modules\next\dist\bin\next" -ErrorAction SilentlyContinue
if (-not $nextBin) { $nextBin = "C:\Users\manag\Squamed 2.0\node_modules\next\dist\bin\next" }
$webRoot = "C:\Users\manag\Squamed 2.0\apps\web"
$backendRoot = "C:\Users\manag\Squamed 2.0\backend\squamed-core"

function Check($name, $ok) {
  if ($ok) { $script:pass++; Write-Output "PASS $name" }
  else { $script:fail++; Write-Output "FAIL $name" }
}

function Probe($url) {
  try {
    $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
    return ($r.StatusCode -eq 200)
  } catch { return $false }
}

function Kill-PortOwnerTree($port) {
  Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object {
      $proc = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
      if ($proc -and $proc.ProcessName -notin @('com.docker.backend', 'pm2')) {
        taskkill /PID $proc.Id /T /F 2>$null | Out-Null
        Write-Output "killed port $port owner pid=$($proc.Id) $($proc.ProcessName) (tree)"
      }
    }
}

Write-Output "== 00.15 app shell demo (8081 + $webPort) =="

# 1 backend on 8081 (poll first; boot when the probe fails)
if (-not (Probe "http://localhost:$backendPort/api/v1/events/catalog")) {
  Write-Output "booting backend (SERVER_PORT=$backendPort bootRun)..."
  Kill-PortOwnerTree $backendPort
  $env:SERVER_PORT = "$backendPort"
  $env:SPRING_PROFILES_ACTIVE = 'dev'
  $env:CORS_ALLOWED_ORIGINS = "http://localhost:3000,http://localhost:3001,http://localhost:$webPort"
  Start-Process -FilePath "$backendRoot\gradlew.bat" -ArgumentList 'bootRun', '--no-daemon', '-q' `
    -WorkingDirectory $backendRoot -WindowStyle Hidden `
    -RedirectStandardOutput "$env:TEMP\backend-0015-demo.log" `
    -RedirectStandardError "$env:TEMP\backend-0015-demo.err.log"
}
$backendUp = $false
$deadline = (Get-Date).AddSeconds(240)
while ((Get-Date) -lt $deadline) {
  if (Probe "http://localhost:$backendPort/api/v1/events/catalog") { $backendUp = $true; break }
  Start-Sleep -Seconds 5
}
Check 'backend serves api (8081)' $backendUp

# 2 web on 3100: production build with the API base baked to 8081
if (-not (Probe "http://localhost:$webPort/login")) {
  Kill-PortOwnerTree $webPort
  Write-Output "building web (NEXT_PUBLIC_API_BASE_URL=http://localhost:$backendPort/api/v1)..."
  $env:NEXT_PUBLIC_API_BASE_URL = "http://localhost:$backendPort/api/v1"
  Push-Location $webRoot
  node "$nextBin" build 2>&1 | Out-Null
  Pop-Location
  Write-Output "starting web (next start -p $webPort)..."
  Start-Process -FilePath "node" -ArgumentList "`"$nextBin`"", 'start', '-p', "$webPort" `
    -WorkingDirectory $webRoot -WindowStyle Hidden `
    -RedirectStandardOutput "$env:TEMP\web-0015-demo.log" `
    -RedirectStandardError "$env:TEMP\web-0015-demo.err.log"
}
$webUp = $false
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
  if (Probe "http://localhost:$webPort/login") { $webUp = $true; break }
  Start-Sleep -Seconds 3
}
Check "web serves login ($webPort)" $webUp

# 3 UI slice via the Playwright driver (its PASS/FAIL lines print below)
if ($backendUp -and $webUp) {
  node scripts\demo-00.15-driver.mjs
  if ($LASTEXITCODE -eq 0) { $script:pass++ } else { $script:fail++ }
} else {
  Write-Output "FAIL ui slice (servers not ready)"
  $script:fail++
}

# 4 cleanup: kill the port owners of the demo servers only (leave pm2 + docker)
Kill-PortOwnerTree $webPort
Kill-PortOwnerTree $backendPort

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
