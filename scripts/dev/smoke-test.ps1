# 冒烟测试：覆盖认证、订单分片、设备台账与排产、维保、仓储与领料、审批中心、财务应收开票、费用借款与 BI 看板。
# 运行前请先执行 scripts/remote/clean-smoke-data.sql（3306）与 clean-smoke-orders-3307.sql（3307），
# 避免上一次中断残留的队列与工单影响 FIFO 位置断言。
[CmdletBinding()]
param(
    [string]$AuthBase = 'http://localhost:9000',
    [string]$GatewayBase = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = 'Admin@123',
    [string]$OpsClientSecret = 'ops-secret-2026'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function New-Base64Url([byte[]]$Bytes) {
    [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-QueryString([hashtable]$Values) {
    ($Values.GetEnumerator() | ForEach-Object {
        [uri]::EscapeDataString([string]$_.Key) + '=' + [uri]::EscapeDataString([string]$_.Value)
    }) -join '&'
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw "FAIL: $Message"
    }
    Write-Host "PASS: $Message"
}

function Send-HttpRequest {
    param(
        [string]$Method,
        [string]$Url,
        [string]$FormBody,
        [string]$JsonBody,
        [string]$BearerToken,
        $Client,
        [switch]$Html
    )

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    if ($Html) {
        $request.Headers.Accept.Add([System.Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new('text/html'))
    }
    if ($BearerToken) {
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $BearerToken)
    }
    if ($FormBody) {
        $request.Content = [System.Net.Http.StringContent]::new(
            $FormBody, [System.Text.Encoding]::UTF8, 'application/x-www-form-urlencoded')
    } elseif ($JsonBody) {
        $request.Content = [System.Net.Http.StringContent]::new(
            $JsonBody, [System.Text.Encoding]::UTF8, 'application/json')
    }

    $response = $Client.SendAsync($request).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    [pscustomobject]@{
        Status = [int]$response.StatusCode
        Location = if ($null -ne $response.Headers.Location) { $response.Headers.Location.ToString() } else { $null }
        Body = $body
    }
}

function Get-UserTokenViaPkce {
    param(
        [string]$AuthBase,
        [string]$Username,
        [string]$Password,
        [string]$Scope = 'openid profile order.read'
    )

    $verifierBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($verifierBytes)
    $rng.Dispose()
    $verifier = New-Base64Url $verifierBytes
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $challenge = New-Base64Url ($sha.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($verifier)))
    $sha.Dispose()

    $handler = [System.Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseCookies = $true
    $handler.CookieContainer = [System.Net.CookieContainer]::new()
    $pkceClient = [System.Net.Http.HttpClient]::new($handler)

    $redirectUri = 'http://localhost:5173/login/oauth2/code/pc-web'
    $authorizeUrl = "$AuthBase/oauth2/authorize?" + (New-QueryString @{
        response_type = 'code'
        client_id = 'pc-web'
        scope = $Scope
        redirect_uri = $redirectUri
        state = 'smoke-state'
        code_challenge = $challenge
        code_challenge_method = 'S256'
    })
    $authorize = Send-HttpRequest -Method 'GET' -Url $authorizeUrl -Client $pkceClient -Html
    $loginUrl = if ($authorize.Location.StartsWith('http')) { $authorize.Location } else { "$AuthBase$($authorize.Location)" }
    $loginPage = Send-HttpRequest -Method 'GET' -Url $loginUrl -Client $pkceClient -Html
    $csrfMatch = [regex]::Match($loginPage.Body, 'name="_csrf"[^>]*value="([^"]+)"')
    if (-not $csrfMatch.Success) {
        $csrfMatch = [regex]::Match($loginPage.Body, 'value="([^"]+)"[^>]*name="_csrf"')
    }
    if (-not $csrfMatch.Success) {
        throw "login page has no CSRF token for $Username"
    }
    $loginBody = 'username=' + [uri]::EscapeDataString($Username) +
        '&password=' + [uri]::EscapeDataString($Password) +
        '&_csrf=' + [uri]::EscapeDataString($csrfMatch.Groups[1].Value)
    $login = Send-HttpRequest -Method 'POST' -Url "$AuthBase/login" -FormBody $loginBody -Client $pkceClient

    $next = $login
    for ($i = 0; $i -lt 5; $i++) {
        if ($null -eq $next.Location) { break }
        if ($next.Location.StartsWith($redirectUri)) { break }
        $nextUrl = if ($next.Location.StartsWith('http')) { $next.Location } else { "$AuthBase$($next.Location)" }
        $next = Send-HttpRequest -Method 'GET' -Url $nextUrl -Client $pkceClient -Html
    }
    if (-not $next.Location) {
        throw "authorization code was not returned for $Username"
    }
    $codeMatch = [regex]::Match($next.Location, '[?&]code=([^&]+)')
    if (-not $codeMatch.Success) {
        throw "authorization code is missing for $Username"
    }
    $code = [uri]::UnescapeDataString($codeMatch.Groups[1].Value)
    $tokenBody = New-QueryString @{
        grant_type = 'authorization_code'
        code = $code
        redirect_uri = $redirectUri
        client_id = 'pc-web'
        code_verifier = $verifier
    }
    $tokenResponse = Send-HttpRequest -Method 'POST' -Url "$AuthBase/oauth2/token" -FormBody $tokenBody -Client $pkceClient
    if ($tokenResponse.Status -ne 200) {
        throw "token exchange failed for ${Username}: status=$($tokenResponse.Status) body=$($tokenResponse.Body)"
    }
    $tokenResponse.Body | ConvertFrom-Json
}

Write-Host '== 1. Client credentials token + gateway routing =='
$clientToken = Invoke-RestMethod -Method Post -Uri "$AuthBase/oauth2/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
        grant_type = 'client_credentials'
        client_id = 'ops-client'
        client_secret = $OpsClientSecret
        scope = 'system.read order.read order.write'
    }
Assert-True ([bool]$clientToken.access_token) 'client_credentials access token issued'

$clientHeaders = @{ Authorization = "Bearer $($clientToken.access_token)" }
$systemHealth = Invoke-RestMethod -Uri "$GatewayBase/api/system/health" -Headers $clientHeaders
Assert-True ($systemHealth.code -eq 0) 'gateway -> system health route works'

$orderHealth = Invoke-RestMethod -Uri "$GatewayBase/api/order/orders/health" -Headers $clientHeaders
Assert-True ($orderHealth.code -eq 0) 'gateway -> order health route works'

$customerHealth = Invoke-RestMethod -Uri "$GatewayBase/api/customer/health" -Headers $clientHeaders
Assert-True ($customerHealth.code -eq 0) 'gateway -> customer health route works'

Write-Host ''
Write-Host '== 2. Authorization code + PKCE + user token =='
$verifierBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($verifierBytes)
$rng.Dispose()
$codeVerifier = New-Base64Url $verifierBytes
$sha = [System.Security.Cryptography.SHA256]::Create()
$challengeBytes = $sha.ComputeHash([System.Text.Encoding]::ASCII.GetBytes($codeVerifier))
$sha.Dispose()
$codeChallenge = New-Base64Url $challengeBytes

$handler = [System.Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$handler.UseCookies = $true
$handler.CookieContainer = [System.Net.CookieContainer]::new()
$client = [System.Net.Http.HttpClient]::new($handler)

$redirectUri = 'http://localhost:5173/login/oauth2/code/pc-web'
$authorizeUrl = "$AuthBase/oauth2/authorize?" + (New-QueryString @{
    response_type = 'code'
    client_id = 'pc-web'
    scope = 'openid profile system.read order.read'
    redirect_uri = $redirectUri
    state = 'smoke-test-state'
    code_challenge = $codeChallenge
    code_challenge_method = 'S256'
})

$authorize = Send-HttpRequest -Method 'GET' -Url $authorizeUrl -Client $client -Html
Assert-True ($authorize.Status -eq 302) 'authorization endpoint redirects to login'
$loginUrl = if ($authorize.Location.StartsWith('http')) { $authorize.Location } else { "$AuthBase$($authorize.Location)" }
$loginPage = Send-HttpRequest -Method 'GET' -Url $loginUrl -Client $client -Html
Assert-True ($loginPage.Status -eq 200) 'login page is available'

$csrfMatch = [regex]::Match($loginPage.Body, 'name="_csrf"[^>]*value="([^"]+)"')
if (-not $csrfMatch.Success) {
    $csrfMatch = [regex]::Match($loginPage.Body, 'value="([^"]+)"[^>]*name="_csrf"')
}
Assert-True $csrfMatch.Success 'login page contains a CSRF token'
$csrf = $csrfMatch.Groups[1].Value

$loginBody = 'username=' + [uri]::EscapeDataString($Username) +
    '&password=' + [uri]::EscapeDataString($Password) +
    '&_csrf=' + [uri]::EscapeDataString($csrf)
$login = Send-HttpRequest -Method 'POST' -Url "$AuthBase/login" -FormBody $loginBody -Client $client
Assert-True ($login.Status -eq 302) 'form login accepted'

$next = $login
for ($i = 0; $i -lt 5; $i++) {
    if ($null -eq $next.Location) { break }
    if ($next.Location.StartsWith($redirectUri)) { break }
    $nextUrl = if ($next.Location.StartsWith('http')) { $next.Location } else { "$AuthBase$($next.Location)" }
    $next = Send-HttpRequest -Method 'GET' -Url $nextUrl -Client $client -Html
}
Assert-True ($next.Location -and $next.Location.StartsWith($redirectUri)) 'authorization code returned to PC Web redirect URI'

$codeMatch = [regex]::Match($next.Location, '[?&]code=([^&]+)')
Assert-True $codeMatch.Success 'authorization code present'
$code = [uri]::UnescapeDataString($codeMatch.Groups[1].Value)

$tokenBody = New-QueryString @{
    grant_type = 'authorization_code'
    code = $code
    redirect_uri = $redirectUri
    client_id = 'pc-web'
    code_verifier = $codeVerifier
}
$tokenResponse = Send-HttpRequest -Method 'POST' -Url "$AuthBase/oauth2/token" -FormBody $tokenBody -Client $client
Assert-True ($tokenResponse.Status -eq 200) 'authorization code exchanged for tokens'
$tokens = $tokenResponse.Body | ConvertFrom-Json
Assert-True ([bool]$tokens.access_token) 'user access token issued'

$me = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/system/me" -BearerToken $tokens.access_token -Client $client
if ($me.Status -ne 200) {
    Write-Host "me response: status=$($me.Status) location=$($me.Location) body=$($me.Body)"
}
Assert-True ($me.Status -eq 200) 'user token accepted by gateway/system'
$meBody = $me.Body | ConvertFrom-Json
Assert-True ($meBody.data.username -eq $Username) 'JWT carries the expected username'
Assert-True ([bool]$meBody.data.userId) 'JWT carries the platform userId claim'

$aiStatusResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/ai/status" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($aiStatusResponse.Status -eq 200) 'AI assistant status route works through gateway'
$aiStatusBody = $aiStatusResponse.Body | ConvertFrom-Json
Assert-True ([bool]$aiStatusBody.data.model) 'AI assistant exposes the configured local model'

# 订单与设备都必须使用真实工厂 ID；演示数据早期使用过 2001，已统一修正。
$factoriesResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/system/factories" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($factoriesResponse.Status -eq 200) 'factory list route works through gateway'
$factoriesBody = $factoriesResponse.Body | ConvertFrom-Json
Assert-True ($factoriesBody.data.Count -ge 1) 'seed data contains at least one factory'
$factoryId = $factoriesBody.data[0].id
Assert-True ([bool]$factoryId) 'factory list exposes an id'

# 订单必须引用财务已录入合同，先按客户 9001 拉取可用合同。
$contractResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/customer/contracts/available?customerId=9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($contractResponse.Status -eq 200) 'available contracts route works for order creator'
$contractBody = $contractResponse.Body | ConvertFrom-Json
Assert-True ($contractBody.data.Count -ge 1) 'seed customer 9001 has an entered contract'
$contractId = $contractBody.data[0].id
Assert-True ([bool]$contractId) 'available contract exposes an id'

$occupancyResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/orders/occupancy?customerId=9001" -BearerToken $tokens.access_token -Client $client
Assert-True ($occupancyResponse.Status -eq 200) 'credit occupancy route works through gateway'
$occupancyBody = $occupancyResponse.Body | ConvertFrom-Json
Assert-True ([string]$occupancyBody.data.customerId -eq '9001') 'credit occupancy returns the requested customer'

$orderJson = (@{
    customerId = 9001
    contractId = $contractId
    factoryId = $factoryId
    totalAmount = 990001
    currency = 'CNY'
    remark = 'smoke test order'
} | ConvertTo-Json -Compress)
$orderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/orders" `
    -JsonBody $orderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($orderResponse.Status -eq 200) 'user token can create an order through gateway'
$orderBody = $orderResponse.Body | ConvertFrom-Json
Assert-True ($orderBody.data.createdBy -eq $meBody.data.userId) 'order.createdBy records the JWT userId'
Assert-True ([string]$orderBody.data.contractId -eq [string]$contractId) 'order stores the selected contractId'
$orderNo = $orderBody.data.orderNo
Assert-True ([bool]$orderNo) 'created order has an orderNo'

$detail = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/orders/$orderNo" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($detail.Status -eq 200) 'order detail route works through gateway'
$detailBody = $detail.Body | ConvertFrom-Json
Assert-True ($detailBody.data.orderNo -eq $orderNo) 'order detail returns the requested shard'
Assert-True ([string]$detailBody.data.contractId -eq [string]$contractId) 'order detail returns the contractId'

$page = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/orders/page?orderNo=$orderNo&page=1&size=10" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($page.Status -eq 200) 'order page route works through gateway'
$pageBody = $page.Body | ConvertFrom-Json
Assert-True ($pageBody.data.total -ge 1) 'order page includes the created order'
Assert-True ($pageBody.data.records[0].orderNo -eq $orderNo) 'order page filters by orderNo'

Write-Host ''
Write-Host '== 3. Equipment ledger and FIFO production queue =='
$deviceNo = 'SMOKE-EQ-' + (Get-Date -Format 'HHmmssfff')
$deviceJson = (@{
    deviceNo = $deviceNo
    deviceName = '冒烟测试设备'
    deviceType = '测试印花机'
    model = 'SMOKE-1800'
    factoryId = $factoryId
    workshop = '冒烟测试车间'
    location = '测试位置'
    materialTypes = 'COTTON'
    minWidthMm = 500
    maxWidthMm = 2000
    processTypes = 'PRINT'
    capacityMetersPerHour = '600.00'
    changeoverMinutes = 1
    yieldRate = '99.00'
    operatorRequirement = '冒烟测试资质'
    remark = 'smoke test device'
} | ConvertTo-Json -Compress)
$deviceResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/devices" `
    -JsonBody $deviceJson -BearerToken $tokens.access_token -Client $client
Assert-True ($deviceResponse.Status -eq 200) 'admin can create an equipment record'
$deviceBody = $deviceResponse.Body | ConvertFrom-Json
$deviceId = $deviceBody.data.id
Assert-True ([bool]$deviceId) 'created device exposes an id'
Assert-True ($deviceBody.data.status -eq 'IDLE') 'new device starts as IDLE'
Assert-True ([string]$deviceBody.data.factoryId -eq [string]$factoryId) 'device belongs to the selected factory'

$workOrderIds = @()
$workOrderLetters = @('A', 'B', 'C')
$workOrderMeters = @(100, 50, 30)
for ($i = 0; $i -lt $workOrderLetters.Count; $i++) {
    $workOrderJson = (@{
        orderNo = $orderNo
        deviceId = $deviceId
        materialType = 'COTTON'
        processType = 'PRINT'
        fabricWidthMm = 1500
        plannedMeters = $workOrderMeters[$i]
        remark = "smoke test work order $($workOrderLetters[$i])"
    } | ConvertTo-Json -Compress)
    $workOrderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders" `
        -JsonBody $workOrderJson -BearerToken $tokens.access_token -Client $client
    Assert-True ($workOrderResponse.Status -eq 200) "work order $($workOrderLetters[$i]) can be queued"
    $workOrderBody = $workOrderResponse.Body | ConvertFrom-Json
    Assert-True ($workOrderBody.data.status -eq 'QUEUED') "work order $($workOrderLetters[$i]) starts as QUEUED"
    Assert-True ([int]$workOrderBody.data.queuePosition -eq ($i + 1)) `
        "work order $($workOrderLetters[$i]) is at FIFO position $($i + 1)"
    Assert-True ([bool]$workOrderBody.data.plannedStart) `
        "work order $($workOrderLetters[$i]) has a planned start time"
    $workOrderIds += $workOrderBody.data.id
}

$startWorkOrder = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[0])/start" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($startWorkOrder.Status -eq 200) 'first queued work order can start'
$startWorkOrderBody = $startWorkOrder.Body | ConvertFrom-Json
Assert-True ($startWorkOrderBody.data.status -eq 'RUNNING') 'started work order becomes RUNNING'

$invalidDeviceStatus = (@{ status = 'FAULT'; reason = 'smoke test while running' } | ConvertTo-Json -Compress)
$invalidStatusResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/devices/$deviceId/status" `
    -JsonBody $invalidDeviceStatus -BearerToken $tokens.access_token -Client $client
Assert-True ($invalidStatusResponse.Status -ge 400) 'device cannot be stopped while a work order is running'

$completeWorkOrder = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[0])/complete" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($completeWorkOrder.Status -eq 200) 'running work order can complete'
$completeWorkOrderBody = $completeWorkOrder.Body | ConvertFrom-Json
Assert-True ($completeWorkOrderBody.data.status -eq 'COMPLETED') 'completed work order becomes COMPLETED'

$workOrderPageResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/work-orders/page?orderNo=$orderNo&page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($workOrderPageResponse.Status -eq 200) 'work order page route works through gateway'
$workOrderPageBody = $workOrderPageResponse.Body | ConvertFrom-Json
Assert-True ($workOrderPageBody.data.total -eq 3) 'work order page returns all three work orders'
$secondWorkOrder = $workOrderPageBody.data.records | Where-Object { [string]$_.id -eq [string]$workOrderIds[1] }
$thirdWorkOrder = $workOrderPageBody.data.records | Where-Object { [string]$_.id -eq [string]$workOrderIds[2] }
Assert-True ([int]$secondWorkOrder.queuePosition -eq 1) 'remaining queue moves to FIFO position 1 after completion'
Assert-True ([int]$thirdWorkOrder.queuePosition -eq 2) 'remaining queue keeps FIFO order after completion'

$jumpJson = (@{
    targetPosition = 1
    reason = 'smoke test jump reason'
} | ConvertTo-Json -Compress)
$jumpResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[2])/jump-requests" `
    -JsonBody $jumpJson -BearerToken $tokens.access_token -Client $client
Assert-True ($jumpResponse.Status -eq 200) 'sales can request a queue jump for a waiting work order'
$jumpBody = $jumpResponse.Body | ConvertFrom-Json
$jumpRequestId = $jumpBody.data.id
Assert-True ($jumpBody.data.status -eq 'PENDING') 'queue jump request starts as PENDING'

# 插队审批已迁入审批中心：申请会生成审批单，审批人在待办中处理。
$jumpApprovalPage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/approval/instances/page?businessType=QUEUE_JUMP&page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($jumpApprovalPage.Status -eq 200) 'approval instance page route works for queue jump'
$jumpApprovalBody = $jumpApprovalPage.Body | ConvertFrom-Json
$jumpApproval = $jumpApprovalBody.data.records | Where-Object {
    [string]$_.businessId -eq [string]$jumpRequestId
} | Select-Object -First 1
Assert-True ([bool]$jumpApproval) 'the queue jump request is submitted to the approval center'
Assert-True ($jumpApproval.status -eq 'PENDING') 'the queue jump approval starts as PENDING'

$jumpTodoPage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/approval/todos/page?page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
$jumpTodoBody = $jumpTodoPage.Body | ConvertFrom-Json
$jumpTodo = $jumpTodoBody.data.records | Where-Object {
    [string]$_.id -eq [string]$jumpApproval.id
} | Select-Object -First 1
Assert-True ([bool]$jumpTodo) 'the queue jump approval appears in the approver todo list'

$approveJson = (@{ comment = 'smoke test approve' } | ConvertTo-Json -Compress)
$approveResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($jumpTodo.tasks[0].id)/approve" `
    -JsonBody $approveJson -BearerToken $tokens.access_token -Client $client
Assert-True ($approveResponse.Status -eq 200) 'factory manager can approve a queue jump request in the approval center'
Assert-True (($approveResponse.Body | ConvertFrom-Json).data.status -eq 'APPROVED') `
    'the queue jump approval becomes APPROVED'

$jumpAfterApproval = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/jump-requests/$jumpRequestId" `
    -BearerToken $tokens.access_token -Client $client
$jumpAfterApprovalBody = $jumpAfterApproval.Body | ConvertFrom-Json
Assert-True ($jumpAfterApprovalBody.data.status -eq 'APPROVED') 'the queue jump request is synced as APPROVED'
Assert-True ([int]$jumpAfterApprovalBody.data.displacedCount -eq 1) 'approval records one displaced work order'

$workOrderPageAfterJump = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/work-orders/page?orderNo=$orderNo&page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
$workOrderPageAfterJumpBody = $workOrderPageAfterJump.Body | ConvertFrom-Json
$thirdAfterJump = $workOrderPageAfterJumpBody.data.records | Where-Object { [string]$_.id -eq [string]$workOrderIds[2] }
$secondAfterJump = $workOrderPageAfterJumpBody.data.records | Where-Object { [string]$_.id -eq [string]$workOrderIds[1] }
Assert-True ([int]$thirdAfterJump.queuePosition -eq 1) 'approved jump moves the work order to queue position 1'
Assert-True ([int]$secondAfterJump.queuePosition -eq 2) 'displaced work order moves to queue position 2'

$messagesResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/messages/page?page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($messagesResponse.Status -eq 200) 'station message page route works through gateway'
$messagesBody = $messagesResponse.Body | ConvertFrom-Json
$displacedMessage = $messagesBody.data.records | Where-Object {
    $_.messageType -eq 'QUEUE_DISPLACED' -and $_.content -like '*smoke test jump reason*'
} | Select-Object -First 1
Assert-True ([bool]$displacedMessage) 'displaced salesperson receives a station message with the jump reason'
Assert-True ([int64]$messagesBody.data.unreadCount -ge 1) 'station message page reports unread messages'

$unreadResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/messages/unread-count" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($unreadResponse.Status -eq 200) 'unread message count route works through gateway'
$unreadBody = $unreadResponse.Body | ConvertFrom-Json
Assert-True ([int64]$unreadBody.data.unreadCount -ge 1) 'unread message count includes queue jump notifications'

$readAllResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/messages/read-all" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($readAllResponse.Status -eq 200) 'all station messages can be marked as read'

$startThird = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[2])/start" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($startThird.Status -eq 200) 'jumped work order can start at queue head'
$completeThird = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[2])/complete" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($completeThird.Status -eq 200) 'jumped work order can complete'

$startSecond = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[1])/start" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($startSecond.Status -eq 200) 'displaced work order can start after the jump work order completes'
$completeSecond = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$($workOrderIds[1])/complete" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($completeSecond.Status -eq 200) 'displaced work order can complete'

$orderAfterProduction = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/orders/$orderNo" `
    -BearerToken $tokens.access_token -Client $client
$orderAfterProductionBody = $orderAfterProduction.Body | ConvertFrom-Json
Assert-True ($orderAfterProductionBody.data.orderStatus -eq 'COMPLETED') `
    'order status becomes COMPLETED after every work order completes'

$deviceAfterProduction = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/devices/$deviceId" `
    -BearerToken $tokens.access_token -Client $client
$deviceAfterProductionBody = $deviceAfterProduction.Body | ConvertFrom-Json
Assert-True ($deviceAfterProductionBody.data.status -eq 'IDLE') 'device returns to IDLE after the last work order completes'
Assert-True (-not $deviceAfterProductionBody.data.currentWorkOrderId) `
    'device clears currentWorkOrderId after the last work order completes'

Write-Host ''
Write-Host '== 4. Device maintenance orders and inspection records =='
# 维护工单需要一台空闲设备；这里固定使用演示设备，避免受台账顺序影响。
$deviceListResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/devices/page?keyword=EQ-F001-01&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($deviceListResponse.Status -eq 200) 'device list route works for maintenance'
$deviceListBody = $deviceListResponse.Body | ConvertFrom-Json
Assert-True ($deviceListBody.data.records.Count -ge 1) 'the demo device is available for maintenance'
$maintenanceDevice = $deviceListBody.data.records[0]
$maintenanceDeviceId = $maintenanceDevice.id
Assert-True ($maintenanceDevice.status -eq 'IDLE') 'the demo device is idle before the maintenance scenario'

# 1) 登记一条异常点检。
$inspectionJson = (@{
    deviceId = $maintenanceDeviceId
    planType = 'DAILY'
    inspectionDate = (Get-Date).ToString('yyyy-MM-dd')
    result = 'ABNORMAL'
    itemCount = 6
    abnormalCount = 1
    content = 'smoke test inspection abnormal item'
} | ConvertTo-Json -Compress)
$inspectionResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/inspections" `
    -JsonBody $inspectionJson -BearerToken $tokens.access_token -Client $client
Assert-True ($inspectionResponse.Status -eq 200) 'inspection record can be registered'
$inspectionBody = $inspectionResponse.Body | ConvertFrom-Json
$inspectionId = $inspectionBody.data.id
Assert-True ($inspectionBody.data.result -eq 'ABNORMAL') 'inspection record keeps the abnormal result'
Assert-True ($inspectionBody.data.abnormalCount -eq 1) 'inspection record keeps the abnormal item count'

# 2) 正常点检不允许生成维护工单。
$normalInspectionJson = (@{
    deviceId = $maintenanceDeviceId
    planType = 'DAILY'
    inspectionDate = (Get-Date).ToString('yyyy-MM-dd')
    result = 'NORMAL'
    itemCount = 6
    abnormalCount = 0
    content = 'smoke test inspection normal item'
} | ConvertTo-Json -Compress)
$normalInspectionResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/inspections" `
    -JsonBody $normalInspectionJson -BearerToken $tokens.access_token -Client $client
Assert-True ($normalInspectionResponse.Status -eq 200) 'normal inspection record can be registered'
$normalInspectionId = ($normalInspectionResponse.Body | ConvertFrom-Json).data.id
$normalToMaintenance = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/inspections/$normalInspectionId/maintenance-order" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($normalToMaintenance.Status -ge 400) 'a normal inspection cannot create a maintenance order'

# 3) 异常点检生成维护工单。
$inspectionToMaintenance = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/inspections/$inspectionId/maintenance-order" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($inspectionToMaintenance.Status -eq 200) 'an abnormal inspection can create a maintenance order'
$maintenanceBody = $inspectionToMaintenance.Body | ConvertFrom-Json
$maintenanceId = $maintenanceBody.data.id
Assert-True ($maintenanceBody.data.status -eq 'PENDING') 'generated maintenance order starts as PENDING'
Assert-True ($maintenanceBody.data.maintenanceType -eq 'INSPECTION') 'generated maintenance order is an inspection fix'
Assert-True ([string]$maintenanceBody.data.inspectionId -eq [string]$inspectionId) `
    'generated maintenance order links back to the inspection record'

# 4) 新建一张订单并排一个等待工单，用于验证停机与恢复通知。
$maintenanceOrderJson = (@{
    customerId = 9001
    contractId = $contractId
    factoryId = $maintenanceDevice.factoryId
    totalAmount = 990002
    remark = 'smoke test maintenance order'
} | ConvertTo-Json -Compress)
$maintenanceOrderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/orders" `
    -JsonBody $maintenanceOrderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($maintenanceOrderResponse.Status -eq 200) 'a second order can be created for the maintenance scenario'
$maintenanceOrderNo = ($maintenanceOrderResponse.Body | ConvertFrom-Json).data.orderNo

$queuedWorkOrderJson = (@{
    orderNo = $maintenanceOrderNo
    deviceId = $maintenanceDeviceId
    materialType = 'COTTON'
    processType = 'PRINT'
    fabricWidthMm = 1500
    plannedMeters = 60
    remark = 'smoke test maintenance work order'
} | ConvertTo-Json -Compress)
$queuedWorkOrderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders" `
    -JsonBody $queuedWorkOrderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($queuedWorkOrderResponse.Status -eq 200) 'a work order can wait on the device queue'
$queuedWorkOrderId = ($queuedWorkOrderResponse.Body | ConvertFrom-Json).data.id

# 5) 开始维护：设备停机，等待队列保留并通知业务员。
$startMaintenance = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/maintenance-orders/$maintenanceId/start" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($startMaintenance.Status -eq 200) 'maintenance order can start'
$startMaintenanceBody = $startMaintenance.Body | ConvertFrom-Json
Assert-True ($startMaintenanceBody.data.status -eq 'IN_PROGRESS') 'started maintenance order becomes IN_PROGRESS'

$deviceDuringMaintenance = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/devices/$maintenanceDeviceId" `
    -BearerToken $tokens.access_token -Client $client
$deviceDuringMaintenanceBody = $deviceDuringMaintenance.Body | ConvertFrom-Json
Assert-True ($deviceDuringMaintenanceBody.data.status -eq 'MAINTENANCE') 'device turns to MAINTENANCE during maintenance'

$blockedWorkOrder = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders" `
    -JsonBody $queuedWorkOrderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($blockedWorkOrder.Status -ge 400) 'a device under maintenance cannot accept new work orders'

$queueDuringMaintenance = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/work-orders/page?orderNo=$maintenanceOrderNo&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
$queueDuringMaintenanceBody = $queueDuringMaintenance.Body | ConvertFrom-Json
$waitingDuringMaintenance = $queueDuringMaintenanceBody.data.records | Where-Object {
    [string]$_.id -eq [string]$queuedWorkOrderId
}
Assert-True ($waitingDuringMaintenance.queueStatus -eq 'WAITING') 'the waiting queue is preserved during maintenance'

$stopMessages = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/messages/page?page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
$stopMessagesBody = $stopMessages.Body | ConvertFrom-Json
$stopMessage = $stopMessagesBody.data.records | Where-Object {
    $_.messageType -eq 'SYSTEM' -and $_.content -like '*已转为保养中*'
} | Select-Object -First 1
Assert-True ([bool]$stopMessage) 'the affected salesperson is notified about the device downtime'

# 6) 完成维护：记录成本，设备恢复空闲并重算队列时间。
$completeMaintenanceJson = (@{
    costAmount = 12345
    costNote = 'smoke test maintenance cost'
    downtimeMinutes = 30
} | ConvertTo-Json -Compress)
$completeMaintenance = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/maintenance-orders/$maintenanceId/complete" `
    -JsonBody $completeMaintenanceJson -BearerToken $tokens.access_token -Client $client
Assert-True ($completeMaintenance.Status -eq 200) 'maintenance order can complete'
$completeMaintenanceBody = $completeMaintenance.Body | ConvertFrom-Json
Assert-True ($completeMaintenanceBody.data.status -eq 'COMPLETED') 'completed maintenance order becomes COMPLETED'
Assert-True ([int64]$completeMaintenanceBody.data.costAmount -eq 12345) 'maintenance cost is recorded in cents'
Assert-True ([int]$completeMaintenanceBody.data.downtimeMinutes -eq 30) 'maintenance downtime minutes are recorded'

$deviceAfterMaintenance = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/devices/$maintenanceDeviceId" `
    -BearerToken $tokens.access_token -Client $client
$deviceAfterMaintenanceBody = $deviceAfterMaintenance.Body | ConvertFrom-Json
Assert-True ($deviceAfterMaintenanceBody.data.status -eq 'IDLE') 'device returns to IDLE after maintenance completes'

$repeatComplete = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/maintenance-orders/$maintenanceId/complete" `
    -JsonBody $completeMaintenanceJson -BearerToken $tokens.access_token -Client $client
Assert-True ($repeatComplete.Status -ge 400) 'a completed maintenance order cannot be completed twice'

$restoreMessages = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/messages/page?page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
$restoreMessagesBody = $restoreMessages.Body | ConvertFrom-Json
$restoreMessage = $restoreMessagesBody.data.records | Where-Object {
    $_.messageType -eq 'SYSTEM' -and $_.title -like '*设备恢复生产*'
} | Select-Object -First 1
Assert-True ([bool]$restoreMessage) 'the waiting salesperson is notified when the device is restored'

# 7) 维护单列表按筛选条件返回，并带出汇总指标。
$maintenancePage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/maintenance-orders/page?deviceId=$maintenanceDeviceId&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($maintenancePage.Status -eq 200) 'maintenance order page route works through gateway'
$maintenancePageBody = $maintenancePage.Body | ConvertFrom-Json
Assert-True ($maintenancePageBody.data.total -ge 1) 'maintenance order page includes the created order'
Assert-True ([int64]$maintenancePageBody.data.summary.costAmountTotal -ge 12345) `
    'maintenance summary aggregates the recorded cost'

# 8) 收尾：完成等待中的工单，让第二张订单也走到已完成。
$startQueuedWorkOrder = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$queuedWorkOrderId/start" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($startQueuedWorkOrder.Status -eq 200) 'the preserved work order can start after maintenance'
$completeQueuedWorkOrder = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/order/work-orders/$queuedWorkOrderId/complete" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($completeQueuedWorkOrder.Status -eq 200) 'the preserved work order can complete after maintenance'

Write-Host ''
Write-Host '== 5. Warehouse: purchase inbound, inventory and cross-factory transfer =='
# 仓库：每个工厂至少一个主仓，这里取一厂、二厂各一个。
$warehousePage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/warehouses/page?page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($warehousePage.Status -eq 200) 'warehouse page route works through gateway'
$warehousePageBody = $warehousePage.Body | ConvertFrom-Json
Assert-True ($warehousePageBody.data.total -ge 2) 'at least two warehouses exist for the two factories'
$sourceWarehouse = $warehousePageBody.data.records | Where-Object { $_.factoryId -eq '1' } | Select-Object -First 1
$targetWarehouse = $warehousePageBody.data.records | Where-Object { $_.factoryId -eq '2' } | Select-Object -First 1
Assert-True ([bool]$sourceWarehouse) 'factory one has a warehouse'
Assert-True ([bool]$targetWarehouse) 'factory two has a warehouse'
Assert-True ($sourceWarehouse.warehouseType -eq 'MAIN') 'the factory warehouse is a main warehouse'

# 物料：取演示布料，读取单价快照用于金额校验。
$materialPage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/materials/page?keyword=MAT-COTTON-128&page=1&size=10" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($materialPage.Status -eq 200) 'material page route works through gateway'
$materialBody = ($materialPage.Body | ConvertFrom-Json).data.records[0]
Assert-True ([bool]$materialBody) 'the demo cotton material exists'
$materialId = $materialBody.id
$materialPrice = [decimal]$materialBody.unitPrice

# 库存：演示入库单生成的初始库存。
$inventoryPage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?materialId=$materialId&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($inventoryPage.Status -eq 200) 'inventory page route works through gateway'
$inventoryBody = ($inventoryPage.Body | ConvertFrom-Json)
$sourceInventory = $inventoryBody.data.records | Where-Object { $_.warehouseId -eq $sourceWarehouse.id } | Select-Object -First 1
Assert-True ([bool]$sourceInventory) 'the source warehouse holds the demo material'
$quantityBefore = [decimal]$sourceInventory.quantity

# 1) 新建采购入库单（草稿），金额按“数量 × 单价”截断到分。
$inboundQuantity = 120
$receiptJson = (@{
    warehouseId = $sourceWarehouse.id
    supplierName = 'smoke test supplier'
    receiptDate = (Get-Date).ToString('yyyy-MM-dd')
    remark = 'smoke test purchase receipt'
    lines = @(@{ materialId = $materialId; quantity = $inboundQuantity; remark = 'smoke test line' })
} | ConvertTo-Json -Compress -Depth 5)
$receiptResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/purchase-receipts" `
    -JsonBody $receiptJson -BearerToken $tokens.access_token -Client $client
Assert-True ($receiptResponse.Status -eq 200) 'a purchase receipt can be created'
$receiptBody = $receiptResponse.Body | ConvertFrom-Json
$receiptId = $receiptBody.data.id
Assert-True ($receiptBody.data.status -eq 'DRAFT') 'a new purchase receipt starts as DRAFT'
$expectedAmount = [int64][math]::Floor($inboundQuantity * $materialPrice * 100)
Assert-True ([int64]$receiptBody.data.totalAmount -eq $expectedAmount) `
    'purchase receipt amount equals quantity times unit price truncated to cents'
Assert-True ([int64]$receiptBody.data.lines[0].amount -eq $expectedAmount) `
    'purchase receipt line amount is persisted'

# 2) 草稿状态不增加库存：确认收货后才是库存来源。
$inventoryBeforeReceive = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityBeforeReceive = [decimal](($inventoryBeforeReceive.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityBeforeReceive -eq $quantityBefore) 'a draft receipt does not change the inventory'

$receiveResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/purchase-receipts/$receiptId/receive" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($receiveResponse.Status -eq 200) 'a draft purchase receipt can be received'
$receiveBody = $receiveResponse.Body | ConvertFrom-Json
Assert-True ($receiveBody.data.status -eq 'RECEIVED') 'a received purchase receipt becomes RECEIVED'

$inventoryAfterReceive = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityAfterReceive = [decimal](($inventoryAfterReceive.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityAfterReceive -eq ($quantityBefore + $inboundQuantity)) `
    'receiving the receipt increases the inventory by the received quantity'

$receiveAgain = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/purchase-receipts/$receiptId/receive" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($receiveAgain.Status -ge 400) 'a purchase receipt cannot be received twice'

# 3) 跨厂调拨：出库扣减来源库存，入库增加目标库存。
$transferJson = (@{
    fromWarehouseId = $sourceWarehouse.id
    toWarehouseId = $targetWarehouse.id
    plannedDate = (Get-Date).ToString('yyyy-MM-dd')
    remark = 'smoke test stock transfer'
    lines = @(@{ materialId = $materialId; quantity = 10; remark = 'smoke test transfer line' })
} | ConvertTo-Json -Compress -Depth 5)
$transferResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers" `
    -JsonBody $transferJson -BearerToken $tokens.access_token -Client $client
Assert-True ($transferResponse.Status -eq 200) 'a cross-factory transfer can be created'
$transferBody = $transferResponse.Body | ConvertFrom-Json
$transferId = $transferBody.data.id
Assert-True ($transferBody.data.status -eq 'PENDING') 'a new transfer starts as PENDING'
Assert-True ($transferBody.data.fromFactoryId -ne $transferBody.data.toFactoryId) 'the transfer crosses factories'

# 库存不足时整单失败，来源库存不变。
$tooMuchJson = (@{
    fromWarehouseId = $sourceWarehouse.id
    toWarehouseId = $targetWarehouse.id
    plannedDate = (Get-Date).ToString('yyyy-MM-dd')
    remark = 'smoke test oversized transfer'
    lines = @(@{ materialId = $materialId; quantity = 99999999 })
} | ConvertTo-Json -Compress -Depth 5)
$tooMuchResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers" `
    -JsonBody $tooMuchJson -BearerToken $tokens.access_token -Client $client
$tooMuchId = ($tooMuchResponse.Body | ConvertFrom-Json).data.id
$tooMuchShip = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers/$tooMuchId/ship" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($tooMuchShip.Status -ge 400) 'a transfer cannot ship more than the available stock'
$inventoryAfterFailedShip = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityAfterFailedShip = [decimal](($inventoryAfterFailedShip.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityAfterFailedShip -eq $quantityAfterReceive) 'a failed ship does not change the inventory'

$shipResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers/$transferId/ship" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($shipResponse.Status -eq 200) 'a pending transfer can be shipped'
Assert-True (($shipResponse.Body | ConvertFrom-Json).data.status -eq 'IN_TRANSIT') 'a shipped transfer becomes IN_TRANSIT'

$inventoryAfterShip = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityAfterShip = [decimal](($inventoryAfterShip.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityAfterShip -eq ($quantityAfterReceive - 10)) 'shipping the transfer reduces the source inventory'

$transferReceive = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers/$transferId/receive" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($transferReceive.Status -eq 200) 'an in-transit transfer can be received'
Assert-True (($transferReceive.Body | ConvertFrom-Json).data.status -eq 'COMPLETED') 'a received transfer becomes COMPLETED'

$targetInventory = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($targetWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$targetInventoryBody = $targetInventory.Body | ConvertFrom-Json
$targetQuantity = if ($targetInventoryBody.data.records.Count -ge 1) { [decimal]$targetInventoryBody.data.records[0].quantity } else { [decimal]0 }
Assert-True ($targetQuantity -eq 10) 'receiving the transfer increases the target warehouse inventory'

# 4) 待出库的调拨单可以取消。
$cancelJson = (@{
    fromWarehouseId = $sourceWarehouse.id
    toWarehouseId = $targetWarehouse.id
    plannedDate = (Get-Date).ToString('yyyy-MM-dd')
    remark = 'smoke test cancellable transfer'
    lines = @(@{ materialId = $materialId; quantity = 1 })
} | ConvertTo-Json -Compress -Depth 5)
$cancellableId = ((Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers" `
        -JsonBody $cancelJson -BearerToken $tokens.access_token -Client $client).Body | ConvertFrom-Json).data.id
$cancelTransfer = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/stock-transfers/$cancellableId/cancel" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($cancelTransfer.Status -eq 200) 'a pending transfer can be cancelled'
Assert-True (($cancelTransfer.Body | ConvertFrom-Json).data.status -eq 'CANCELLED') 'a cancelled transfer becomes CANCELLED'

Write-Host ''
Write-Host '== 6. Material issue and work order material cost =='
# 领料需要一张排队中的工单，这里新建订单与工单。
$issueOrderJson = (@{
    customerId = 9001
    contractId = $contractId
    factoryId = $factoryId
    totalAmount = 990003
    remark = 'smoke test material issue order'
} | ConvertTo-Json -Compress)
$issueOrderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/orders" `
    -JsonBody $issueOrderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($issueOrderResponse.Status -eq 200) 'an order can be created for the material issue scenario'
$issueOrderNo = ($issueOrderResponse.Body | ConvertFrom-Json).data.orderNo

$issueWorkOrderJson = (@{
    orderNo = $issueOrderNo
    deviceId = 1
    materialType = 'COTTON'
    processType = 'PRINT'
    fabricWidthMm = 1500
    plannedMeters = 80
    remark = 'smoke test material issue work order'
} | ConvertTo-Json -Compress)
$issueWorkOrderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders" `
    -JsonBody $issueWorkOrderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($issueWorkOrderResponse.Status -eq 200) 'a work order can be created for the material issue scenario'
$issueWorkOrderId = ($issueWorkOrderResponse.Body | ConvertFrom-Json).data.id

# 1) 领料单建单：待发料，不扣库存。
$issueQuantity = 10
$issueJson = (@{
    warehouseId = $sourceWarehouse.id
    remark = 'smoke test material issue'
    lines = @(@{ materialId = $materialId; quantity = $issueQuantity; remark = 'smoke test issue line' })
} | ConvertTo-Json -Compress -Depth 5)
$issueResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders/$issueWorkOrderId/issues" `
    -JsonBody $issueJson -BearerToken $tokens.access_token -Client $client
Assert-True ($issueResponse.Status -eq 200) 'a material issue can be created for a work order'
$issueBody = $issueResponse.Body | ConvertFrom-Json
$issueId = $issueBody.data.id
Assert-True ($issueBody.data.status -eq 'PENDING') 'a new material issue starts as PENDING'
$issueExpectedAmount = [int64][math]::Floor($issueQuantity * $materialPrice * 100)
Assert-True ([int64]$issueBody.data.totalAmount -eq $issueExpectedAmount) `
    'material issue amount equals quantity times unit price truncated to cents'

$inventoryBeforeIssue = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityBeforeIssue = [decimal](($inventoryBeforeIssue.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityBeforeIssue -eq $quantityAfterShip) 'creating a material issue does not change the inventory'

$workOrderBeforeIssue = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/work-orders/by-order/$issueOrderNo" `
    -BearerToken $tokens.access_token -Client $client
$workOrderBeforeIssueBody = @($workOrderBeforeIssue.Body | ConvertFrom-Json).data[0]
Assert-True ([int64]$workOrderBeforeIssueBody.materialCostAmount -eq 0) `
    'a work order starts with zero material cost'

# 2) 确认发料：扣减库存并计入工单材料成本。
$issueShipResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/material-issues/$issueId/issue" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($issueShipResponse.Status -eq 200) 'a pending material issue can be issued'
$issueShipBody = $issueShipResponse.Body | ConvertFrom-Json
Assert-True ($issueShipBody.data.status -eq 'COMPLETED') 'an issued material issue becomes COMPLETED'

$inventoryAfterIssue = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityAfterIssue = [decimal](($inventoryAfterIssue.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityAfterIssue -eq ($quantityBeforeIssue - $issueQuantity)) `
    'issuing material reduces the warehouse inventory'

$workOrderAfterIssue = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/work-orders/by-order/$issueOrderNo" `
    -BearerToken $tokens.access_token -Client $client
$workOrderAfterIssueBody = @($workOrderAfterIssue.Body | ConvertFrom-Json).data[0]
Assert-True ([int64]$workOrderAfterIssueBody.materialCostAmount -eq $issueExpectedAmount) `
    'issued material is added to the work order material cost'

$issueAgain = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/material-issues/$issueId/issue" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($issueAgain.Status -ge 400) 'a material issue cannot be issued twice'

# 3) 库存不足时整单失败，库存不变。
$tooMuchIssueJson = (@{
    warehouseId = $sourceWarehouse.id
    remark = 'smoke test oversized material issue'
    lines = @(@{ materialId = $materialId; quantity = 99999999 })
} | ConvertTo-Json -Compress -Depth 5)
$tooMuchIssueId = ((Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders/$issueWorkOrderId/issues" `
        -JsonBody $tooMuchIssueJson -BearerToken $tokens.access_token -Client $client).Body | ConvertFrom-Json).data.id
$tooMuchIssueResult = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/material-issues/$tooMuchIssueId/issue" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($tooMuchIssueResult.Status -ge 400) 'a material issue cannot exceed the available stock'
$inventoryAfterFailedIssue = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/inventories/page?warehouseId=$($sourceWarehouse.id)&materialId=$materialId&page=1&size=5" `
    -BearerToken $tokens.access_token -Client $client
$quantityAfterFailedIssue = [decimal](($inventoryAfterFailedIssue.Body | ConvertFrom-Json).data.records[0].quantity)
Assert-True ($quantityAfterFailedIssue -eq $quantityAfterIssue) 'a failed material issue does not change the inventory'

# 4) 领料仓库必须与工单同厂。
$crossFactoryIssueJson = (@{
    warehouseId = $targetWarehouse.id
    remark = 'smoke test cross factory material issue'
    lines = @(@{ materialId = $materialId; quantity = 1 })
} | ConvertTo-Json -Compress -Depth 5)
$crossFactoryIssue = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders/$issueWorkOrderId/issues" `
    -JsonBody $crossFactoryIssueJson -BearerToken $tokens.access_token -Client $client
Assert-True ($crossFactoryIssue.Status -ge 400) 'a material issue cannot use a warehouse of another factory'

# 5) 待发料的领料单可以取消。
$cancellableIssueJson = (@{
    warehouseId = $sourceWarehouse.id
    remark = 'smoke test cancellable material issue'
    lines = @(@{ materialId = $materialId; quantity = 1 })
} | ConvertTo-Json -Compress -Depth 5)
$cancellableIssueId = ((Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/work-orders/$issueWorkOrderId/issues" `
        -JsonBody $cancellableIssueJson -BearerToken $tokens.access_token -Client $client).Body | ConvertFrom-Json).data.id
$cancelIssue = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/material-issues/$cancellableIssueId/cancel" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($cancelIssue.Status -eq 200) 'a pending material issue can be cancelled'
Assert-True (($cancelIssue.Body | ConvertFrom-Json).data.status -eq 'CANCELLED') 'a cancelled material issue becomes CANCELLED'

# 6) 领料单分页可按工单查询。
$issuePage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/material-issues/page?workOrderId=$issueWorkOrderId&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($issuePage.Status -eq 200) 'material issue page route works through gateway'
$issuePageBody = $issuePage.Body | ConvertFrom-Json
Assert-True ($issuePageBody.data.total -ge 2) 'material issue page returns the work order issues'
Assert-True ([int64]$issuePageBody.data.summary.completed -ge 1) 'material issue summary counts issued documents'

Write-Host ''
Write-Host '== 7. Approval center: submit, todo, approve and business action =='
# 审批定义：演示数据包含信用额度调整流程（财务审批）。
$definitionPage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/approval/definitions/page?businessType=CREDIT_LIMIT&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($definitionPage.Status -eq 200) 'approval definition page route works through gateway'
$definitionBody = $definitionPage.Body | ConvertFrom-Json
Assert-True ($definitionBody.data.total -ge 1) 'the credit limit approval definition is configured'
$creditDefinition = $definitionBody.data.records[0]
Assert-True ($creditDefinition.approverRole -eq 'FINANCE') 'the credit limit definition is approved by finance'

# 1) 提交信用额度调整申请（待审批，额度不变）。
$creditLimitBefore = 0
$customerBefore = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($customerBefore.Status -eq 200) 'customer detail route works for the approval scenario'
$creditLimitBefore = [int64](($customerBefore.Body | ConvertFrom-Json).data.creditLimit)
$newCreditLimit = $creditLimitBefore + 5000000
$approvalJson = @{
    businessType = 'CREDIT_LIMIT'
    businessId = '9001'
    title = 'smoke test credit limit adjustment'
    amount = $newCreditLimit
    factoryId = $factoryId
    payload = @{ customerId = 9001; creditLimit = $newCreditLimit }
} | ConvertTo-Json -Compress -Depth 4
$approvalResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/instances" `
    -JsonBody $approvalJson -BearerToken $tokens.access_token -Client $client
Assert-True ($approvalResponse.Status -eq 200) 'an approval request can be submitted'
$approvalBody = $approvalResponse.Body | ConvertFrom-Json
$approvalId = $approvalBody.data.id
$approvalTaskId = $approvalBody.data.tasks[0].id
Assert-True ($approvalBody.data.status -eq 'PENDING') 'a submitted approval starts as PENDING'
Assert-True ([bool]$approvalBody.data.definitionId) 'the approval request is matched to a definition'
Assert-True ($approvalBody.data.definitionName -eq $creditDefinition.name) 'the matched definition is reported back'

$customerAfterSubmit = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ([int64](($customerAfterSubmit.Body | ConvertFrom-Json).data.creditLimit) -eq $creditLimitBefore) `
    'a pending approval does not change the customer credit limit'

# 2) 待办查询：申请出现在审批人待办里。
$todoResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/approval/todos/page?page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($todoResponse.Status -eq 200) 'approval todo route works through gateway'
$todoBody = $todoResponse.Body | ConvertFrom-Json
$todoInstance = $todoBody.data.records | Where-Object { $_.id -eq $approvalId } | Select-Object -First 1
Assert-True ([bool]$todoInstance) 'the submitted approval appears in the approver todo list'

# 3) 审批通过：业务动作落地，客户额度更新为申请值。
$approveResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/tasks/$approvalTaskId/approve" `
    -JsonBody (@{ comment = 'smoke test approve' } | ConvertTo-Json -Compress) `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($approveResponse.Status -eq 200) 'a pending approval can be approved'
$approveBody = $approveResponse.Body | ConvertFrom-Json
Assert-True ($approveBody.data.status -eq 'APPROVED') 'an approved request becomes APPROVED'
Assert-True ($approveBody.data.records.Count -ge 2) 'the approval trail records submit and approve'

$customerAfterApprove = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ([int64](($customerAfterApprove.Body | ConvertFrom-Json).data.creditLimit) -eq $newCreditLimit) `
    'approving the request applies the credit limit through the business action'

$approveAgain = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/tasks/$approvalTaskId/approve" `
    -JsonBody (@{ comment = 'smoke test approve again' } | ConvertTo-Json -Compress) `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($approveAgain.Status -ge 400) 'an approval task cannot be processed twice'

# 4) 未配置审批流程的业务类型不允许提交。
$unconfiguredJson = (@{
    businessType = 'NOT_CONFIGURED'
    businessId = '1'
    title = 'smoke test unconfigured'
    amount = 1
    payload = @{}
} | ConvertTo-Json -Compress -Depth 3)
$unconfiguredResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/instances" `
    -JsonBody $unconfiguredJson -BearerToken $tokens.access_token -Client $client
Assert-True ($unconfiguredResponse.Status -ge 400) 'a business type without approval definition cannot be submitted'

# 5) 驳回：必须填写意见，驳回后额度不变。
$rejectJson = @{
    businessType = 'CREDIT_LIMIT'
    businessId = '9001'
    title = 'smoke test credit limit rejection'
    amount = $creditLimitBefore
    payload = @{ customerId = 9001; creditLimit = $creditLimitBefore }
} | ConvertTo-Json -Compress -Depth 4
$rejectRequest = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/instances" `
    -JsonBody $rejectJson -BearerToken $tokens.access_token -Client $client
$rejectTaskId = ($rejectRequest.Body | ConvertFrom-Json).data.tasks[0].id
$rejectWithoutComment = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/tasks/$rejectTaskId/reject" `
    -JsonBody (@{ comment = '' } | ConvertTo-Json -Compress) `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($rejectWithoutComment.Status -ge 400) 'rejecting without a comment is rejected'
$rejectResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/tasks/$rejectTaskId/reject" `
    -JsonBody (@{ comment = 'smoke test reject' } | ConvertTo-Json -Compress) `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($rejectResponse.Status -eq 200) 'a pending approval can be rejected'
Assert-True (($rejectResponse.Body | ConvertFrom-Json).data.status -eq 'REJECTED') 'a rejected request becomes REJECTED'
$customerAfterReject = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ([int64](($customerAfterReject.Body | ConvertFrom-Json).data.creditLimit) -eq $newCreditLimit) `
    'a rejected request does not change the customer credit limit'

# 6) 申请人可撤销待审批申请。
$cancelJson = @{
    businessType = 'CREDIT_LIMIT'
    businessId = '9001'
    title = 'smoke test credit limit cancel'
    amount = $creditLimitBefore
    payload = @{ customerId = 9001; creditLimit = $creditLimitBefore }
} | ConvertTo-Json -Compress -Depth 4
$cancelRequest = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/instances" `
    -JsonBody $cancelJson -BearerToken $tokens.access_token -Client $client
$cancelId = ($cancelRequest.Body | ConvertFrom-Json).data.id
$cancelResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/instances/$cancelId/cancel" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($cancelResponse.Status -eq 200) 'the applicant can cancel a pending request'
Assert-True (($cancelResponse.Body | ConvertFrom-Json).data.status -eq 'CANCELLED') 'a cancelled request becomes CANCELLED'

# 7) 我的申请分页可按申请人查询。
$minePage = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/approval/instances/page?mine=true&page=1&size=50" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($minePage.Status -eq 200) 'approval instance page route works through gateway'
$mineBody = $minePage.Body | ConvertFrom-Json
Assert-True ($mineBody.data.total -ge 3) 'my approval page lists the submitted requests'

# 8) 收尾：把信用额度改回原值，避免测试数据影响其他用例。
$restoreJson = @{
    businessType = 'CREDIT_LIMIT'
    businessId = '9001'
    title = 'smoke test credit limit restore'
    amount = $creditLimitBefore
    payload = @{ customerId = 9001; creditLimit = $creditLimitBefore }
} | ConvertTo-Json -Compress -Depth 4
$restoreRequest = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/instances" `
    -JsonBody $restoreJson -BearerToken $tokens.access_token -Client $client
$restoreTaskId = ($restoreRequest.Body | ConvertFrom-Json).data.tasks[0].id
$restoreApprove = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/approval/tasks/$restoreTaskId/approve" `
    -JsonBody (@{ comment = 'smoke test restore' } | ConvertTo-Json -Compress) `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($restoreApprove.Status -eq 200) 'the smoke test can restore the credit limit'
$customerAfterRestore = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ([int64](($customerAfterRestore.Body | ConvertFrom-Json).data.creditLimit) -eq $creditLimitBefore) `
    'the customer credit limit is restored to the original value'

Write-Host ''
Write-Host '== 8. Finance receivables and receipt allocation =='
# 客户 9001 的信用余额基线：核销完成后应回到基线（应收被收清）。
$creditBefore = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001/credit" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($creditBefore.Status -eq 200) 'customer credit route works through gateway'
$creditBeforeBody = $creditBefore.Body | ConvertFrom-Json
$outstandingBefore = [int64]$creditBeforeBody.data.uncollectedReceivable

$openResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/receivables/open?customerId=9001" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($openResponse.Status -eq 200) 'open receivables route works through gateway'
$openBody = $openResponse.Body | ConvertFrom-Json
Assert-True ($openBody.data.Count -ge 1) 'seed customer 9001 has an open receivable'

# 手工补录 1 分应收，验证“应收 -> 收款 -> 核销 -> 信用释放”整条链路。
$receivableJson = (@{
    customerId = 9001
    contractId = $contractId
    amount = 1
    dueDate = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
    remark = 'smoke test receivable'
} | ConvertTo-Json -Compress)
$receivableResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/receivables" `
    -JsonBody $receivableJson -BearerToken $tokens.access_token -Client $client
Assert-True ($receivableResponse.Status -eq 200) 'finance can create a receivable'
$receivableBody = $receivableResponse.Body | ConvertFrom-Json
$receivableId = $receivableBody.data.id
Assert-True ([bool]$receivableId) 'created receivable exposes an id'

$creditWithReceivable = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001/credit" `
    -BearerToken $tokens.access_token -Client $client
$creditWithBody = $creditWithReceivable.Body | ConvertFrom-Json
Assert-True ([int64]$creditWithBody.data.uncollectedReceivable -eq ($outstandingBefore + 1)) `
    'new receivable increases the credit outstanding'

$receiptJson = (@{
    customerId = 9001
    amount = 1
    receivedDate = (Get-Date).ToString('yyyy-MM-dd')
    method = 'BANK_TRANSFER'
    serialNo = 'SMOKE-SK-0001'
    remark = 'smoke test receipt'
} | ConvertTo-Json -Compress)
$receiptResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/receipts" `
    -JsonBody $receiptJson -BearerToken $tokens.access_token -Client $client
Assert-True ($receiptResponse.Status -eq 200) 'finance can register a receipt'
$receiptBody = $receiptResponse.Body | ConvertFrom-Json
$receiptId = $receiptBody.data.id
Assert-True ([bool]$receiptId) 'created receipt exposes an id'
Assert-True ([int64]$receiptBody.data.availableAmount -eq 1) 'new receipt is fully unallocated'

$allocationJson = (@{
    allocations = @(@{ receivableId = $receivableId; amount = 1 })
} | ConvertTo-Json -Depth 5 -Compress)
$allocationResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/receipts/$receiptId/allocations" `
    -JsonBody $allocationJson -BearerToken $tokens.access_token -Client $client
Assert-True ($allocationResponse.Status -eq 200) 'receipt allocation succeeds'
$allocationBody = $allocationResponse.Body | ConvertFrom-Json
Assert-True ($allocationBody.data.status -eq 'ALLOCATED') 'receipt becomes ALLOCATED'
Assert-True ($allocationBody.data.allocations.Count -eq 1) 'allocation detail is persisted'

$receivableAfter = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/finance/receivables/$receivableId" `
    -BearerToken $tokens.access_token -Client $client
$receivableAfterBody = $receivableAfter.Body | ConvertFrom-Json
Assert-True ($receivableAfterBody.data.status -eq 'PAID') 'allocated receivable becomes PAID'
Assert-True ([int64]$receivableAfterBody.data.outstandingAmount -eq 0) 'allocated receivable has no outstanding balance'

$creditAfter = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/customer/customers/9001/credit" `
    -BearerToken $tokens.access_token -Client $client
$creditAfterBody = $creditAfter.Body | ConvertFrom-Json
Assert-True ([int64]$creditAfterBody.data.uncollectedReceivable -eq $outstandingBefore) `
    'credit outstanding returns to the baseline after allocation'

# 超额核销必须被拒绝：同一张应收再核销 1 分会超过未收余额。
$overAllocation = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/receipts/$receiptId/allocations" `
    -JsonBody $allocationJson -BearerToken $tokens.access_token -Client $client
Assert-True ($overAllocation.Status -ge 400) 'over-allocation is rejected by the service'

Write-Host ''
Write-Host '== 9. Manual invoice and customer statement =='
$invoiceNo = 'SMOKE-INV-' + (Get-Date -Format 'yyyyMMddHHmmssfff')
$invoiceJson = (@{
    customerId = 9001
    contractId = $contractId
    invoiceNumber = $invoiceNo
    invoiceType = 'VAT_SPECIAL'
    invoiceTitle = '冒烟测试客户'
    taxNo = '91310000DEMO9001X'
    amount = 11300
    taxAmount = 1300
    taxRate = '13.0000'
    invoiceDate = (Get-Date).ToString('yyyy-MM-dd')
    remark = 'smoke test invoice'
} | ConvertTo-Json -Compress)
$invoiceResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/invoices" `
    -JsonBody $invoiceJson -BearerToken $tokens.access_token -Client $client
Assert-True ($invoiceResponse.Status -eq 200) 'finance can register a manual invoice'
$invoiceBody = $invoiceResponse.Body | ConvertFrom-Json
$invoiceId = $invoiceBody.data.id
Assert-True ([bool]$invoiceId) 'created invoice exposes an id'
Assert-True ($invoiceBody.data.invoiceNumber -eq $invoiceNo) 'invoice number is persisted'
Assert-True ($invoiceBody.data.status -eq 'REGISTERED') 'new invoice starts as REGISTERED'
Assert-True ([int64]$invoiceBody.data.netAmount -eq 10000) 'invoice net amount equals amount minus tax'

$dupInvoiceResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/invoices" `
    -JsonBody $invoiceJson -BearerToken $tokens.access_token -Client $client
Assert-True ($dupInvoiceResponse.Status -ge 400) 'duplicate invoice number is rejected'

$overCapJson = (@{
    customerId = 9001
    contractId = $contractId
    invoiceNumber = 'SMOKE-OVER-' + (Get-Date -Format 'yyyyMMddHHmmssfff')
    invoiceType = 'VAT_NORMAL'
    invoiceTitle = '冒烟测试客户'
    amount = 200000000
    taxAmount = 0
    taxRate = '0'
    invoiceDate = (Get-Date).ToString('yyyy-MM-dd')
    remark = 'smoke test invoice over cap'
} | ConvertTo-Json -Compress)
$overCapResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/invoices" `
    -JsonBody $overCapJson -BearerToken $tokens.access_token -Client $client
Assert-True ($overCapResponse.Status -ge 400) 'invoice over the contract quota is rejected'

$sendInvoice = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/invoices/$invoiceId/send" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($sendInvoice.Status -eq 200) 'invoice can be marked as sent'
$sendInvoiceBody = $sendInvoice.Body | ConvertFrom-Json
Assert-True ($sendInvoiceBody.data.status -eq 'SENT') 'sent invoice becomes SENT'

$signInvoice = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/invoices/$invoiceId/sign" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($signInvoice.Status -eq 200) 'invoice can be marked as signed'
$signInvoiceBody = $signInvoice.Body | ConvertFrom-Json
Assert-True ($signInvoiceBody.data.status -eq 'SIGNED') 'signed invoice becomes SIGNED'

$repeatSend = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/invoices/$invoiceId/send" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($repeatSend.Status -ge 400) 'invoice status cannot move backwards'

# 对账单：期间取最近 365 天，期末余额应等于客户当前未收应收。
$statementStart = (Get-Date).AddDays(-365).ToString('yyyy-MM-dd')
$statementEnd = (Get-Date).ToString('yyyy-MM-dd')
$statementJson = (@{
    customerId = 9001
    periodStart = $statementStart
    periodEnd = $statementEnd
    remark = 'smoke test statement'
} | ConvertTo-Json -Compress)
$statementResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/statements" `
    -JsonBody $statementJson -BearerToken $tokens.access_token -Client $client
Assert-True ($statementResponse.Status -eq 200) 'finance can generate a customer statement'
$statementBody = $statementResponse.Body | ConvertFrom-Json
$statementId = $statementBody.data.id
Assert-True ([bool]$statementId) 'generated statement exposes an id'
Assert-True ($statementBody.data.status -eq 'GENERATED') 'statement starts as GENERATED'
Assert-True ([int64]$statementBody.data.closingAmount -eq $outstandingBefore) `
    'statement closing balance equals the customer outstanding receivable'
Assert-True (([int64]$statementBody.data.openingAmount + [int64]$statementBody.data.receivableAmount - [int64]$statementBody.data.receivedAmount) -eq [int64]$statementBody.data.closingAmount) `
    'statement balances: opening + receivable - received = closing'
Assert-True ($statementBody.data.lines.Count -ge 2) 'statement snapshot includes receivable and receipt lines'

$statementPage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/statements/page?customerId=9001&page=1&size=10" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($statementPage.Status -eq 200) 'statement page route works through gateway'
$statementPageBody = $statementPage.Body | ConvertFrom-Json
Assert-True ($statementPageBody.data.total -ge 1) 'statement page includes the generated statement'

$confirmJson = (@{ differenceNote = 'smoke test confirmed' } | ConvertTo-Json -Compress)
$confirmStatement = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/statements/$statementId/confirm" `
    -JsonBody $confirmJson -BearerToken $tokens.access_token -Client $client
Assert-True ($confirmStatement.Status -eq 200) 'statement can be confirmed by the customer'
$confirmStatementBody = $confirmStatement.Body | ConvertFrom-Json
Assert-True ($confirmStatementBody.data.status -eq 'CONFIRMED') 'confirmed statement becomes CONFIRMED'
Assert-True ([bool]$confirmStatementBody.data.confirmedAt) 'confirmed statement records the confirmation time'

$repeatConfirm = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/statements/$statementId/confirm" `
    -JsonBody $confirmJson -BearerToken $tokens.access_token -Client $client
Assert-True ($repeatConfirm.Status -ge 400) 'statement cannot be confirmed twice'

$futureStatement = (@{
    customerId = 9001
    periodStart = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
    periodEnd = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
    remark = 'smoke test statement future'
} | ConvertTo-Json -Compress)
$futureResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/statements" `
    -JsonBody $futureStatement -BearerToken $tokens.access_token -Client $client
Assert-True ($futureResponse.Status -ge 400) 'future statement period is rejected'

Write-Host ''
Write-Host '== 10. Device reporting: signature, overwrite, image once, staging and IoTDB =='
# 设备上报走 /api/iot/**，不需要 OAuth 令牌；设备用一机一密密钥做 HMAC-SHA256 验签。
$deviceNo = 'EQ-F001-01'
$deviceSecret = 'demo-secret-EQ-F001-01'

function New-DeviceSignature {
    param(
        [string]$Secret,
        [string]$DeviceNo,
        [string]$MsgType,
        [long]$Timestamp,
        [string]$Nonce,
        [string]$PayloadJson
    )
    $base = "$DeviceNo|$MsgType|$Timestamp|$Nonce|$PayloadJson"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    try {
        $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
        $hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($base))
    } finally {
        $hmac.Dispose()
    }
    return (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
}

function New-DeviceMessage {
    param(
        [string]$DeviceNo,
        [string]$MsgType,
        [string]$OrderNo,
        [long]$Timestamp,
        [long]$Seq,
        [string]$Secret,
        [string]$PayloadJson
    )
    $nonce = 'smoke' + ($Seq % 100000)
    $sign = New-DeviceSignature -Secret $Secret -DeviceNo $DeviceNo -MsgType $MsgType `
        -Timestamp $Timestamp -Nonce $nonce -PayloadJson $PayloadJson
    $orderField = ''
    if ($OrderNo) { $orderField = ',"orderNo":"' + $OrderNo + '"' }
    return '{"version":"v1","msgType":"' + $MsgType + '","deviceNo":"' + $DeviceNo + '"' + $orderField + ',"timestamp":' + $Timestamp + ',"seq":' + $Seq + ',"nonce":"' + $nonce + '","sign":"' + $sign + '","payload":' + $PayloadJson + '}'
}

$iotOrderJson = (@{
    customerId = 9001
    contractId = $contractId
    factoryId = $factoryId
    totalAmount = 990004
    remark = 'smoke test device report order'
} | ConvertTo-Json -Compress)
$iotOrderResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/order/orders" `
    -JsonBody $iotOrderJson -BearerToken $tokens.access_token -Client $client
Assert-True ($iotOrderResponse.Status -eq 200) 'an order can be created for the device report scenario'
$iotOrderNo = ($iotOrderResponse.Body | ConvertFrom-Json).data.orderNo

$iotBaseTs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() - 2000
# 每次运行使用不同的序号段，避免上一轮的去重缓存把本轮报文判成重放。
$iotSeqBase = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() % 100000000) * 10

# 1) 首次订单作业上报：订单号精确匹配，回执 0。
$payloadV1 = '{"status":"running","printedLengthMeter":12.500,"progressMeter":12.500,"progressPercent":25.50,"inkUsageMl":120.250,"inkDeltaMl":2.100,"remainingMinutes":30,"speedMeterPerHour":38.200,"widthMeter":1.600,"passCount":1,"deviceState":"printing","errorCode":"","imageUploaded":false,"workOrderNo":"WO-SMOKE"}'
$messageV1 = New-DeviceMessage -DeviceNo $deviceNo -MsgType 'order' -OrderNo $iotOrderNo `
    -Timestamp ($iotBaseTs + 1) -Seq ($iotSeqBase + 1) -Secret $deviceSecret -PayloadJson $payloadV1
$reportV1 = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $messageV1 -Client $client
Assert-True ($reportV1.Status -eq 200) 'device report endpoint is reachable without an OAuth token'
$reportV1Body = $reportV1.Body | ConvertFrom-Json
Assert-True ($reportV1Body.data.code -eq 0) 'a correctly signed order report is accepted'
Assert-True ($reportV1Body.data.matched -eq $true) 'the reported order number is matched exactly'

# 2) 同一订单再次上报：后到覆盖前到，仍只有一行。
$payloadV2 = '{"status":"running","printedLengthMeter":25.750,"progressMeter":25.750,"progressPercent":50.00,"inkUsageMl":135.500,"inkDeltaMl":15.250,"remainingMinutes":20,"speedMeterPerHour":40.000,"widthMeter":1.600,"passCount":2,"deviceState":"printing","errorCode":"","imageUploaded":false,"workOrderNo":"WO-SMOKE"}'
$messageV2 = New-DeviceMessage -DeviceNo $deviceNo -MsgType 'order' -OrderNo $iotOrderNo `
    -Timestamp ($iotBaseTs + 2) -Seq ($iotSeqBase + 2) -Secret $deviceSecret -PayloadJson $payloadV2
$reportV2 = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $messageV2 -Client $client
Assert-True (($reportV2.Body | ConvertFrom-Json).data.code -eq 0) 'a later report for the same order is accepted'

$reportPage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/device-reports/page?orderNo=$iotOrderNo&page=1&size=10" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($reportPage.Status -eq 200) 'device report page route works through gateway'
$reportPageBody = $reportPage.Body | ConvertFrom-Json
Assert-True ($reportPageBody.data.total -eq 1) 'one order keeps a single report row after repeated pushes'
$reportRow = $reportPageBody.data.records[0]
Assert-True ([decimal]$reportRow.printedLengthMeter -eq 25.75) 'the later push overwrites the earlier printed length'
Assert-True ([decimal]$reportRow.progressPercent -eq 50.0) 'progress percent keeps two decimals from the device payload'
Assert-True ($reportRow.deviceSeq -eq ($iotSeqBase + 2)) 'the report row records the latest device sequence'

# 3) 签名错误：拒绝并返回 2002。
$badMessage = $messageV2 -replace '"sign":"[0-9a-f]+"', '"sign":"0000000000000000000000000000000000000000000000000000000000000000"'
$oldSeqToken = '"seq":' + ($iotSeqBase + 2)
$newSeqToken = '"seq":' + ($iotSeqBase + 3)
$badMessage = $badMessage -replace $oldSeqToken, $newSeqToken
$badResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $badMessage -Client $client
Assert-True (($badResponse.Body | ConvertFrom-Json).data.code -eq 2002) 'a bad signature is rejected with code 2002'

# 4) 重复报文：同一 deviceNo + seq 在窗口内只处理一次。
$duplicateResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $messageV2 -Client $client
Assert-True (($duplicateResponse.Body | ConvertFrom-Json).data.code -eq 2003) 'a duplicated device sequence is rejected with code 2003'

# 5) 订单图片：一条订单仅一次，重复上传返回 4003。
$imagePayload = '{"fileName":"order.jpg","format":"jpg","sizeBytes":2048,"width":800,"height":600,"hash":"smoke-hash"}'
$imageMessage = New-DeviceMessage -DeviceNo $deviceNo -MsgType 'image' -OrderNo $iotOrderNo `
    -Timestamp ($iotBaseTs + 3) -Seq ($iotSeqBase + 4) -Secret $deviceSecret -PayloadJson $imagePayload
$imageResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $imageMessage -Client $client
$imageBody = $imageResponse.Body | ConvertFrom-Json
Assert-True ($imageBody.data.code -eq 0) 'the first order image is accepted'
Assert-True ($imageBody.data.imageId -eq "IMG$iotOrderNo") 'the image id is derived from the matched order number'

$repeatImageMessage = New-DeviceMessage -DeviceNo $deviceNo -MsgType 'image' -OrderNo $iotOrderNo `
    -Timestamp ($iotBaseTs + 4) -Seq ($iotSeqBase + 5) -Secret $deviceSecret -PayloadJson $imagePayload
$repeatImageResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $repeatImageMessage -Client $client
Assert-True (($repeatImageResponse.Body | ConvertFrom-Json).data.code -eq 4003) 'a second image for the same order is rejected with 4003'

# 6) 订单号未匹配：数据进暂存，回执 3001。
$unknownOrderNo = '999999999999999999'
$unknownPayload = '{"status":"running","printedLengthMeter":1.500,"progressPercent":1.00}'
$unknownMessage = New-DeviceMessage -DeviceNo $deviceNo -MsgType 'order' -OrderNo $unknownOrderNo `
    -Timestamp ($iotBaseTs + 5) -Seq ($iotSeqBase + 6) -Secret $deviceSecret -PayloadJson $unknownPayload
$unknownResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $unknownMessage -Client $client
Assert-True (($unknownResponse.Body | ConvertFrom-Json).data.code -eq 3001) 'an unknown order number is staged with code 3001'

$stagingPage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/device-reports/staging/page?page=1&size=10" `
    -BearerToken $tokens.access_token -Client $client
$stagingPageBody = $stagingPage.Body | ConvertFrom-Json
Assert-True ($stagingPageBody.data.total -ge 1) 'unmatched reports appear in the staging page'
$stagedRow = $stagingPageBody.data.records | Where-Object { $_.rawOrderNo -eq $unknownOrderNo } | Select-Object -First 1
Assert-True ($null -ne $stagedRow) 'the staged report keeps the raw order number'

# 7) 心跳写入 IoTDB，并可按测点查询。
$statusPayload = '{"online":true,"workState":"printing","temperature":42.5,"inkLevelPercent":88.5,"firmwareVersion":"1.0.0","ip":"192.168.3.50","rssi":-55}'
$statusMessage = New-DeviceMessage -DeviceNo $deviceNo -MsgType 'status' -OrderNo '' `
    -Timestamp ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) -Seq ($iotSeqBase + 7) -Secret $deviceSecret -PayloadJson $statusPayload
$statusResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/iot/report" `
    -JsonBody $statusMessage -Client $client
Assert-True (($statusResponse.Body | ConvertFrom-Json).data.code -eq 0) 'a status heartbeat is accepted'

$seriesResponse = $null
$seriesBody = $null
# IoTDB 写入成功后可能有一小段刷盘延迟，这里轮询最多 8 秒再断言。
for ($attempt = 1; $attempt -le 8; $attempt++) {
    $seriesResponse = Send-HttpRequest -Method 'GET' `
        -Url "$GatewayBase/api/order/device-reports/iotdb/${deviceNo}?measurement=temperature&limit=5" `
        -BearerToken $tokens.access_token -Client $client
    if ($seriesResponse.Status -eq 200) {
        $seriesBody = $seriesResponse.Body | ConvertFrom-Json
        if ((@($seriesBody.data)).Count -ge 1) { break }
    }
    Start-Sleep -Seconds 1
}
Assert-True ($seriesResponse.Status -eq 200) 'the IoTDB time series route works through gateway'
Assert-True ((@($seriesBody.data)).Count -ge 1) 'the status heartbeat is queryable from IoTDB'

# 8) CoAP：同一信封通过 UDP PUT 到 /device/{deviceNo}/order，验证 ACK 回执。
$coapPayload = '{"status":"running","printedLengthMeter":55.500,"progressPercent":88.00}'
$coapSeq = $iotSeqBase + 8
$coapTs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$coapNonce = 'smoke' + ($coapSeq % 100000)
$coapSign = New-DeviceSignature -Secret $deviceSecret -DeviceNo $deviceNo -MsgType 'order' `
    -Timestamp $coapTs -Nonce $coapNonce -PayloadJson $coapPayload
$coapJson = '{"version":"v1","msgType":"order","deviceNo":"' + $deviceNo + '","orderNo":"' + $iotOrderNo +
    '","timestamp":' + $coapTs + ',"seq":' + $coapSeq + ',"nonce":"' + $coapNonce +
    '","sign":"' + $coapSign + '","payload":' + $coapPayload + '}'

function Add-CoapOption {
    param([System.IO.MemoryStream]$Stream, [byte]$Head, [string]$Value)
    $Stream.WriteByte($Head)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
    $Stream.Write($bytes, 0, $bytes.Length)
}

$coapStream = New-Object System.IO.MemoryStream
foreach ($head in @(0x40, 0x02, 0x55, 0x66)) { $coapStream.WriteByte($head) }
Add-CoapOption $coapStream 0xB6 'device'
Add-CoapOption $coapStream 0x0A $deviceNo
Add-CoapOption $coapStream 0x05 'order'
$coapStream.WriteByte(0xFF)
$coapBytes = [System.Text.Encoding]::UTF8.GetBytes($coapJson)
$coapStream.Write($coapBytes, 0, $coapBytes.Length)
$coapPacket = $coapStream.ToArray()

$coapAck = $null
$coapUdp = New-Object System.Net.Sockets.UdpClient
try {
    $coapUdp.Client.ReceiveTimeout = 5000
    [void]$coapUdp.Send($coapPacket, $coapPacket.Length,
        (New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Loopback, 5683)))
    $coapRemote = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Any, 0)
    $coapResponse = $coapUdp.Receive([ref]$coapRemote)
    $coapMarker = 0
    while ($coapMarker -lt $coapResponse.Length -and $coapResponse[$coapMarker] -ne 0xFF) { $coapMarker++ }
    $coapAck = [System.Text.Encoding]::UTF8.GetString(
        $coapResponse, $coapMarker + 1, $coapResponse.Length - $coapMarker - 1) | ConvertFrom-Json
} finally {
    $coapUdp.Close()
}
Assert-True ($null -ne $coapAck) 'CoAP PUT over UDP returns an acknowledgement'
Assert-True ($coapAck.code -eq 0) 'CoAP order report is accepted'
Assert-True ($coapAck.matched -eq $true) 'CoAP order report matches the order number'

# 9) 接入审计、配置与权限。
$logPage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/device-reports/logs/page?deviceNo=$deviceNo&page=1&size=20" `
    -BearerToken $tokens.access_token -Client $client
$logPageBody = $logPage.Body | ConvertFrom-Json
Assert-True ($logPageBody.data.total -ge 6) 'every device message leaves an access log row'
Assert-True ((@($logPageBody.data.records | Where-Object { $_.resultCode -eq 2002 })).Count -ge 1) 'the access log records the rejected signature'

$configResponse = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/order/device-reports/config" `
    -BearerToken $tokens.access_token -Client $client
$configBody = $configResponse.Body | ConvertFrom-Json
Assert-True ($configBody.data.enabled -eq $true) 'device reporting is enabled by default'
Assert-True ($configBody.data.frequency.orderSeconds -eq 10) 'the default order report interval is 10 seconds'
Assert-True ($configBody.data.mqtt.orderTopic -eq 'device/{deviceNo}/order') 'the default order topic template is exposed'

$salesTokens = Get-UserTokenViaPkce -AuthBase $AuthBase -Username 'sales01' -Password 'Sales@123'
Assert-True ([bool]$salesTokens.access_token) 'sales user can sign in with authorization code + PKCE'
$salesReportPage = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/order/device-reports/page?page=1&size=5" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($salesReportPage.Status -eq 403) 'sales cannot read device reports without production:report:read'

Write-Host ''
Write-Host '== 11. Finance documents: expense approval/payment, loan disbursement/repayment and offset =='

# 财务单据区分“审批”和“执行”：审批通过不代表付款/放款，财务需再登记执行动作。
# 报销与借款都是员工本人单据，因此用 PKCE 登录销售、厂长、财务三个真实账号验证权限与数据范围。
$managerTokens = Get-UserTokenViaPkce -AuthBase $AuthBase -Username 'manager01' -Password 'Manager@123'
$financeTokens = Get-UserTokenViaPkce -AuthBase $AuthBase -Username 'finance01' -Password 'Finance@123'
Assert-True ([bool]$managerTokens.access_token) 'factory manager signs in with authorization code + PKCE'
Assert-True ([bool]$financeTokens.access_token) 'finance user signs in with authorization code + PKCE'

$financeToday = (Get-Date).ToString('yyyy-MM-dd')

# 从厂长的待办中按业务主键定位审批任务，避免依赖待办分页顺序。
function Find-ApprovalTodo {
    param(
        [string]$BusinessType,
        [string]$BusinessId,
        [string]$BearerToken
    )
    $todoResponse = Send-HttpRequest -Method 'GET' `
        -Url "$GatewayBase/api/approval/todos/page?businessType=$BusinessType&page=1&size=50" `
        -BearerToken $BearerToken -Client $client
    if ($todoResponse.Status -ne 200) { return $null }
    $todoPage = $todoResponse.Body | ConvertFrom-Json
    $matched = @($todoPage.data.records | Where-Object { [string]$_.businessId -eq [string]$BusinessId })
    if ($matched.Count -eq 0) { return $null }
    return $matched[0]
}

# 1) 500 元差旅报销：≤1000 元由厂长审批，销售无付款权限。
$smallExpenseJson = @{
    expenseType = 'TRAVEL'
    amount = '50000'
    expenseDate = $financeToday
    department = 'Sales'
    description = 'smoke test expense small'
    attachmentUrl = $null
} | ConvertTo-Json -Compress
$smallExpenseResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/expenses" `
    -JsonBody $smallExpenseJson -BearerToken $salesTokens.access_token -Client $client
Assert-True ($smallExpenseResponse.Status -eq 200) 'sales can create an expense claim draft'
$smallExpense = ($smallExpenseResponse.Body | ConvertFrom-Json).data
$smallExpenseId = [string]$smallExpense.id
Assert-True ($smallExpense.status -eq 'DRAFT') 'a new expense claim starts as DRAFT'
Assert-True ([string]$smallExpense.amount -eq '50000') 'expense amount is stored in cents'
Assert-True ($smallExpense.claimNo.StartsWith('BX-')) 'expense claim number uses the BX- prefix'

$salesPayAttempt = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$smallExpenseId/pay" `
    -JsonBody '{"paymentMethod":"TRANSFER","offsetLoanAmount":"0"}' `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($salesPayAttempt.Status -eq 403) 'sales cannot pay an expense claim without finance:expense:pay'

$smallSubmitResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$smallExpenseId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($smallSubmitResponse.Status -eq 200) 'sales can submit an expense claim for approval'
$smallSubmitted = ($smallSubmitResponse.Body | ConvertFrom-Json).data
Assert-True ($smallSubmitted.status -eq 'PENDING') 'a submitted expense claim becomes PENDING'
Assert-True ([bool]$smallSubmitted.approvalInstanceId) 'submitting an expense creates an approval instance'

$managerSmallTodo = Find-ApprovalTodo -BusinessType 'EXPENSE' -BusinessId $smallExpenseId `
    -BearerToken $managerTokens.access_token
Assert-True ($null -ne $managerSmallTodo) 'expense within 1000 CNY appears in the factory manager inbox'
Assert-True ($managerSmallTodo.tasks[0].approverRole -eq 'FACTORY_MANAGER') `
    'expense within 1000 CNY is routed to FACTORY_MANAGER'

$managerApproveSmall = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($managerSmallTodo.tasks[0].id)/approve" `
    -JsonBody '{"comment":"smoke test manager approve"}' `
    -BearerToken $managerTokens.access_token -Client $client
Assert-True ($managerApproveSmall.Status -eq 200) 'factory manager can approve a small expense'

$smallAfterApproveResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/expenses/$smallExpenseId" `
    -BearerToken $salesTokens.access_token -Client $client
$smallAfterApprove = ($smallAfterApproveResponse.Body | ConvertFrom-Json).data
Assert-True ($smallAfterApprove.status -eq 'APPROVED') 'approval moves the expense claim to APPROVED (awaiting payment)'
Assert-True ($smallAfterApprove.approvalNote -eq 'smoke test manager approve') `
    'the approval comment is copied to the expense claim'

$smallPayResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$smallExpenseId/pay" `
    -JsonBody '{"paymentMethod":"TRANSFER","offsetLoanAmount":"0","remark":"smoke test pay small"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($smallPayResponse.Status -eq 200) 'finance can pay an approved expense claim'
$smallPaid = ($smallPayResponse.Body | ConvertFrom-Json).data
Assert-True ($smallPaid.status -eq 'PAID') 'a paid expense claim becomes PAID'
Assert-True ([string]$smallPaid.paidAmount -eq '50000') 'the paid amount equals the claim amount without offset'

$smallPayAgain = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$smallExpenseId/pay" `
    -JsonBody '{"paymentMethod":"TRANSFER","offsetLoanAmount":"0"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($smallPayAgain.Status -eq 409) 'a paid expense claim cannot be paid twice'

# 2) 1200 元报销：>1000 元由财务审批，验证金额档位与超冲销校验。
$largeExpenseJson = @{
    expenseType = 'ENTERTAINMENT'
    amount = '120000'
    expenseDate = $financeToday
    department = 'Sales'
    description = 'smoke test expense large'
} | ConvertTo-Json -Compress
$largeExpenseResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/expenses" `
    -JsonBody $largeExpenseJson -BearerToken $salesTokens.access_token -Client $client
Assert-True ($largeExpenseResponse.Status -eq 200) 'sales can create a large expense claim'
$largeExpenseId = [string](($largeExpenseResponse.Body | ConvertFrom-Json).data.id)

$largeSubmitResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$largeExpenseId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($largeSubmitResponse.Status -eq 200) 'a large expense claim can be submitted'
$largeSubmitted = ($largeSubmitResponse.Body | ConvertFrom-Json).data
Assert-True ($largeSubmitted.status -eq 'PENDING') 'the large expense claim is pending approval'

$managerLargeTodo = Find-ApprovalTodo -BusinessType 'EXPENSE' -BusinessId $largeExpenseId `
    -BearerToken $managerTokens.access_token
Assert-True ($null -eq $managerLargeTodo) 'expense over 1000 CNY is not routed to the factory manager'

$financeLargeTodo = Find-ApprovalTodo -BusinessType 'EXPENSE' -BusinessId $largeExpenseId `
    -BearerToken $financeTokens.access_token
Assert-True ($null -ne $financeLargeTodo) 'expense over 1000 CNY appears in the finance inbox'
Assert-True ($financeLargeTodo.tasks[0].approverRole -eq 'FINANCE') `
    'expense over 1000 CNY is routed to FINANCE'

$financeApproveLarge = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($financeLargeTodo.tasks[0].id)/approve" `
    -JsonBody '{"comment":"smoke test finance approve"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($financeApproveLarge.Status -eq 200) 'finance can approve a large expense'

$overOffsetAttempt = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$largeExpenseId/pay" `
    -JsonBody '{"paymentMethod":"TRANSFER","offsetLoanAmount":"200000"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($overOffsetAttempt.Status -eq 400) 'an offset larger than the claim amount is rejected'

$largePayResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$largeExpenseId/pay" `
    -JsonBody '{"paymentMethod":"TRANSFER","offsetLoanAmount":"0","remark":"smoke test pay large"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($largePayResponse.Status -eq 200) 'a large expense claim can be paid after finance approval'

# 3) 驳回、再提交与撤销：审批驳回不删除单据，员工可修改后再次提交。
$rejectExpenseJson = @{
    expenseType = 'OFFICE'
    amount = '30000'
    expenseDate = $financeToday
    description = 'smoke test expense reject'
} | ConvertTo-Json -Compress
$rejectExpenseResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/expenses" `
    -JsonBody $rejectExpenseJson -BearerToken $salesTokens.access_token -Client $client
Assert-True ($rejectExpenseResponse.Status -eq 200) 'a claim for the rejection path is created'
$rejectExpenseId = [string](($rejectExpenseResponse.Body | ConvertFrom-Json).data.id)

$rejectSubmitResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$rejectExpenseId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($rejectSubmitResponse.Status -eq 200) 'the rejection-path claim is submitted'

$rejectTodo = Find-ApprovalTodo -BusinessType 'EXPENSE' -BusinessId $rejectExpenseId `
    -BearerToken $managerTokens.access_token
Assert-True ($null -ne $rejectTodo) 'the rejection-path claim reaches the manager inbox'

$rejectResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($rejectTodo.tasks[0].id)/reject" `
    -JsonBody '{"comment":"smoke test reject reason"}' `
    -BearerToken $managerTokens.access_token -Client $client
Assert-True ($rejectResponse.Status -eq 200) 'the manager can reject an expense claim'

$rejectedExpenseResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/expenses/$rejectExpenseId" `
    -BearerToken $salesTokens.access_token -Client $client
$rejectedExpense = ($rejectedExpenseResponse.Body | ConvertFrom-Json).data
Assert-True ($rejectedExpense.status -eq 'REJECTED') 'a rejected expense claim is kept as REJECTED'
Assert-True ($rejectedExpense.approvalNote -eq 'smoke test reject reason') `
    'the rejection comment is copied to the expense claim'

$resubmitResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$rejectExpenseId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($resubmitResponse.Status -eq 200) 'a rejected expense claim can be resubmitted'
Assert-True ((($resubmitResponse.Body | ConvertFrom-Json).data.status) -eq 'PENDING') `
    'a resubmitted expense claim returns to PENDING'

$cancelPendingResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$rejectExpenseId/cancel" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($cancelPendingResponse.Status -eq 200) 'the applicant can cancel a pending expense claim'
Assert-True ((($cancelPendingResponse.Body | ConvertFrom-Json).data.status) -eq 'CANCELLED') `
    'cancelling a pending claim synchronises the workflow to CANCELLED'

$draftExpenseJson = @{
    expenseType = 'OTHER'
    amount = '10000'
    expenseDate = $financeToday
    description = 'smoke test expense draft cancel'
} | ConvertTo-Json -Compress
$draftExpenseResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/expenses" `
    -JsonBody $draftExpenseJson -BearerToken $salesTokens.access_token -Client $client
$draftExpenseId = [string](($draftExpenseResponse.Body | ConvertFrom-Json).data.id)
$draftCancelResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$draftExpenseId/cancel" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($draftCancelResponse.Status -eq 200) 'a draft expense claim can be cancelled'
Assert-True ((($draftCancelResponse.Body | ConvertFrom-Json).data.status) -eq 'CANCELLED') `
    'a cancelled draft expense claim becomes CANCELLED'

# 4) 3000 元借款：≤5000 元厂长审批，审批通过后必须由财务放款。
$loanJson = @{
    amount = '300000'
    loanDate = $financeToday
    purpose = 'smoke test loan offset'
    expectedRepayDate = $financeToday
    department = 'Sales'
} | ConvertTo-Json -Compress
$loanResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/loans" `
    -JsonBody $loanJson -BearerToken $salesTokens.access_token -Client $client
Assert-True ($loanResponse.Status -eq 200) 'sales can create a loan draft'
$loan = ($loanResponse.Body | ConvertFrom-Json).data
$loanId = [string]$loan.id
Assert-True ($loan.loanNo.StartsWith('JK-')) 'loan number uses the JK- prefix'

$loanSubmitResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($loanSubmitResponse.Status -eq 200) 'sales can submit a loan for approval'
Assert-True ((($loanSubmitResponse.Body | ConvertFrom-Json).data.status) -eq 'PENDING') `
    'a submitted loan becomes PENDING'
Assert-True ([bool](($loanSubmitResponse.Body | ConvertFrom-Json).data.approvalInstanceId)) `
    'submitting a loan creates an approval instance'

$managerLoanTodo = Find-ApprovalTodo -BusinessType 'LOAN' -BusinessId $loanId `
    -BearerToken $managerTokens.access_token
Assert-True ($null -ne $managerLoanTodo) 'a loan within 5000 CNY appears in the factory manager inbox'
Assert-True ($managerLoanTodo.tasks[0].approverRole -eq 'FACTORY_MANAGER') `
    'a loan within 5000 CNY is routed to FACTORY_MANAGER'

$managerApproveLoan = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($managerLoanTodo.tasks[0].id)/approve" `
    -JsonBody '{"comment":"smoke test manager approve loan"}' `
    -BearerToken $managerTokens.access_token -Client $client
Assert-True ($managerApproveLoan.Status -eq 200) 'the factory manager can approve a loan'
Assert-True ((($managerApproveLoan.Body | ConvertFrom-Json).data.status) -eq 'APPROVED') `
    'an approved loan waits for disbursement'

$salesDisburseAttempt = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/disburse" `
    -JsonBody '{"method":"TRANSFER"}' -BearerToken $salesTokens.access_token -Client $client
Assert-True ($salesDisburseAttempt.Status -eq 403) 'sales cannot disburse a loan without finance:loan:disburse'

$disburseResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/disburse" `
    -JsonBody '{"method":"TRANSFER"}' -BearerToken $financeTokens.access_token -Client $client
Assert-True ($disburseResponse.Status -eq 200) 'finance can disburse an approved loan'
$disbursedLoan = ($disburseResponse.Body | ConvertFrom-Json).data
Assert-True ($disbursedLoan.status -eq 'DISBURSED') 'a disbursed loan enters DISBURSED (repaying)'
Assert-True ([string]$disbursedLoan.outstandingAmount -eq '300000') `
    'a newly disbursed loan owes the full amount'

$salesRepayAttempt = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/repay" `
    -JsonBody '{"amount":"10000","method":"TRANSFER"}' `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($salesRepayAttempt.Status -eq 403) 'sales cannot register a loan repayment without finance:loan:repay'

# 5) 6000 元借款：>5000 元由财务审批。
$largeLoanJson = @{
    amount = '600000'
    loanDate = $financeToday
    purpose = 'smoke test loan large'
    expectedRepayDate = $financeToday
} | ConvertTo-Json -Compress
$largeLoanResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/loans" `
    -JsonBody $largeLoanJson -BearerToken $salesTokens.access_token -Client $client
Assert-True ($largeLoanResponse.Status -eq 200) 'sales can create a large loan draft'
$largeLoanId = [string](($largeLoanResponse.Body | ConvertFrom-Json).data.id)

$largeLoanSubmit = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$largeLoanId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($largeLoanSubmit.Status -eq 200) 'a large loan can be submitted'
Assert-True ((($largeLoanSubmit.Body | ConvertFrom-Json).data.status) -eq 'PENDING') `
    'the large loan is pending approval'

$managerLargeLoanTodo = Find-ApprovalTodo -BusinessType 'LOAN' -BusinessId $largeLoanId `
    -BearerToken $managerTokens.access_token
Assert-True ($null -eq $managerLargeLoanTodo) 'a loan over 5000 CNY is not routed to the factory manager'
$financeLargeLoanTodo = Find-ApprovalTodo -BusinessType 'LOAN' -BusinessId $largeLoanId `
    -BearerToken $financeTokens.access_token
Assert-True ($null -ne $financeLargeLoanTodo) 'a loan over 5000 CNY appears in the finance inbox'
Assert-True ($financeLargeLoanTodo.tasks[0].approverRole -eq 'FINANCE') `
    'a loan over 5000 CNY is routed to FINANCE'

$financeApproveLargeLoan = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($financeLargeLoanTodo.tasks[0].id)/approve" `
    -JsonBody '{"comment":"smoke test finance approve loan"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($financeApproveLargeLoan.Status -eq 200) 'finance can approve a large loan'
Assert-True ((($financeApproveLargeLoan.Body | ConvertFrom-Json).data.status) -eq 'APPROVED') `
    'the large loan waits for finance disbursement'

# 6) 报销付款冲销借款：800 元报销冲 500 元借款，现金实付 300 元。
$offsetExpenseJson = @{
    expenseType = 'LOGISTICS'
    amount = '80000'
    expenseDate = $financeToday
    description = 'smoke test expense offset'
} | ConvertTo-Json -Compress
$offsetExpenseResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/expenses" `
    -JsonBody $offsetExpenseJson -BearerToken $salesTokens.access_token -Client $client
Assert-True ($offsetExpenseResponse.Status -eq 200) 'an expense claim for loan offset is created'
$offsetExpense = ($offsetExpenseResponse.Body | ConvertFrom-Json).data
$offsetExpenseId = [string]$offsetExpense.id

$offsetSubmitResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$offsetExpenseId/submit" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($offsetSubmitResponse.Status -eq 200) 'the offset expense claim is submitted'
$offsetTodo = Find-ApprovalTodo -BusinessType 'EXPENSE' -BusinessId $offsetExpenseId `
    -BearerToken $managerTokens.access_token
Assert-True ($null -ne $offsetTodo) 'the offset expense claim reaches the manager inbox'
$offsetApprove = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/approval/tasks/$($offsetTodo.tasks[0].id)/approve" `
    -JsonBody '{"comment":"smoke test approve offset"}' `
    -BearerToken $managerTokens.access_token -Client $client
Assert-True ($offsetApprove.Status -eq 200) 'the offset expense claim is approved'

$outstandingResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/expenses/$offsetExpenseId/outstanding-loans" `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($outstandingResponse.Status -eq 200) 'finance can query the claimant outstanding loans'
$outstandingLoans = @((($outstandingResponse.Body | ConvertFrom-Json).data))
$offsetLoan = $outstandingLoans | Where-Object { [string]$_.id -eq $loanId } | Select-Object -First 1
Assert-True ($null -ne $offsetLoan) 'the disbursed loan is available for offset'
Assert-True ([string]$offsetLoan.outstandingAmount -eq '300000') 'the offset loan exposes its outstanding amount'

$offsetPayJson = @{
    paymentMethod = 'TRANSFER'
    offsetLoanId = $loanId
    offsetLoanAmount = '50000'
    remark = 'smoke test expense offset payment'
} | ConvertTo-Json -Compress
$offsetPayResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/expenses/$offsetExpenseId/pay" `
    -JsonBody $offsetPayJson -BearerToken $financeTokens.access_token -Client $client
Assert-True ($offsetPayResponse.Status -eq 200) 'finance can pay an expense with a loan offset'
$offsetPaid = ($offsetPayResponse.Body | ConvertFrom-Json).data
Assert-True ($offsetPaid.status -eq 'PAID') 'the offset expense claim becomes PAID'
Assert-True ([string]$offsetPaid.paidAmount -eq '30000') 'cash paid is the claim amount minus the offset'
Assert-True ([string]$offsetPaid.offsetLoanAmount -eq '50000') 'the offset amount is stored on the expense claim'

$loanAfterOffsetResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/loans/$loanId" `
    -BearerToken $salesTokens.access_token -Client $client
$loanAfterOffset = ($loanAfterOffsetResponse.Body | ConvertFrom-Json).data
Assert-True ([string]$loanAfterOffset.repaidAmount -eq '50000') 'the loan repaid amount includes the offset'
Assert-True ([string]$loanAfterOffset.outstandingAmount -eq '250000') `
    'the loan outstanding amount is reduced by the offset'
$offsetRecord = @($loanAfterOffset.repayments) |
    Where-Object { $_.method -eq 'OFFSET' } | Select-Object -First 1
Assert-True ($null -ne $offsetRecord) 'the offset creates a repayment record'
Assert-True ($offsetRecord.expenseClaimNo -eq $offsetExpense.claimNo) `
    'the offset repayment record links back to the expense claim'

# 7) 手工还款：部分还款、超还校验、全额结清。
$partialRepayResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/repay" `
    -JsonBody ('{"amount":"100000","repaymentDate":"' + $financeToday + '","method":"TRANSFER","remark":"smoke test repay partial"}') `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($partialRepayResponse.Status -eq 200) 'finance can register a partial repayment'
$partialLoan = ($partialRepayResponse.Body | ConvertFrom-Json).data
Assert-True ([string]$partialLoan.outstandingAmount -eq '150000') `
    'a partial repayment reduces the outstanding amount'

$overRepayResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/repay" `
    -JsonBody '{"amount":"200000","method":"TRANSFER"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($overRepayResponse.Status -eq 400) 'a repayment larger than the outstanding amount is rejected'

$fullRepayResponse = Send-HttpRequest -Method 'POST' `
    -Url "$GatewayBase/api/finance/loans/$loanId/repay" `
    -JsonBody '{"amount":"150000","method":"CASH","remark":"smoke test repay final"}' `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($fullRepayResponse.Status -eq 200) 'finance can register the final repayment'
$repaidLoan = ($fullRepayResponse.Body | ConvertFrom-Json).data
Assert-True ($repaidLoan.status -eq 'REPAID') 'a fully repaid loan becomes REPAID'
Assert-True ([string]$repaidLoan.outstandingAmount -eq '0') 'a fully repaid loan has no outstanding amount'
Assert-True ([string]$repaidLoan.repaidAmount -eq '300000') 'the repaid amount equals the loan amount'
Assert-True (@($repaidLoan.repayments).Count -eq 3) 'the loan keeps all repayment records (offset + two manual)'

# 8) 数据范围：财务自己的草稿，销售不可见。
$financePrivateJson = @{
    amount = '10000'
    loanDate = $financeToday
    purpose = 'smoke test loan private finance'
} | ConvertTo-Json -Compress
$financePrivateResponse = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/finance/loans" `
    -JsonBody $financePrivateJson -BearerToken $financeTokens.access_token -Client $client
Assert-True ($financePrivateResponse.Status -eq 200) 'finance can create a private loan draft'
$financePrivateId = [string](($financePrivateResponse.Body | ConvertFrom-Json).data.id)
$salesReadPrivate = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/loans/$financePrivateId" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($salesReadPrivate.Status -eq 403) 'sales cannot read another user loan'
$financeReadPrivate = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/loans/$financePrivateId" `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($financeReadPrivate.Status -eq 200) 'finance can read its own loan draft'

# 9) 分页与汇总：财务可见全部单据。
$expensePageResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/expenses/page?page=1&size=50" `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($expensePageResponse.Status -eq 200) 'finance can page all expense claims'
$expensePage = ($expensePageResponse.Body | ConvertFrom-Json).data
Assert-True ($expensePage.total -ge 4) 'the expense page includes every smoke claim'
Assert-True ($null -ne $expensePage.summary) 'the expense page exposes a summary block'
Assert-True ([string]$expensePage.summary.paidAmount -ne '') 'the expense summary exposes the paid amount'

$loanPageResponse = Send-HttpRequest -Method 'GET' `
    -Url "$GatewayBase/api/finance/loans/page?page=1&size=50" `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($loanPageResponse.Status -eq 200) 'finance can page all loans'
$loanPage = ($loanPageResponse.Body | ConvertFrom-Json).data
Assert-True ($loanPage.total -ge 3) 'the loan page includes every smoke loan'
Assert-True ($null -ne $loanPage.summary) 'the loan page exposes a summary block'

Write-Host ''
Write-Host '== 12. BI dashboard: scope, metric catalogue and ETL trigger =='
# 1) 管理员看板：卡片、趋势、工厂对比与待数据源指标。
$biDashboard = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/bi/dashboard" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($biDashboard.Status -eq 200) 'admin can read the BI dashboard'
$biData = ($biDashboard.Body | ConvertFrom-Json).data
Assert-True ($null -ne $biData) 'the BI dashboard returns a payload'
Assert-True (@($biData.cards).Count -gt 0) "the BI dashboard returns metric cards ($(@($biData.cards).Count))"
Assert-True ($biData.meta.scopeMode -eq 'ORG') 'admin sees the whole organisation scope'
Assert-True (@($biData.orderTrend.months).Count -eq 12) 'the BI order trend covers 12 months'
Assert-True ($null -ne $biData.factoryComparison) 'the BI dashboard exposes the factory comparison'
Assert-True (@($biData.pendingMetrics).Count -gt 0) 'the BI dashboard lists the metrics waiting for a data source'
$biCardCodes = @($biData.cards | ForEach-Object { $_.code })
Assert-True ($biCardCodes -notcontains 'order_gross_margin') 'a pending metric is never rendered as a card'
Assert-True ($biData.meta.dataUpdatedAt -ne '尚未完成首次同步') 'the BI dashboard reports the last successful sync'

# 2) 指标口径目录：READY 与 PENDING_SOURCE 都要有定义。
$biMetrics = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/bi/metrics" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($biMetrics.Status -eq 200) 'admin can read the BI metric catalogue'
$biMetricList = @(($biMetrics.Body | ConvertFrom-Json).data)
Assert-True ($biMetricList.Count -ge 30) "the BI metric catalogue covers the V1.4 KPI list ($($biMetricList.Count))"
Assert-True (@($biMetricList | Where-Object { $_.status -eq 'READY' }).Count -gt 0) 'the metric catalogue lists computable metrics'
Assert-True (@($biMetricList | Where-Object { $_.status -eq 'PENDING_SOURCE' }).Count -gt 0) 'the metric catalogue lists the metrics waiting for a data source'

# 3) 数据范围：管理员/财务看全公司，厂长只看本厂，销售只看本人。
$biManager = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/bi/dashboard" `
    -BearerToken $managerTokens.access_token -Client $client
Assert-True ($biManager.Status -eq 200) 'the factory manager can read the BI dashboard'
$biManagerData = ($biManager.Body | ConvertFrom-Json).data
Assert-True ($biManagerData.meta.scopeMode -eq 'FACTORY') 'the factory manager is limited to their own factory'
Assert-True (@($biManagerData.factoryComparison).Count -le 1) 'the factory manager only sees their own factory in the comparison'

$biFinance = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/bi/dashboard" `
    -BearerToken $financeTokens.access_token -Client $client
Assert-True ($biFinance.Status -eq 200) 'finance can read the BI dashboard'
$biFinanceData = ($biFinance.Body | ConvertFrom-Json).data
Assert-True ($biFinanceData.meta.scopeMode -eq 'ORG') 'finance sees the whole organisation scope'
Assert-True (@($biFinanceData.cards | ForEach-Object { $_.code }) -contains 'receivable_balance') 'finance receives the receivable card'

$biSales = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/bi/dashboard" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($biSales.Status -eq 200) 'sales can read the BI dashboard'
$biSalesData = ($biSales.Body | ConvertFrom-Json).data
Assert-True ($biSalesData.meta.scopeMode -eq 'SELF') 'sales only sees their own data'
$biSalesCodes = @($biSalesData.cards | ForEach-Object { $_.code })
Assert-True ($biSalesCodes -notcontains 'month_receipt_amount') 'sales does not receive the receipt metric without the permission'
Assert-True ($biSalesCodes -contains 'month_order_amount') 'sales still receives the order metrics they may read'

# 4) ETL 手动触发：只有 ADMIN 有 bi:etl:run。
$biRefresh = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/bi/admin/refresh" `
    -BearerToken $tokens.access_token -Client $client
Assert-True ($biRefresh.Status -eq 200) 'admin can trigger the BI refresh'
$biRefreshData = ($biRefresh.Body | ConvertFrom-Json).data
Assert-True ($biRefreshData.executed -eq $true) 'the BI refresh reports that it ran'
Assert-True ($biRefreshData.lastStatus -eq 'SUCCESS') 'the BI refresh reports a successful ETL'

$biSalesRefresh = Send-HttpRequest -Method 'POST' -Url "$GatewayBase/api/bi/admin/refresh" `
    -BearerToken $salesTokens.access_token -Client $client
Assert-True ($biSalesRefresh.Status -eq 403) 'sales cannot trigger the BI refresh'

$biAnonymous = Send-HttpRequest -Method 'GET' -Url "$GatewayBase/api/bi/dashboard" -Client $client
Assert-True ($biAnonymous.Status -eq 401) 'an anonymous BI dashboard request is rejected'

Write-Host ''
Write-Host 'Smoke test completed successfully.'
