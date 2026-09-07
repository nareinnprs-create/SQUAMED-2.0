#!/usr/bin/env pwsh
# SQUAMED Phase 1 end-to-end smoke test.
# Walks every Phase 1 module via the public REST API and asserts invariants.
# Usage: pwsh scripts/phase1-e2e.ps1 [-BaseUrl http://localhost:8080] [-Username admin@squamed.local] [-Password Admin@12345]
# Exits non-zero if any check fails. Safe to re-run (uses unique data).

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin@squamed.local",
    [string]$Password = "Admin@12345"
)

$script:Passed = 0
$script:Failed = 0
$script:Log = [System.Collections.Generic.List[string]]::new()

$headers = @{}

function Send-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body
    )
    $uri = "$BaseUrl$Path"
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $headers
        ContentType = "application/json"
        TimeoutSec = 15
    }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 8) }
    try {
        $resp = Invoke-RestMethod @params
        return @{ ok = $true; data = $resp.data }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        return @{ ok = $false; status = $status; message = $_.Exception.Message }
    }
}

function Assert-Check {
    param([string]$Name, [bool]$Condition)
    if ($Condition) {
        $script:Passed++
        Write-Host "  PASS | $Name" -ForegroundColor Green
        $script:Log.Add("PASS | $Name") | Out-Null
    } else {
        $script:Failed++
        Write-Host "  FAIL | $Name" -ForegroundColor Red
        $script:Log.Add("FAIL | $Name") | Out-Null
    }
}

function Note-Assert {
    param([string]$Name)
    $script:Passed++
    Write-Host "  PASS | $Name" -ForegroundColor Green
    $script:Log.Add("PASS | $Name") | Out-Null
}

Write-Host "== SQUAMED Phase 1 E2E (base: $BaseUrl) ==" -ForegroundColor Cyan

# ---------------------------------------------------------------- auth
Write-Host "`n> Auth / RBAC" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = $Username; password = $Password }
Assert-Check "login with bootstrap admin" $r.ok
if (-not $r.ok) {
    Write-Host "Cannot continue without a session." -ForegroundColor Red
    Write-Host ("  trace: {0}" -f $r.message)
    exit 1
}
$token = $r.data.accessToken
$userId = $r.data.userId
if (-not $userId) { $userId = $r.data.user.id }
$headers["Authorization"] = "Bearer $token"

$r = Send-Api -Method Get -Path "/api/v1/auth/me"
Assert-Check "GET /auth/me returns identity" ($r.ok -and $null -ne $r.data)
$me = $r.data

# unauthenticated access must be rejected
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/patients" -Method Get -TimeoutSec 10 | Out-Null
    Assert-Check "unauth request to /patients blocked" $false
} catch {
    Assert-Check "unauth request to /patients blocked" ($_.Exception.Response -ne $null)
}

# ------------------------------------------------------- hospital config
Write-Host "`n> Hospital Configuration" -ForegroundColor Yellow
$r = Send-Api -Method Get -Path "/api/v1/hospital-config/hospitals"
Assert-Check "GET hospitals" $r.ok
$hospitals = @($r.data)
Assert-Check "at least one hospital exists" ($hospitals.Count -ge 1)
$hospitalId = $hospitals[0].id

$r = Send-Api -Method Get -Path "/api/v1/hospital-config/hospitals/$hospitalId/departments"
Assert-Check "GET departments" $r.ok
$departments = @($r.data)
$departmentId = $null
if ($departments.Count -ge 1) { $departmentId = $departments[0].id }
else {
    $r = Send-Api -Method Post -Path "/api/v1/hospital-config/hospitals/$hospitalId/departments" -Body @{
        name = "Smoke Test Dept"; code = "E2E-$([DateTime]::Now.Ticks)"; type = "OPD"
    }
    if ($r.ok) { $departmentId = $r.data.id }
    Assert-Check "create department if missing" ($null -ne $departmentId)
}
$r = Send-Api -Method Get -Path "/api/v1/hospital-config/hospitals/$hospitalId/tree"
Assert-Check "GET hospital tree" $r.ok

# ----------------------------------------------------------- master data
Write-Host "`n> Master Data" -ForegroundColor Yellow
$r = Send-Api -Method Get -Path "/api/v1/master-data/drugs"
Assert-Check "GET drugs catalog" $r.ok
$drugs = @($r.data)
$drugId = $null
if ($drugs.Count -ge 1) { $drugId = $drugs[0].id }
else {
    $r = Send-Api -Method Post -Path "/api/v1/master-data/drugs" -Body @{
        code = "E2EDRUG"; name = "Smoke Test Drug"; category = "General"; unit = "tab"
    }
    if ($r.ok) { $drugId = $r.data.id }
    Assert-Check "create drug if missing" ($null -ne $drugId)
}

$r = Send-Api -Method Get -Path "/api/v1/master-data/lab-tests"
Assert-Check "GET lab-tests catalog" $r.ok
$labTests = @($r.data)
$labTest = $null
if ($labTests.Count -ge 1) { $labTest = $labTests[0] }
else {
    $r = Send-Api -Method Post -Path "/api/v1/master-data/lab-tests" -Body @{
        code = "SMO"; name = "Smoke CBC"; category = "Hematology"; unit = "x10^9/L"
    }
    if ($r.ok) { $labTest = $r.data }
    Assert-Check "create lab-test if missing" ($null -ne $labTest)
}

# -------------------------------------------------------------- patient
Write-Host "`n> Patient / EMPI" -ForegroundColor Yellow
$suffix = ([DateTime]::Now.ToString("HHmmssfff"))
$patient = @{
    firstName = "E2E"; lastName = "Smoke$suffix"; gender = "male"
    dateOfBirth = "1992-05-15"; phone = "55$([DateTime]::Now.ToString('MMddHHmmss'))"
    qid = "2$([DateTime]::Now.ToString('9MMddHHmmss'))"; nationality = "Qatar"
    city = "Doha"
}
$r = Send-Api -Method Post -Path "/api/v1/patients" -Body $patient
Assert-Check "create patient" $r.ok
if (-not $r.ok) { Write-Host ("  {0}" -f $r.message) }
$patientId = $r.data.patient.id
$mrn = $r.data.patient.mrn
Assert-Check "MRN generated (MQ-YYYY-####)" ($mrn -match "^MQ-\d{4}-\d{4}$")

$r = Send-Api -Method Get -Path "/api/v1/patients?query=Smoke$suffix"
Assert-Check "search patients by name" ($r.ok -and (@($r.data).Count -ge 1))

$r = Send-Api -Method Post -Path "/api/v1/patients/match" -Body @{
    firstName = "E2E"; lastName = "Smoke$suffix"; gender = "male"; dateOfBirth = "1992-05-15"; qid = $patient.qid
}
Assert-Check "EMPI match endpoint returns candidates" ($r.ok -and $null -ne $r.data)

$r = Send-Api -Method Post -Path "/api/v1/patients/$patientId/allergies" -Body @{
    allergen = "Penicillin"; reaction = "Rash"; severity = "moderate"
}
Assert-Check "add allergy" $r.ok

# ----------------------------------------------------------- scheduling
Write-Host "`n> Appointments (Scheduling)" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/appointments" -Body @{
    patientId = $patientId
    departmentId = $departmentId
    doctorId = $userId
    appointmentType = "OPD"
    scheduledStart = [DateTimeOffset]::UtcNow.AddHours(1).ToString("o")
    scheduledEnd = [DateTimeOffset]::UtcNow.AddHours(2).ToString("o")
    durationMinutes = 30
    reason = "E2E smoke appointment"
    priority = "normal"
}
Assert-Check "create appointment" $r.ok
$appointmentId = $r.data.id
Assert-Check "appointment created with id" ($null -ne $appointmentId)

$r = Send-Api -Method Get -Path "/api/v1/appointments?patientId=$patientId" -ErrorAction SilentlyContinue
if ($r.ok) {
    Assert-Check "list appointments" ($null -ne $r.data)
} else {
    $list = Send-Api -Method Get -Path "/api/v1/appointments"
    Assert-Check "list appointments (fallback)" $list.ok
}

# -------------------------------------------------------- reception/queue
Write-Host "`n> Reception + Queue" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/reception/visits" -Body @{
    patientId = $patientId
    appointmentId = $appointmentId
    visitType = "OPD"
    departmentId = $departmentId
    reason = "E2E smoke visit"
    priority = "normal"
    addToQueue = $true
    queueType = "OPD"
}
Assert-Check "check-in visit (VN-...)" ($r.ok -and $r.data.visit.visitNumber -match "^VN-")
if ($r.ok) {
    $visitId = $r.data.visit.id
    $token = $r.data.queueEntry.tokenNumber
    $queueType = $r.data.queueEntry.queueType
    Assert-Check "queue token issued (R-...)" ($token -match "^[A-Z]-\d+$")
} else {
    $visitId = $null; $token = $null; $queueType = "OPD"
    $r = Send-Api -Method Post -Path "/api/v1/reception/visits" -Body @{
        patientId = $patientId; visitType = "OPD"; departmentId = $departmentId; reason = "E2E smoke"; priority = "normal"
    }
    if ($r.ok) { $visitId = $r.data.visit.id }
}

$board = Send-Api -Method Get -Path "/api/v1/queue/board?queueType=OPD"
Assert-Check "GET queue board" $board.ok

if ($token) {
    $r = Send-Api -Method Patch -Path "/api/v1/queue/entries/$queueType/$token/start" -Body @{ servedBy = $userId }
    Assert-Check "queue entry start" $r.ok
    $stats = Send-Api -Method Get -Path "/api/v1/queue/statistics?queueType=OPD"
    Assert-Check "GET queue statistics" $stats.ok
}

# ------------------------------------------------------------ encounter
Write-Host "`n> Encounter / OPD" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/encounters" -Body @{
    patientId = $patientId
    visitId = $visitId
    departmentId = $departmentId
    chiefComplaint = "Fever and cough for 3 days"
    reason = "Outpatient consultation"
}
Assert-Check "start encounter" $r.ok
$encounterId = $r.data.id

$r = Send-Api -Method Post -Path "/api/v1/encounters/$encounterId/vitals" -Body @{
    bloodPressureSystolic = 120; bloodPressureDiastolic = 80
    heartRate = 88; respiratoryRate = 18; temperatureC = 37.8; spo2Percent = 98
    painScore = 3
}
Assert-Check "record vitals" $r.ok

$r = Send-Api -Method Post -Path "/api/v1/encounters/$encounterId/notes" -Body @{
    noteType = "SOAP"; title = "Initial consult"
    subjective = "Cough, mild fever"; objective = "Vitals recorded"; assessment = "URI suspect"; plan = "CBC + symptomatic"
}
Assert-Check "add SOAP note" $r.ok
if ($r.ok) {
    $noteId = $r.data.id
    $sign = Send-Api -Method Post -Path "/api/v1/encounters/notes/$noteId/sign" -Body @{ }
    Assert-Check "sign note" ($sign.ok -or ($sign.status -eq 409))
}

$r = Send-Api -Method Post -Path "/api/v1/encounters/$encounterId/prescriptions" -Body @{
    drugName = "Smoke Test Drug"; strength = "500 mg"; form = "tablet"
    dose = "1 tab"; frequency = "TID"; route = "oral"; quantity = 10
}
Assert-Check "prescribe medication" $r.ok
if ($r.ok -and $drugId) {
    $r2 = Send-Api -Method Post -Path "/api/v1/encounters/$encounterId/prescriptions" -Body @{
        drugId = $drugId; drugName = "Smoke Test Drug"; frequency = "BID"; quantity = 20
    }
    Assert-Check "prescribe with drugId" $r2.ok
}

# ------------------------------------------------------------------ lab
Write-Host "`n> Laboratory" -ForegroundColor Yellow
if ($labTest) {
    $testCode = $labTest.code
    $testName = $labTest.name
    $r = Send-Api -Method Post -Path "/api/v1/lab/orders" -Body @{
        patientId = $patientId
        encounterId = $encounterId
        tests = @(@{ testCode = $testCode; testName = $testName })
        priority = "routine"
        clinicalNote = "E2E smoke order"
        orderedBy = $userId
    }
    Assert-Check "create lab order (LO-...)" ($r.ok -and $r.data.order.orderNumber -match "^LO-")
    if ($r.ok) {
        $orderId = $r.data.order.id
        Assert-Check "lab order created with id" ($null -ne $orderId)
        $det = Send-Api -Method Get -Path "/api/v1/lab/orders/$orderId"
        $testRequestId = $det.data.tests[0].id
        Assert-Check "order detail has test request" ($null -ne $testRequestId)

        $c = Send-Api -Method Post -Path "/api/v1/lab/orders/$orderId/collect" -Body @{ collectedBy = $userId }
        Assert-Check "collect specimen" $c.ok
        $rec = Send-Api -Method Post -Path "/api/v1/lab/orders/$orderId/receive" -Body @{ }
        Assert-Check "receive specimen" $rec.ok
        $st = Send-Api -Method Post -Path "/api/v1/lab/orders/$orderId/start" -Body @{ }
        Assert-Check "start analysis" $st.ok
        $res = Send-Api -Method Post -Path "/api/v1/lab/orders/$orderId/results" -Body @{
            results = @(@{ testRequestId = $testRequestId; resultValue = "5.2"; unit = $labTest.unit; referenceRange = $labTest.referenceRange })
        }
        Assert-Check "enter results (auto isAbnormal)" ($res.ok -and $null -ne $res.data)
        $ver = Send-Api -Method Post -Path "/api/v1/lab/orders/$orderId/verify" -Body @{ }
        Assert-Check "verify results" $ver.ok
        $rel = Send-Api -Method Post -Path "/api/v1/lab/orders/$orderId/release" -Body @{ }
        Assert-Check "release results" ($rel.ok -and $rel.data.order.resultStatus -eq "released")
    }
} else {
    Assert-Check "create lab order" $false
}

# -------------------------------------------------------------- pharmacy
Write-Host "`n> Pharmacy" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/pharmacy/stock" -Body @{
    drugId = $drugId
    drugName = "Smoke Test Drug"
    batchNumber = "E2E-B"
    quantityOnHand = 100
    reorderLevel = 10
    unitPrice = 12.5
    location = "A-1"
}
Assert-Check "create stock record (100 units)" $r.ok
if ($r.ok) {
    $stockId = $r.data.id
    $stockQtyBefore = [int]$r.data.quantityOnHand
    $d = Send-Api -Method Post -Path "/api/v1/pharmacy/dispensations" -Body @{
        patientId = $patientId
        encounterId = $encounterId
        stockId = $stockId
        drugId = $drugId
        drugName = "Smoke Test Drug"
        quantity = 5
        unitPrice = 12.5
    }
    Assert-Check "dispense 5 units" ($d.ok -and $d.data.status -eq "dispensed")
    if ($d.ok) {
        $dispenseId = $d.data.id
        $drop = Send-Api -Method Post -Path "/api/v1/pharmacy/dispensations/$dispenseId/verify" -Body @{ }
        Assert-Check "verify dispensation" $drop.ok
        $stock = Send-Api -Method Get -Path "/api/v1/pharmacy/stock"
        $current = @($stock.data) | Where-Object { $_.id -eq $stockId } | Select-Object -First 1
        Assert-Check "stock decremented by dispensed qty" ($current -and ([int]$current.quantityOnHand -eq ($stockQtyBefore - 5)))
    }
    $over = Send-Api -Method Post -Path "/api/v1/pharmacy/dispensations" -Body @{
        patientId = $patientId; stockId = $stockId; drugId = $drugId
        drugName = "Smoke Test Drug"; quantity = 99999
    }
    $overRejected = (-not $over.ok) -and ($over.status -eq 409 -or $over.status -eq 400)
    Assert-Check "oversell rejected (INSUFFICIENT_STOCK)" $overRejected
} else {
    Assert-Check "dispense 5 units" $false
    Assert-Check "oversell rejected" $false
}

# --------------------------------------------------------------- billing
Write-Host "`n> Billing" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/billing/tariffs" -Body @{
    code = "E2E-CONSULT"; name = "E2E Consultation"; category = "consultation"; priceQar = 200; insuranceApplicable = $false
}
Assert-Check "create tariff" $r.ok

$r = Send-Api -Method Post -Path "/api/v1/billing/charges" -Body @{
    patientId = $patientId
    encounterId = $encounterId
    chargeCode = "E2E-CONSULT"
    chargeName = "E2E Consultation"
    quantity = 1
    unitPriceQar = 200
    insuranceClaimable = $false
}
Assert-Check "post charge (200 QAR)" $r.ok

$r = Send-Api -Method Post -Path "/api/v1/billing/invoices" -Body @{ patientId = $patientId; encounterId = $encounterId }
Assert-Check "issue invoice (INV-...)" ($r.ok -and $r.data.invoice.invoiceNumber -match "^INV-")
if ($r.ok) {
    $invoiceId = $r.data.invoice.id
    $due = [decimal]$r.data.invoice.amountDueQar
    Assert-Check "invoice unpaid (due > 0)" ($due -gt 0)
    $p = Send-Api -Method Post -Path "/api/v1/billing/invoices/$invoiceId/payments" -Body @{
        method = "CASH"; amountQar = $due; reference = "E2E-PAY"
    }
    Assert-Check "pay invoice in full" ($p.ok -and $p.data.status -eq "completed")
    $inv2 = Send-Api -Method Get -Path "/api/v1/billing/invoices/$invoiceId"
    Assert-Check "invoice settled (status paid, due 0)" ($inv2.data.invoice.status -eq "paid" -and [decimal]$inv2.data.invoice.amountDueQar -eq 0)
}

# ----------------------------------------------------------------- tasks
Write-Host "`n> Tasks / Notifications / Realtime" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/tasks" -Body @{
    title = "Follow up E2E patient"
    description = "Smoke task created by integration test"
    priority = "normal"
    relatedType = "PATIENT"
    relatedId = $patientId
    assigneeId = $userId
    dueAt = [DateTimeOffset]::UtcNow.AddDays(1).ToString("o")
}
Assert-Check "create task" $r.ok
if ($r.ok) {
    $taskId = $r.data.id
    $st = Send-Api -Method Patch -Path "/api/v1/tasks/$taskId/status" -Body @{ status = "in_progress" }
    Assert-Check "task -> in_progress" $st.ok
    $co = Send-Api -Method Patch -Path "/api/v1/tasks/$taskId/status" -Body @{ status = "completed" }
    Assert-Check "task -> completed" $co.ok
}

$nc = Send-Api -Method Get -Path "/api/v1/notifications/unread-count"
Assert-Check "GET notification unread-count" $nc.ok
$nl = Send-Api -Method Get -Path "/api/v1/notifications"
Assert-Check "GET notification list" $nl.ok
$read = Send-Api -Method Post -Path "/api/v1/notifications/read-all" -Body @{ }
Assert-Check "mark-all-read" $read.ok

# -------------------------------------------------------------- communication
Write-Host "`n> Communication (channels + messages)" -ForegroundColor Yellow
$r = Send-Api -Method Post -Path "/api/v1/communication/channels" -Body @{
    name = "E2E Ops Team"; type = "group"; memberIds = @($userId)
}
Assert-Check "create channel" $r.ok
if ($r.ok) {
    $channelId = $r.data.id
    $r2 = Send-Api -Method Get -Path "/api/v1/communication/channels"
    Assert-Check "list my channels includes new channel" ($r2.ok -and (@($r2.data) | Where-Object { $_.id -eq $channelId }))
    $m = Send-Api -Method Post -Path "/api/v1/communication/channels/$channelId/messages" -Body @{ body = "E2E hello"; messageType = "text" }
    Assert-Check "send channel message" $m.ok
    $l = Send-Api -Method Get -Path "/api/v1/communication/channels/$channelId/messages?limit=10"
    Assert-Check "list channel messages" ($l.ok -and (@($l.data).Count -ge 1))
    $read = Send-Api -Method Post -Path "/api/v1/communication/channels/$channelId/read" -Body @{ }
    Assert-Check "mark channel read" $read.ok
    $uc = Send-Api -Method Get -Path "/api/v1/communication/unread-count"
    Assert-Check "GET communication unread-count" ($uc.ok -and $null -ne $uc.data)
} else {
    Assert-Check "list my channels" $false
    Assert-Check "send channel message" $false
    Assert-Check "list channel messages" $false
    Assert-Check "mark channel read" $false
    Assert-Check "GET communication unread-count" $false
}

# ----------------------------------------------------------------- search
Write-Host "`n> OpenSearch Search" -ForegroundColor Yellow
Start-Sleep -Seconds 2
$r = Send-Api -Method Get -Path "/api/v1/search/search?q=Smoke$suffix"
Assert-Check "search finds created patient" ($r.ok -and (@($r.data.hits).Count -ge 1))
$r2 = Send-Api -Method Get -Path "/api/v1/search/search/indices"
Assert-Check "search indices endpoint" ($r2.ok -and $null -ne $r2.data)

# ---------------------------------------------------------- patient 360
Write-Host "`n> Patient 360" -ForegroundColor Yellow
$p360 = Send-Api -Method Get -Path "/api/v1/patient360/$patientId"
Assert-Check "GET patient360 aggregates" ($p360.ok -and $null -ne $p360.data.demographics.mrn)
if ($p360.ok) {
    Assert-Check "patient360 demographics id matches" ($p360.data.demographics.id -eq $patientId)
    Assert-Check "patient360 demo sections present" ($null -ne $p360.data.encounters -and $null -ne $p360.data.labOrders -and $null -ne $p360.data.tasks)
} else {
    Assert-Check "patient360 demographics id matches" $false
    Assert-Check "patient360 demo sections present" $false
}

# ----------------------------------------------------------------- audit
Write-Host "`n> Audit Trail" -ForegroundColor Yellow
$aud = Send-Api -Method Get -Path "/api/v1/audit/logs?entityType=Patient&limit=10"
Assert-Check "GET audit logs for Patient" ($aud.ok -and (@($aud.data).Count -ge 1))
$aud2 = Send-Api -Method Get -Path "/api/v1/audit/logs?limit=50"
Assert-Check "GET audit logs global" ($aud2.ok -and @($aud2.data).Count -ge 1)

# -------------------------------------------------------------- workflow
Write-Host "`n> Workflow Engine" -ForegroundColor Yellow
$wfSteps = @(
    @{ stepKey = "notify"; stepType = "NOTIFICATION"; sequenceOrder = 0; config = @{ recipientId = $userId; type = "workflow"; title = "E2E workflow started"; body = "smoke" } },
    @{ stepKey = "check"; stepType = "TASK"; sequenceOrder = 1; config = @{ title = "E2E workflow task"; assigneeId = $userId } },
    @{ stepKey = "approve"; stepType = "APPROVAL"; sequenceOrder = 2; config = @{ approvers = @($userId) } },
    @{ stepKey = "finish"; stepType = "ACTION"; sequenceOrder = 3; config = @{ type = "complete" } }
)
$wdef = Send-Api -Method Post -Path "/api/v1/workflow/definitions" -Body @{
    code = "E2E_FLOW$suffix"; name = "E2E Flow"; triggerType = "MANUAL"; steps = $wfSteps
}
Assert-Check "create workflow definition" ($wdef.ok -and $wdef.data.status -eq "DRAFT")
if ($wdef.ok) {
    $wdefId = $wdef.data.id
    $act = Send-Api -Method Post -Path "/api/v1/workflow/definitions/$wdefId/activate" -Body @{ }
    Assert-Check "activate workflow definition" ($act.ok -and $act.data.status -eq "ACTIVE")
    $winst = Send-Api -Method Post -Path "/api/v1/workflow/instances" -Body @{ definitionId = $wdefId; payload = @{ source = "smoke" } }
    Assert-Check "trigger workflow instance reaches approval" ($winst.ok -and $winst.data.status -eq "WAITING_APPROVAL")
    if ($winst.ok) {
        $winstId = $winst.data.id
        $wdetail = Send-Api -Method Get -Path "/api/v1/workflow/instances/$winstId"
        Assert-Check "instance detail has step instances" ($wdetail.ok -and (@($wdetail.data.stepInstances).Count -ge 2))
        $appr = @($wdetail.data.approvals | Where-Object { $_.status -eq "PENDING" })
        Assert-Check "instance has pending approval" ($appr.Count -ge 1)
        $dec = Send-Api -Method Post -Path "/api/v1/workflow/instances/$winstId/approvals/$($appr[0].id)/decide" -Body @{ decision = "APPROVED"; comments = "ok" }
        Assert-Check "approve continues workflow to completion" ($dec.ok -and $dec.data.status -eq "COMPLETED")
    } else {
        Assert-Check "instance detail has step instances" $false
        Assert-Check "instance has pending approval" $false
        Assert-Check "approve continues workflow to completion" $false
    }
    $wp = Send-Api -Method Get -Path "/api/v1/workflow/approvals/pending"
    Assert-Check "GET pending approvals" $wp.ok
} else {
    Assert-Check "activate workflow definition" $false
    Assert-Check "trigger workflow instance reaches approval" $false
    Assert-Check "instance detail has step instances" $false
    Assert-Check "instance has pending approval" $false
    Assert-Check "approve continues workflow to completion" $false
    Assert-Check "GET pending approvals" $false
}

# ----------------------------------------------------------- analytics
Write-Host "`n> Analytics" -ForegroundColor Yellow
$ao = Send-Api -Method Get -Path "/api/v1/analytics/overview"
Assert-Check "analytics overview" ($ao.ok -and $null -ne $ao.data.totalPatients -and $null -ne $ao.data.revenueTodayQar)
$ap = Send-Api -Method Get -Path "/api/v1/analytics/patients"
Assert-Check "analytics patients" ($ap.ok -and $null -ne $ap.data.byGender)
$ae = Send-Api -Method Get -Path "/api/v1/analytics/encounters"
Assert-Check "analytics encounters" ($ae.ok -and $null -ne $ae.data.byStatus)
$aq = Send-Api -Method Get -Path "/api/v1/analytics/queue"
Assert-Check "analytics queue" ($aq.ok -and $null -ne $aq.data.byQueueType)
$al = Send-Api -Method Get -Path "/api/v1/analytics/lab"
Assert-Check "analytics lab" ($al.ok -and $null -ne $al.data.byResultStatus)
$ab = Send-Api -Method Get -Path "/api/v1/analytics/billing"
Assert-Check "analytics billing" ($ab.ok -and $null -ne $ab.data.revenueThisMonthQar)

# ------------------------------------------------------- FHIR / HL7 interoperability
Write-Host "`n> Interoperability (FHIR / HL7 / MLLP)" -ForegroundColor Yellow
$fp = Send-Api -Method Get -Path "/api/v1/fhir/Patient"
Assert-Check "FHIR Patient bundle" ($fp.ok -and $fp.data.resourceType -eq "Bundle" -and (@($fp.data.entry).Count -ge 1))
$fe = Send-Api -Method Get -Path "/api/v1/fhir/Encounter"
Assert-Check "FHIR Encounter bundle" ($fe.ok -and $fe.data.resourceType -eq "Bundle")
$fo = Send-Api -Method Get -Path "/api/v1/fhir/DiagnosticReport"
Assert-Check "FHIR DiagnosticReport bundle" ($fo.ok -and $fo.data.resourceType -eq "Bundle")

$crlf = [char]13
$adtRaw = "MSH|^~\&|E2E|HOSP|SQUAMED|MIR|20260906120000||ADT^A04^ADT_A04|CTRL-E2E$suffix|P|2.5${crlf}EVN|A04|20260906120000${crlf}PID|1||E2E-MRN-$suffix||Test^E2E||19800101|M"
$hparse = Send-Api -Method Post -Path "/api/v1/hl7/parse" -Body @{ raw = $adtRaw }
Assert-Check "HL7 parse ADT message" ($hparse.ok -and $hparse.data.messageType -eq "ADT" -and $hparse.data.controlId -eq "CTRL-E2E$suffix")
$hsend = Send-Api -Method Post -Path "/api/v1/hl7/messages" -Body @{ raw = $adtRaw }
Assert-Check "HL7 ADT import returns AA ACK" ($hsend.ok -and $hsend.data.ackCode -eq "AA" -and $null -ne $hsend.data.importedPatientId)
$hhist = Send-Api -Method Get -Path "/api/v1/hl7/messages"
Assert-Check "HL7 message history" ($hhist.ok -and (@($hhist.data).Count -ge 1))
$ih = Send-Api -Method Get -Path "/api/v1/interop/health"
Assert-Check "interop health MLLP running" ($ih.ok -and $ih.data.mllp.mllpRunning -eq $true)

# ------------------------------------------------------- AI gateway
Write-Host "`n> AI Gateway (models / agents / commands / chat / streaming)" -ForegroundColor Yellow
$aimodels = Send-Api -Method Get -Path "/api/v1/ai/models"
Assert-Check "AI default ECHO model seeded" ($aimodels.ok -and (@($aimodels.data | Where-Object { $_.provider -eq "ECHO" -and $_.isDefault }).Count -ge 1))
$aiagents = Send-Api -Method Get -Path "/api/v1/ai/agents"
Assert-Check "AI ops agent seeded" ($aiagents.ok -and (@($aiagents.data | Where-Object { $_.code -eq "ops" }).Count -eq 1))
$aitools = Send-Api -Method Get -Path "/api/v1/ai/tools"
Assert-Check "AI tools seeded" ($aitools.ok -and (@($aitools.data).Count -ge 5))
$ainew = Send-Api -Method Post -Path "/api/v1/ai/models" -Body @{ name = "E2E OpenAI $suffix"; provider = "OPENAI"; modelName = "gpt-4o-mini"; baseUrl = "https://api.openai.com/v1"; apiKey = "sk-e2e" }
Assert-Check "AI create OPENAI model" ($ainew.ok -and $null -ne $ainew.data.id)
$ainewdel = Send-Api -Method Delete -Path "/api/v1/ai/models/$($ainew.data.id)"
Assert-Check "AI delete model" ($ainewdel.ok -and $ainewdel.data.deleted -eq $true)
$acomNav = Send-Api -Method Post -Path "/api/v1/ai/command" -Body @{ query = "open laboratory" }
Assert-Check "AI command navigate" ($acomNav.ok -and $acomNav.data.intent -eq "navigate")
$acomCnt = Send-Api -Method Post -Path "/api/v1/ai/command" -Body @{ query = "how many patients?" }
Assert-Check "AI command count" ($acomCnt.ok -and $acomCnt.data.intent -eq "count" -and $acomCnt.data.message -match "patients")
$acomApp = Send-Api -Method Post -Path "/api/v1/ai/command" -Body @{ query = "pending approvals" }
Assert-Check "AI command workflow" ($acomApp.ok -and $acomApp.data.intent -eq "workflow")
$acomRev = Send-Api -Method Post -Path "/api/v1/ai/command" -Body @{ query = "revenue today" }
Assert-Check "AI command analytics" ($acomRev.ok -and $acomRev.data.intent -eq "analytics" -and $acomRev.data.message -match "QR")
$acomSrv = Send-Api -Method Post -Path "/api/v1/ai/command" -Body @{ query = "search patient Sara" }
Assert-Check "AI command search" ($acomSrv.ok -and $acomSrv.data.intent -eq "search_patients")
$achat = Send-Api -Method Post -Path "/api/v1/ai/chat" -Body @{ messages = @(@{ role = "user"; content = "how many patients?" }) }
Assert-Check "AI chat reply" ($achat.ok -and $achat.data.intent -eq "count" -and $achat.data.reply -match "patients")
$streamBody = ""
try {
    $sresp = Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v1/ai/chat/stream" -Headers $headers -ContentType "application/json" -Body (@{ messages = @(@{ role = "user"; content = "pending approvals" }) } | ConvertTo-Json -Depth 8) -UseBasicParsing
    $streamBody = $sresp.Content
    $streamOk = ($streamBody -match '"type":"token"' -and $streamBody -match '"type":"done"')
} catch {
    $streamOk = $false
}
Assert-Check "AI chat stream SSE tokens+done" $streamOk
$ailogs = Send-Api -Method Get -Path "/api/v1/ai/logs"
Assert-Check "AI interaction logs recorded" ($ailogs.ok -and (@($ailogs.data).Count -ge 4))

# ------------------------------------------------------- AI scribe
Write-Host "`n> AI Scribe (session pipeline: record -> STT -> speakers -> draft -> sign)" -ForegroundColor Yellow
$spipe = Send-Api -Method Get -Path "/api/v1/scribe/pipeline"
Assert-Check "scribe pipeline stages" ($spipe.ok -and (@($spipe.data.stages).Count -eq 6 -and $spipe.data.stages -contains "SIGNED"))
$ssess = Send-Api -Method Post -Path "/api/v1/scribe/sessions" -Body @{ patientName = "E2E Scribe $suffix"; chiefComplaint = "Persistent cough $suffix" }
Assert-Check "scribe create session" ($ssess.ok -and $null -ne $ssess.data.id -and $ssess.data.stage -eq "CREATED")
$sid = $ssess.data.id
$srec = Send-Api -Method Post -Path "/api/v1/scribe/sessions/$sid/start-recording"
Assert-Check "scribe start recording" ($srec.ok -and $srec.data.stage -eq "TRANSCRIBING")
$script = "Patient: I have had this cough for five days.`nDoctor: Mild wheeze on expiration noted.`nPatient: Temperature was 38 at home."
$sstop = Send-Api -Method Post -Path "/api/v1/scribe/sessions/$sid/stop-recording" -Body @{ transcriptText = $script; durationSeconds = 75 }
Assert-Check "scribe stop recording separates speakers" ($sstop.ok -and $sstop.data.stage -eq "SPEAKERS_RESOLVED" -and (@($sstop.data.segments).Count -eq 3))
$sgen = Send-Api -Method Post -Path "/api/v1/scribe/sessions/$sid/generate"
Assert-Check "scribe generate SOAP draft" ($sgen.ok -and $sgen.data.stage -eq "DRAFTED" -and $sgen.data.draft -match "SOAP NOTE")
$ssign = Send-Api -Method Post -Path "/api/v1/scribe/sessions/$sid/sign"
Assert-Check "scribe sign session" ($ssign.ok -and $ssign.data.status -eq "SIGNED" -and $ssign.data.stage -eq "SIGNED" -and $null -ne $ssign.data.signedAt)
$sclean = Send-Api -Method Delete -Path "/api/v1/scribe/sessions/$sid"
Assert-Check "scribe delete session" ($sclean.ok -and $sclean.data.deleted -eq $true)

# --------------------------------------------------------------- summary
Write-Host "`n======================================" -ForegroundColor Cyan
Write-Host "  PASSED: $script:Passed   FAILED: $script:Failed" -ForegroundColor $(if ($script:Failed -eq 0) { "Green" } else { "Red" })
Write-Host "======================================" -ForegroundColor Cyan
if ($script:Failed -gt 0) {
    Write-Host "`nFailed checks:" -ForegroundColor Red
    $script:Log | Where-Object { $_ -like "FAIL*" } | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
exit 0