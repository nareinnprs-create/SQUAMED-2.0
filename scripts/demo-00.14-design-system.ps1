# Exit-demo slice for section 00.14 (Design system): the /design-system showcase
# route served from the production build (tokens, both densities, i18n, task cards,
# the reworked spec surfaces: Context Spine, instrument numerals, clinical data
# components, feedback states) plus the committed Storybook static build (44 stories).
# Web on 3000, no API. Server is started separately; node processes are killed after
# the run. ASCII only.
$ErrorActionPreference = 'Stop'
$web = 'http://localhost:3000'
$script:pass = 0
$script:fail = 0

function Check($name, $ok) {
  if ($ok) { $script:pass++; Write-Output "PASS $name" }
  else { $script:fail++; Write-Output "FAIL $name" }
}

function Web($path) {
  try {
    return Invoke-WebRequest -Uri "$web$path" -UseBasicParsing -TimeoutSec 15
  } catch {
    return @{ _status = $_.Exception.Response.StatusCode.value__; Content = '' }
  }
}

Write-Output "== 00.14 design system demo (3000) =="

# 1 web app serves the shell
$root = Web '/'
Check 'web root serves the app shell' ($root.StatusCode -eq 200 -and $root.Content.Contains('SQUAMED'))

# 2 the public showcase route is served
$ds = Web '/design-system'
Check 'design-system route served (public)' ($ds.StatusCode -eq 200)

# 3 showcase heading
Check 'showcase heading' ($ds.Content.Contains('SQUAMED Design System'))

# 4 spec summary line: light clinical mode, RTL-ready, WCAG baseline
Check 'light clinical mode subtitle' ($ds.Content.Contains('light clinical mode'))
Check 'RTL-ready declared' ($ds.Content.Contains('RTL-ready'))
Check 'WCAG 2.2 AA declared' ($ds.Content.Contains('WCAG 2.2 AA'))

# 5 i18n: en + hi declared and the language toggle rendered
Check 'i18n en + hi declared' ($ds.Content.Contains('i18n (en, hi)'))
Check 'language toggle rendered' ($ds.Content.Contains('English'))

# 6 tokens: clinical token hex values on the page
Check 'token palette rendered' ($ds.Content.Contains('#15191A') -and $ds.Content.Contains('#087F7A'))

# 7 core component: data table in both densities
Check 'clinical density (44px rows) section' ($ds.Content.Contains('44px rows'))
Check 'enterprise density (36px rows) section' ($ds.Content.Contains('36px rows'))

# 8 core component: task card with translated open action + escalation badge
Check 'task card fixture rendered' ($ds.Content.Contains('Administer morning insulin'))
Check 'task card open action (i18n key)' ($ds.Content.Contains('Open task'))
Check 'escalated badge rendered' ($ds.Content.Contains('Escalated'))

# 9 rework: Context Spine signature (name + mono MRN/ABHA + allergy glyph + statuses + stage)
Check 'context spine fixture rendered' ($ds.Content.Contains('MQ-2026-0042') -and $ds.Content.Contains('12-3456-7890-1234'))
Check 'context spine allergy flag rendered' ($ds.Content.Contains('Allergies: Penicillin'))
Check 'context spine encounter stage rendered' ($ds.Content.Contains('Day 2'))

# 10 rework: instrument numerals — Plex wired via next/font (variable classes on the
# root; face names live in the built CSS, not the SSR HTML)
$html = [string]$ds.Content
Check 'font variable classes on root' ($html.Contains('__variable_'))
$builtCss = Get-ChildItem 'apps/web/.next/static/css' -Filter '*.css' -ErrorAction SilentlyContinue
Check 'IBM Plex Sans face in built CSS' ($null -ne $builtCss -and ($builtCss | Select-String -Pattern 'IBM Plex Sans' -Quiet))
Check 'IBM Plex Mono face in built CSS' ($null -ne $builtCss -and ($builtCss | Select-String -Pattern 'IBM Plex Mono' -Quiet))
Check 'vital chip mono fixture rendered' ($ds.Content.Contains('128/82') -and $ds.Content.Contains('mmHg'))

# 11 rework: clinical data components
Check 'status chip rendered' ($ds.Content.Contains('SLA at risk'))
Check 'lab row on range track rendered' ($ds.Content.Contains('Potassium') -and $ds.Content.Contains('High'))
Check 'med order line high-alert + second verify rendered' ($ds.Content.Contains('High alert') -and $ds.Content.Contains('Verify'))
Check 'dual confirm signature rendered' ($ds.Content.Contains('Confirm patient identity') -and $ds.Content.Contains('Blood transfusion'))
Check 'feedback states rendered' ($ds.Content.Contains('You are offline') -and $ds.Content.Contains('Could not save the note'))

# 12 Storybook static build committed output: entry page + iframe
$sbIndex = 'packages/ui/storybook-static/index.html'
$sbIframe = 'packages/ui/storybook-static/iframe.html'
Check 'storybook index.html built' (Test-Path $sbIndex)
Check 'storybook iframe.html built' (Test-Path $sbIframe)

# 13 Storybook carries the full component inventory (44 stories)
$idx = Get-Content 'packages/ui/storybook-static/index.json' -Raw | ConvertFrom-Json
$storyCount = ($idx.entries.PSObject.Properties | Where-Object { $_.Value.type -eq 'story' }).Count
Check "storybook stories = 44 (got $storyCount)" ($storyCount -eq 44)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
