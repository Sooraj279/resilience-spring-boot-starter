<#
.SYNOPSIS
    End-to-end smoke test for the resilience starter demo-app.

.DESCRIPTION
    Exercises every behaviour the library claims to provide, against a running
    demo-app, and reports pass/fail per check. Exits 0 only if everything passed,
    so it can be wired into CI later.

    Prerequisites:
      * demo-app running       ->  ./mvnw -pl demo-app spring-boot:run
      * Redis on localhost:6379 (because application.yml sets backend: redis)
      * curl.exe on PATH (ships with Windows 10+)

.EXAMPLE
    ./smoke-test.ps1
    ./smoke-test.ps1 -BaseUrl http://localhost:9090
    ./smoke-test.ps1 -SkipCircuitRecovery    # skips the 10s cooldown wait
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$SkipCircuitRecovery
)

$ErrorActionPreference = "Stop"

$script:Passed = 0
$script:Failed = 0

function Invoke-Api {
    param([Parameter(Mandatory)][string]$Url)

    $lines = @(& curl.exe -s -D - -o - --max-time 15 $Url 2>$null)
    if ($lines.Count -eq 0) {
        return [pscustomobject]@{ Status = 0; Headers = @{}; Body = "" }
    }

    $status    = 0
    $headers   = @{}
    $bodyStart = $lines.Count

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^HTTP/\S+\s+(\d{3})') { $status = [int]$Matches[1]; continue }
        if ($line -match '^\s*$')               { $bodyStart = $i + 1; break }
        if ($line -match '^([^:]+):\s*(.*)$')   { $headers[$Matches[1].Trim().ToLower()] = $Matches[2].Trim() }
    }

    $body = ""
    if ($bodyStart -lt $lines.Count) {
        $body = ($lines[$bodyStart..($lines.Count - 1)] -join "`n")
    }

    [pscustomobject]@{ Status = $status; Headers = $headers; Body = $body }
}

function Check {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][bool]$Condition,
        [string]$Detail = ""
    )
    if ($Condition) {
        $script:Passed++
        Write-Host "  PASS  " -ForegroundColor Green -NoNewline
        Write-Host $Name
    } else {
        $script:Failed++
        Write-Host "  FAIL  " -ForegroundColor Red -NoNewline
        Write-Host $Name
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
    }
}

function Section { param([string]$Title) Write-Host ""; Write-Host $Title -ForegroundColor Cyan }

# A fresh id per run. Rate-limit state lives in Redis and outlives the process,
# so reusing an id would start from a partly drained bucket.
function New-UserId { "smoke-" + [guid]::NewGuid().ToString("N").Substring(0, 8) }

Write-Host "Resilience starter smoke test" -ForegroundColor White
Write-Host "Target: $BaseUrl"

# ---------------------------------------------------------------- 1. liveness
Section "1. Application is up"

$health = Invoke-Api "$BaseUrl/actuator/health"
Check "actuator/health reachable" ($health.Status -eq 200) "got HTTP $($health.Status) - is demo-app running?"
Check "health reports UP" ($health.Body -match '"status"\s*:\s*"UP"') "body: $($health.Body)"

if ($health.Status -ne 200) {
    Write-Host ""
    Write-Host "Application is not reachable. Start it with:" -ForegroundColor Yellow
    Write-Host "  ./mvnw -pl demo-app spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# ------------------------------------------------- 2. redis backend is really in play
Section "2. Redis backend is actually serving (not silently failing open)"

# application.yml sets fail-open: true. If Redis is down every call is allowed with
# remaining = -1, and RateLimitAspect omits X-RateLimit-Remaining in that case. So the
# presence of that header is proof the Redis round trip genuinely succeeded. Without
# this check, a dead Redis looks identical to a working one until limits never trigger.
$probeUser = New-UserId
$probe = Invoke-Api "$BaseUrl/api/hello?userId=$probeUser"

Check "GET /api/hello returns 200" ($probe.Status -eq 200) "got HTTP $($probe.Status)"
Check "X-RateLimit-Limit header present" ($probe.Headers.ContainsKey("x-ratelimit-limit")) "headers: $($probe.Headers.Keys -join ', ')"
Check "X-RateLimit-Remaining present (proves Redis is live)" `
      ($probe.Headers.ContainsKey("x-ratelimit-remaining")) `
      "header absent => limiter failed open => Redis unreachable on localhost:6379"
Check "X-RateLimit-Limit is 5" ($probe.Headers["x-ratelimit-limit"] -eq "5") "got '$($probe.Headers['x-ratelimit-limit'])'"

# ---------------------------------------------------- 3. token bucket enforcement
Section "3. Token bucket limiter (/api/hello, capacity 5)"

$userA = New-UserId
$statuses = @()
for ($i = 1; $i -le 5; $i++) {
    $r = Invoke-Api "$BaseUrl/api/hello?userId=$userA"
    $statuses += $r.Status
}
Check "first 5 requests all allowed" (($statuses | Where-Object { $_ -ne 200 }).Count -eq 0) "statuses: $($statuses -join ', ')"

$sixth = Invoke-Api "$BaseUrl/api/hello?userId=$userA"
Check "6th request rejected with 429" ($sixth.Status -eq 429) "got HTTP $($sixth.Status)"
Check "429 carries Retry-After header" ($sixth.Headers.ContainsKey("retry-after")) "headers: $($sixth.Headers.Keys -join ', ')"
Check "Retry-After is a positive integer" `
      ($sixth.Headers["retry-after"] -match '^\d+$' -and [int]$sixth.Headers["retry-after"] -ge 1) `
      "got '$($sixth.Headers['retry-after'])'"
Check "429 body is a ProblemDetail" ($sixth.Body -match 'Too Many Requests') "body: $($sixth.Body)"

# ------------------------------------------------------- 4. per-key isolation
Section "4. Limits are per key, not global"

$userB = New-UserId
$other = Invoke-Api "$BaseUrl/api/hello?userId=$userB"
Check "a different userId is unaffected by the exhausted bucket" ($other.Status -eq 200) "got HTTP $($other.Status)"

# ------------------------------------------------- 5. sliding window enforcement
Section "5. Sliding window limiter (/api/strict, capacity 5)"

$userC = New-UserId
$swStatuses = @()
for ($i = 1; $i -le 5; $i++) {
    $r = Invoke-Api "$BaseUrl/api/strict?userId=$userC"
    $swStatuses += $r.Status
}
Check "first 5 requests all allowed" (($swStatuses | Where-Object { $_ -ne 200 }).Count -eq 0) "statuses: $($swStatuses -join ', ')"

$swSixth = Invoke-Api "$BaseUrl/api/strict?userId=$userC"
Check "6th request rejected with 429" ($swSixth.Status -eq 429) "got HTTP $($swSixth.Status)"

# ------------------------------------------------------- 6. circuit breaker
Section "6. Circuit breaker (/api/flaky, threshold 3, cooldown 10s)"

# The breaker is keyed by method, not by user, and lives in memory. Three consecutive
# failures trip it; the 4th call is short-circuited before the method body runs.
$failStatuses = @()
for ($i = 1; $i -le 3; $i++) {
    $r = Invoke-Api "$BaseUrl/api/flaky?fail=true"
    $failStatuses += $r.Status
}
Check "3 failing calls surface as 500" (($failStatuses | Where-Object { $_ -ne 500 }).Count -eq 0) "statuses: $($failStatuses -join ', ')"

$open = Invoke-Api "$BaseUrl/api/flaky?fail=false"
Check "circuit is open: next call short-circuits with 503" ($open.Status -eq 503) "got HTTP $($open.Status) - expected CircuitOpenException"
Check "503 body is a ProblemDetail" ($open.Body -match 'Service Unavailable') "body: $($open.Body)"

if ($SkipCircuitRecovery) {
    Write-Host "  SKIP  half-open recovery (-SkipCircuitRecovery)" -ForegroundColor Yellow
    Write-Host "        NOTE: circuit left OPEN; the next run's section 6 may misreport." -ForegroundColor DarkGray
} else {
    Write-Host "        waiting 11s for the cooldown to elapse..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 11
    $recovered = Invoke-Api "$BaseUrl/api/flaky?fail=false"
    Check "after cooldown, a trial request is admitted and succeeds" ($recovered.Status -eq 200) "got HTTP $($recovered.Status)"

    $closed = Invoke-Api "$BaseUrl/api/flaky?fail=false"
    Check "circuit closed again after successful trial" ($closed.Status -eq 200) "got HTTP $($closed.Status)"
}

# ------------------------------------------------------------- 7. metrics
Section "7. Micrometer metrics are registered"

# These counters register lazily on first increment, so this section is only
# meaningful after the traffic generated above.
$allowed = Invoke-Api "$BaseUrl/actuator/metrics/resilience.ratelimit.allowed"
Check "resilience.ratelimit.allowed exists" ($allowed.Status -eq 200) "HTTP $($allowed.Status) - metrics bean may not be wired"
Check "allowed count is non-zero" ($allowed.Body -match '"statistic"\s*:\s*"COUNT"\s*,\s*"value"\s*:\s*([1-9]|\d{2,})') "body: $($allowed.Body)"

$rejected = Invoke-Api "$BaseUrl/actuator/metrics/resilience.ratelimit.rejected"
Check "resilience.ratelimit.rejected exists" ($rejected.Status -eq 200) "HTTP $($rejected.Status)"

$circuit = Invoke-Api "$BaseUrl/actuator/metrics/resilience.circuit.rejected"
Check "resilience.circuit.rejected exists" ($circuit.Status -eq 200) "HTTP $($circuit.Status)"

$list = Invoke-Api "$BaseUrl/actuator/metrics"
Check "resilience metrics appear in the metrics index" ($list.Body -match 'resilience\.') "no 'resilience.' entries in names[]"

# ------------------------------------------------------------- summary
Write-Host ""
Write-Host ("-" * 52)
Write-Host "Passed: $script:Passed   Failed: $script:Failed"

if ($script:Failed -gt 0) {
    Write-Host "SMOKE TEST FAILED" -ForegroundColor Red
    exit 1
}

Write-Host "ALL CHECKS PASSED" -ForegroundColor Green
exit 0
