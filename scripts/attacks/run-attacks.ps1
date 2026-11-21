# Local security scenarios. They do not write business data or modify application state.
# `before` expects access to be open; `after` expects access to be blocked.
param(
    [ValidateSet('before', 'after')]
    [string]$Mode = 'after',
    [string]$Gateway = 'http://127.0.0.1:8080',
    [string]$Order = 'http://127.0.0.1:8081',
    [string]$Inventory = 'http://127.0.0.1:8082',
    [string]$Notification = 'http://127.0.0.1:8083',
    [string]$ProductId = 'SKU-42'
)

$ErrorActionPreference = 'Continue'
$script:results = [System.Collections.Generic.List[object]]::new()

foreach ($targetUrl in @($Gateway, $Order, $Inventory, $Notification)) {
    $targetUri = [uri]$targetUrl
    if ($targetUri.Scheme -ne 'http' -or $targetUri.Host -notin @('localhost', '127.0.0.1', '::1')) {
        throw "Target refused: only local HTTP services (localhost/loopback) are allowed: $targetUrl"
    }
}

function Invoke-LocalGet([string]$Url, [string]$Token = '') {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -Headers $headers `
            -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        return [pscustomobject]@{ Code = [int]$response.StatusCode; Body = [string]$response.Content }
    }
    catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Code = $status; Body = '' }
    }
}

function Test-TcpPort([string]$Url) {
    $uri = [uri]$Url
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($uri.Host, $uri.Port)
        return $task.Wait(1500) -and $client.Connected
    }
    catch { return $false }
    finally { $client.Dispose() }
}

function Report([string]$Name, [bool]$AccessSucceeded, [string]$Detail) {
    $expectedOpen = ($Mode -eq 'before')
    $matchesMode = ($AccessSucceeded -eq $expectedOpen)
    $verdict = if ($AccessSucceeded) { 'ACCESSIBLE' } else { 'BLOCKED' }
    $color = if ($matchesMode) { 'Green' } else { 'Red' }
    Write-Host ("{0,-11} {1,-38} {2}" -f $verdict, $Name, $Detail) -ForegroundColor $color
    $script:results.Add([pscustomobject]@{
        Scenario = $Name
        Verdict = $verdict
        MatchesMode = $matchesMode
        Detail = $Detail
    })
}

Write-Host "== Non-destructive local checks (mode $Mode) ==" -ForegroundColor Cyan
Write-Host 'Target: localhost / 127.0.0.1 only. No POST requests, Kafka events, or stock changes.' -ForegroundColor DarkGray

# A1. Direct service access: Compose publishes only the gateway on the host.
foreach ($target in @(
    @{ Name = 'bypass gateway: order-service'; Url = $Order },
    @{ Name = 'bypass gateway: inventory-service'; Url = $Inventory },
    @{ Name = 'bypass gateway: notification-service'; Url = $Notification }
)) {
    $open = Test-TcpPort $target.Url
    Report $target.Name $open ("TCP {0}:{1}" -f ([uri]$target.Url).Host, ([uri]$target.Url).Port)
}

# A2. Read data through the only published port, without an identity.
foreach ($target in @(
    @{ Name = 'list orders without authentication'; Url = "$Gateway/orders" },
    @{ Name = 'list notifications without authentication'; Url = "$Gateway/notifications" },
    @{ Name = 'read stock without authentication'; Url = "$Gateway/stock/$ProductId" }
)) {
    $response = Invoke-LocalGet $target.Url
    Report $target.Name ($response.Code -eq 200) "HTTP $($response.Code)"
}

# A3. A forged JWT must not grant access. This test is read-only.
function ConvertTo-Base64Url([string]$Value) {
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$forgedToken = (ConvertTo-Base64Url '{"alg":"none","typ":"JWT"}') + '.' +
    (ConvertTo-Base64Url '{"sub":"mallory","roles":["admin"]}') + '.'
$response = Invoke-LocalGet "$Gateway/orders" $forgedToken
Report 'forged unsigned JWT grants access' ($response.Code -eq 200) "HTTP $($response.Code)"

# A4. Check only whether Kafka accepts a metadata query without credentials.
# `--list` does not produce messages or consume events.
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host 'SKIP        Kafka unauthenticated metadata (docker not found)' -ForegroundColor Yellow
} else {
    $container = & docker inspect --format '{{.State.Running}}' kafka 2>$null
    if ($LASTEXITCODE -ne 0 -or $container -ne 'true') {
        Write-Host 'SKIP        Kafka unauthenticated metadata (kafka container is stopped)' -ForegroundColor Yellow
    } else {
        $null = & docker exec kafka /opt/kafka/bin/kafka-topics.sh `
            --bootstrap-server localhost:9092 --list 2>&1
        $kafkaAccessible = ($LASTEXITCODE -eq 0)
        $kafkaDetail = if ($kafkaAccessible) { 'topic list is accessible' } else { 'connection refused without authentication' }
        Report 'Kafka metadata without credentials' $kafkaAccessible $kafkaDetail
    }
}

Write-Host "`n== Summary ==" -ForegroundColor Cyan
$script:results | Format-Table -AutoSize
$unexpected = @($script:results | Where-Object { -not $_.MatchesMode }).Count
if ($unexpected -gt 0) {
    Write-Host "$unexpected result(s) did not match mode $Mode expectations." -ForegroundColor Red
    exit 1
}
Write-Host "All executed scenarios match mode $Mode expectations." -ForegroundColor Green
exit 0
