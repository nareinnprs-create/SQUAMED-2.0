# Exit-demo slice for section 00.14 (Design system): the /design-system showcase
# route served from the production build (tokens, both densities, i18n, task cards)
# plus the committed Storybook static build (32 stories). Web on 3000, no API.
# Server is started separately; node processes are killed after the run. ASCII only.
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

# 9 Storybook static build committed output: entry page + iframe
$sbIndex = 'packages/ui/storybook-static/index.html'
$sbIframe = 'packages/ui/storybook-static/iframe.html'
Check 'storybook index.html built' (Test-Path $sbIndex)
Check 'storybook iframe.html built' (Test-Path $sbIframe)

# 10 Storybook carries the full component inventory (32 stories)
$idx = Get-Content 'packages/ui/storybook-static/index.json' -Raw | ConvertFrom-Json
$storyCount = ($idx.entries.PSObject.Properties | Where-Object { $_.Value.type -eq 'story' }).Count
Check "storybook stories = 32 (got $storyCount)" ($storyCount -eq 32)

Write-Output "== $script:pass passed, $script:fail failed =="
if ($script:fail -gt 0) { exit 1 }
