# scripts/smoke-saga.ps1
param([string]$Base = "http://localhost:8080")

function Place-Order($productId, $quantity) {
    $body = @{ productId = $productId; quantity = $quantity } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$Base/orders" -ContentType "application/json" -Body $body
}

function Wait-Status($id, $expected, $timeoutSec = 15) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    do {
        if ((Invoke-RestMethod -Uri "$Base/orders/$id").status -eq $expected) { return $true }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $false
}

$cases = @(
    @{ Name = "valid order";        Product = "SKU-42"; Qty = 1;    Expected = "CONFIRMED" },
    @{ Name = "insufficient stock"; Product = "SKU-42"; Qty = 5000; Expected = "REJECTED"  },
    @{ Name = "unknown product";    Product = "SKU-00"; Qty = 1;    Expected = "REJECTED"  }
)

$failed = 0
foreach ($c in $cases) {
    $order = Place-Order $c.Product $c.Qty
    if (Wait-Status $order.id $c.Expected) {
        Write-Host "PASS  $($c.Name) -> $($c.Expected)"
    } else {
        Write-Host "FAIL  $($c.Name): expected $($c.Expected)" -ForegroundColor Red
        $failed++
    }
}
exit $failed