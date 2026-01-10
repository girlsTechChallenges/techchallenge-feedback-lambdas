# Script para testar API com autenticação Cognito
# Uso: .\test-api-with-auth.ps1 -Action <insert|list> -Email <email> -Password <password>

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("insert", "list")]
    [string]$Action,
    
    [Parameter(Mandatory=$true)]
    [string]$Email,
    
    [Parameter(Mandatory=$true)]
    [string]$Password,
    
    [Parameter(Mandatory=$false)]
    [string]$StackName = "techchallenge-feedback"
)

# Obter token do Cognito
Write-Host "ℹ Autenticando usuário..." -ForegroundColor Cyan
$tokenResult = .\manage-users.ps1 -Action login -Email $Email -Password $Password -StackName $StackName

# Ler token do arquivo
$token = Get-Content ".\cognito-token.txt" -Raw

# Obter URL da API
Write-Host "ℹ Obtendo URL da API..." -ForegroundColor Cyan
$outputs = aws cloudformation describe-stacks --stack-name $StackName --query "Stacks[0].Outputs" --output json | ConvertFrom-Json

if ($Action -eq "insert") {
    $apiUrl = ($outputs | Where-Object { $_.OutputKey -eq "FeedbackApiUrl" }).OutputValue
    
    # Payload de teste
    $payload = @{
        customerId = "customer-$(Get-Random -Maximum 9999)"
        rating = 5
        comment = "Teste com autenticação Cognito - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        category = "PRODUTO"
    } | ConvertTo-Json
    
    Write-Host "`nℹ Enviando feedback para: $apiUrl" -ForegroundColor Cyan
    Write-Host "Payload:" -ForegroundColor Cyan
    Write-Host $payload -ForegroundColor White
    
    $response = curl.exe -X POST $apiUrl `
        -H "Content-Type: application/json" `
        -H "Authorization: Bearer $token" `
        -d $payload `
        --silent --show-error --write-out "`nHTTP Status: %{http_code}`n"
    
    Write-Host "`nResposta:" -ForegroundColor Yellow
    Write-Host $response
}
elseif ($Action -eq "list") {
    $apiUrl = ($outputs | Where-Object { $_.OutputKey -eq "ListFeedbacksApiUrl" }).OutputValue
    
    Write-Host "`nℹ Listando feedbacks de: $apiUrl" -ForegroundColor Cyan
    
    $response = curl.exe -X GET $apiUrl `
        -H "Authorization: Bearer $token" `
        --silent --show-error --write-out "`nHTTP Status: %{http_code}`n"
    
    Write-Host "`nResposta:" -ForegroundColor Yellow
    Write-Host $response | ConvertFrom-Json | ConvertTo-Json -Depth 10
}

Write-Host "`n✓ Teste concluído!" -ForegroundColor Green
