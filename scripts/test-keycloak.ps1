# scripts/test-keycloak.ps1
# End-to-end check of the identity model (Keycloak + JWT validated at every hop).
# Run from the repo root with the full stack up: docker compose up -d --build
$ErrorActionPreference = 'Stop'
$gw = 'http://localhost:8080'
$kc = 'http://localhost:8180/realms/shop/protocol/openid-connect/token'
$net = 'devsecops-project_frontend'   # adjust with `docker network ls` if needed
$script:fail = 0

function Get-Token($u, $p) {
  (curl.exe -s -d "grant_type=password&client_id=shop-cli&username=$u&password=$p" $kc |
    ConvertFrom-Json).access_token
}

function Code($method, $url, $token, $bodyFile) {
  $a = @('-s', '-o', 'NUL', '-w', '%{http_code}', '-X', $method)
  if ($token)    { $a += @('-H', "Authorization: Bearer $token") }
  if ($bodyFile) { $a += @('-H', 'Content-Type: application/json', '--data-binary', "@$bodyFile") }
  [int](curl.exe @a $url)
}

function Body($url, $token) {
  curl.exe -s -H "Authorization: Bearer $token" $url
}

function Check($name, $actual, $expected) {
  if ($actual -eq $expected) {
    Write-Host "PASS  $name -> $actual" -ForegroundColor Green
  } else {
    Write-Host "FAIL  $name -> $actual (expected $expected)" -ForegroundColor Red
    $script:fail++
  }
}

$alice = Get-Token alice alice-pass
$bob   = Get-Token bob bob-pass
$root  = Get-Token root root-pass
if (-not ($alice -and $bob -and $root)) { throw 'Could not obtain tokens: is Keycloak up?' }

# Alice creates an order
$orderFile = New-TemporaryFile
Set-Content $orderFile '{"productId":"SKU-42","quantity":1}' -NoNewline
$resp = curl.exe -s -w "`n%{http_code}" -X POST "$gw/orders" `
  -H "Authorization: Bearer $alice" -H 'Content-Type: application/json' --data-binary "@$orderFile"
$lines = @($resp -split "`n")
Check 'alice POST /orders' ([int]$lines[-1]) 201
$id = ($lines[0] | ConvertFrom-Json).id

# Order ownership
Check 'alice GET /orders/{id}' (Code GET "$gw/orders/$id" $alice) 200
Check 'bob   GET /orders/{id} (not owner)' (Code GET "$gw/orders/$id" $bob) 404
Check 'root  GET /orders/{id} (admin)' (Code GET "$gw/orders/$id" $root) 200
Check 'bob   GET /orders (empty list)' ((Body "$gw/orders" $bob).Trim()) '[]'
Check 'root  GET /orders contains the order' ((Body "$gw/orders" $root) -match $id) $true

# Stock reservation is not routed through the gateway
Check 'alice POST /stock/SKU-42/reservations' (Code POST "$gw/stock/SKU-42/reservations" $alice $orderFile) 403
Check 'root  POST /stock/SKU-42/reservations' (Code POST "$gw/stock/SKU-42/reservations" $root $orderFile) 403

# Notifications (async: wait for the saga and the mail)
Start-Sleep -Seconds 6
Check 'alice GET /notifications contains her order' ((Body "$gw/notifications" $alice) -match $id) $true
Check 'bob   GET /notifications (empty list)' ((Body "$gw/notifications" $bob).Trim()) '[]'
Check 'bob   GET /notifications/user/{id} (admin only)' `
  (Code GET "$gw/notifications/user/11111111-1111-1111-1111-111111111111" $bob) 403

# Zero Trust: each service revalidates the JWT itself, without the gateway
function InternalCode($url) {
  [int](docker run --rm --network $net curlimages/curl -s -o /dev/null -w '%{http_code}' $url)
}
Check 'direct order-service, no token'        (InternalCode 'http://order-service:8081/orders') 401
Check 'direct inventory-service, no token'    (InternalCode 'http://inventory-service:8082/stock/SKU-42') 401
Check 'direct notification-service, no token' (InternalCode 'http://notification-service:8083/notifications') 401

Remove-Item $orderFile
if ($script:fail) { Write-Host "`n$script:fail check(s) failed" -ForegroundColor Red; exit 1 }
Write-Host "`nAll identity checks passed" -ForegroundColor Green