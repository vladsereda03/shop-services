# Emulates a LiqPay server-to-server callback to the payment service.
# LiqPay cannot reach localhost, so for local e2e testing we send the same
# POST it would send: form fields "data" (base64 JSON) and "signature"
# (base64(sha1(private_key + data + private_key))).
#
# The signature must be built with the SAME private key the payment service
# runs with (env LIQPAY_PRIVATE_KEY, default "sandbox_private_key").
#
# Usage:
#   .\emulate-liqpay-callback.ps1 -UserId 1                      # happy path
#   .\emulate-liqpay-callback.ps1 -UserId 1 -BadSignature        # expect 403
#   .\emulate-liqpay-callback.ps1 -UserId 1 -Status failure      # expect 200, no order
#   .\emulate-liqpay-callback.ps1 -UserId 1 -PaymentId 123       # run twice: the duplicate
#                                                                # must answer 200 without a second order

param(
    [Parameter(Mandatory = $true)]
    [long]$UserId,

    [double]$Amount = 100.0,

    # "sandbox" and "success" create an order; anything else must be ignored
    [string]$Status = "sandbox",

    # unique per LiqPay charge; pass the same value twice to test callback deduplication
    [long]$PaymentId = (Get-Random -Minimum 1000000000 -Maximum 2000000000),

    [string]$PrivateKey = $(if ($env:LIQPAY_PRIVATE_KEY) { $env:LIQPAY_PRIVATE_KEY } else { "sandbox_private_key" }),

    [string]$Url = "http://localhost:8085/payment/new",

    [switch]$BadSignature
)

# payload mimics the real LiqPay callback JSON (only status/info/order_id matter to us)
$payload = [ordered]@{
    payment_id  = $PaymentId
    action      = "pay"
    status      = $Status
    version     = 3
    order_id    = [guid]::NewGuid().ToString()
    description = "Emulated LiqPay callback"
    amount      = $Amount
    currency    = "UAH"
    info        = "$UserId"
} | ConvertTo-Json -Compress

$data = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($payload))

$sha1 = [System.Security.Cryptography.SHA1]::Create()
$signature = [Convert]::ToBase64String(
    $sha1.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($PrivateKey + $data + $PrivateKey)))
$sha1.Dispose()

if ($BadSignature) {
    $signature = "deliberately-broken-signature"
}

Write-Host "POST $Url"
Write-Host "  status=$Status  info(userId)=$UserId  payment_id=$PaymentId  badSignature=$($BadSignature.IsPresent)"

try {
    $response = Invoke-WebRequest -Uri $Url -Method Post `
        -Body @{ data = $data; signature = $signature } -UseBasicParsing
    Write-Host "HTTP $($response.StatusCode) - callback accepted" -ForegroundColor Green
} catch {
    $statusCode = "?"
    if ($_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
    Write-Host "HTTP $statusCode - callback rejected" -ForegroundColor Yellow
}
