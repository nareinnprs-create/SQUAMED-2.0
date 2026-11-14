#!/usr/bin/env pwsh
# 00.02 contract-foundation exit-demo slice (gate 12).
# Drives the live contract-foundation surfaces: idempotency replay + conflict through the
# public REST API, and the committed-snapshot stamp integrity (offline checks).
# Usage: pwsh scripts/demo-00.02-contract-foundation.ps1 [-BaseUrl http://localhost:8081]
# Requires a running squamed-core instance; exits non-zero if any check fails.

param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "admin@squamed.local",
    [string]$Password = "Admin@12345"
)

$script:Passed = 0
$script:Failed = 0

function Assert-Check {
    param([string]$Name, [bool]$Ok)
    if ($Ok) {
        $script:Passed++
        Write-Host "PASS  $Name"
    } else {
        $script:Failed++
        Write-Host "FAIL  $Name" -ForegroundColor Red
    }
}

function Send-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [hashtable]$ExtraHeaders = @{}
    )
    $uri = "$BaseUrl$Path"
    $headers = @{ Authorization = "Bearer $($script:Token)" } + $ExtraHeaders
    $params = @{
        Method               = $Method
        Uri                  = $uri
        Headers              = $headers
        ContentType          = "application/json"
        TimeoutSec           = 15
        SkipHttpErrorCheck   = $true
    }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 8) }
    $resp = Invoke-WebRequest @params -UseBasicParsing
    return @{ status = [int]$resp.StatusCode; body = $resp.Content }
}

# --- auth ---
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)
$script:Token = $login.data.accessToken
Assert-Check "login" ($null -ne $script:Token)

# --- idempotent replay: same key + same body replays the first response ---
$suffix = "$(Get-Random -Maximum 999999)"
$key = "demo-00.02-$suffix"
$patientA = @{ firstName = "Demo"; lastName = "Replay$suffix"; gender = "male"; dateOfBirth = "1990-01-01" }
$first = Send-Api -Method Post -Path "/api/v1/patients" -Body $patientA -ExtraHeaders @{ "Idempotency-Key" = $key }
Assert-Check "first POST creates the patient (2xx)" ($first.status -ge 200 -and $first.status -lt 300)
$firstId = ($first.body | ConvertFrom-Json).data.patient.id

$replay = Send-Api -Method Post -Path "/api/v1/patients" -Body $patientA -ExtraHeaders @{ "Idempotency-Key" = $key }
$replayId = ($replay.body | ConvertFrom-Json).data.patient.id
Assert-Check "retry with same key replays the same patient id ($firstId)" ($replay.status -eq $first.status -and $replayId -eq $firstId -and $null -ne $firstId)

# --- conflict: same key + different payload ---
$conflict = Send-Api -Method Post -Path "/api/v1/patients" -Body @{ firstName = "Conflict"; lastName = "Replay$suffix"; gender = "male"; dateOfBirth = "1990-01-01" } -ExtraHeaders @{ "Idempotency-Key" = $key }
$conflictCode = ($conflict.body | ConvertFrom-Json).error.code
Assert-Check "same key + different payload = 409 IDEMPOTENCY_CONFLICT" ($conflict.status -eq 409 -and $conflictCode -eq "IDEMPOTENCY_CONFLICT")

# --- contract snapshot stamp integrity (offline) ---
$snapshotPath = Join-Path $PSScriptRoot "..\docs\contracts\openapi-v1.json"
$generatedPath = Join-Path $PSScriptRoot "..\packages\api-client\src\generated\schema.d.ts"
$snapshotHash = (Get-FileHash $snapshotPath -Algorithm SHA256).Hash.ToLowerInvariant()
$stampLine = (Select-String -Path $generatedPath -Pattern 'openapi snapshot sha256: ([0-9a-f]{64})').Matches[0].Groups[1].Value
Assert-Check "generated client carries the committed snapshot sha256" ($snapshotHash -eq $stampLine)

Write-Host ""
Write-Host "00.02 exit-demo slice: $Passed passed, $Failed failed"
exit ([int]($Failed -gt 0))
