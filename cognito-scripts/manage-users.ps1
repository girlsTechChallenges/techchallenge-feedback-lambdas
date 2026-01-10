# Script para gerenciar usuários do Cognito User Pool
# Uso: .\manage-users.ps1 -Action <create|login|delete> -Email <email> -Password <password> -Name <nome>

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("create", "login", "delete", "list")]
    [string]$Action,
    
    [Parameter(Mandatory=$false)]
    [string]$Email,
    
    [Parameter(Mandatory=$false)]
    [string]$Password,
    
    [Parameter(Mandatory=$false)]
    [string]$Name,
    
    [Parameter(Mandatory=$false)]
    [string]$StackName = "techchallenge-feedback"
)

# Obter IDs do Cognito a partir dos outputs do CloudFormation
function Get-CognitoConfig {
    try {
        Write-Host "ℹ Obtendo configuração do Cognito..." -ForegroundColor Cyan
        
        $outputs = aws cloudformation describe-stacks --stack-name $StackName --query "Stacks[0].Outputs" --output json | ConvertFrom-Json
        
        $userPoolId = ($outputs | Where-Object { $_.OutputKey -eq "CognitoUserPoolId" }).OutputValue
        $clientId = ($outputs | Where-Object { $_.OutputKey -eq "CognitoUserPoolClientId" }).OutputValue
        
        if (-not $userPoolId -or -not $clientId) {
            throw "Não foi possível obter os IDs do Cognito. Verifique se a stack foi implantada corretamente."
        }
        
        return @{
            UserPoolId = $userPoolId
            ClientId = $clientId
        }
    }
    catch {
        Write-Host "✗ Erro ao obter configuração: $_" -ForegroundColor Red
        exit 1
    }
}

# Criar novo usuário
function New-CognitoUser {
    param($Config, $Email, $Password, $Name)
    
    if (-not $Email -or -not $Password -or -not $Name) {
        Write-Host "✗ Para criar usuário, forneça: -Email, -Password e -Name" -ForegroundColor Red
        exit 1
    }
    
    try {
        Write-Host "ℹ Criando usuário $Email..." -ForegroundColor Cyan
        
        # Criar usuário
        aws cognito-idp admin-create-user `
            --user-pool-id $Config.UserPoolId `
            --username $Email `
            --user-attributes Name=email,Value=$Email Name=name,Value=$Name Name=email_verified,Value=true `
            --message-action SUPPRESS
        
        # Definir senha permanente
        aws cognito-idp admin-set-user-password `
            --user-pool-id $Config.UserPoolId `
            --username $Email `
            --password $Password `
            --permanent
        
        Write-Host "✓ Usuário criado com sucesso!" -ForegroundColor Green
        Write-Host "ℹ Email: $Email" -ForegroundColor Cyan
        Write-Host "ℹ Nome: $Name" -ForegroundColor Cyan
    }
    catch {
        Write-Host "✗ Erro ao criar usuário: $_" -ForegroundColor Red
        exit 1
    }
}

# Autenticar usuário e obter token
function Get-CognitoToken {
    param($Config, $Email, $Password)
    
    if (-not $Email -or -not $Password) {
        Write-Host "✗ Para login, forneça: -Email e -Password" -ForegroundColor Red
        exit 1
    }
    
    try {
        Write-Host "ℹ Autenticando usuário $Email..." -ForegroundColor Cyan
        
        $authResult = aws cognito-idp initiate-auth `
            --auth-flow USER_PASSWORD_AUTH `
            --client-id $Config.ClientId `
            --auth-parameters USERNAME=$Email,PASSWORD=$Password `
            --query "AuthenticationResult" `
            --output json | ConvertFrom-Json
        
        Write-Host "✓ Autenticação realizada com sucesso!" -ForegroundColor Green
        Write-Host "`n=== TOKENS ===" -ForegroundColor Yellow
        Write-Host "IdToken (use este para Authorization header):" -ForegroundColor Cyan
        Write-Host $authResult.IdToken -ForegroundColor White
        Write-Host "`nAccessToken:" -ForegroundColor Cyan
        Write-Host $authResult.AccessToken -ForegroundColor White
        Write-Host "`nRefreshToken:" -ForegroundColor Cyan
        Write-Host $authResult.RefreshToken -ForegroundColor White
        Write-Host "`nExpira em: $($authResult.ExpiresIn) segundos" -ForegroundColor Yellow
        
        # Salvar token em arquivo para fácil acesso
        $authResult.IdToken | Out-File -FilePath ".\cognito-token.txt" -NoNewline
        Write-Host "`nℹ IdToken salvo em: .\cognito-token.txt" -ForegroundColor Cyan
        
        return $authResult
    }
    catch {
        Write-Host "✗ Erro ao autenticar: $_" -ForegroundColor Red
        exit 1
    }
}

# Deletar usuário
function Remove-CognitoUser {
    param($Config, $Email)
    
    if (-not $Email) {
        Write-Host "✗ Para deletar usuário, forneça: -Email" -ForegroundColor Red
        exit 1
    }
    
    try {
        Write-Host "ℹ Deletando usuário $Email..." -ForegroundColor Cyan
        
        aws cognito-idp admin-delete-user `
            --user-pool-id $Config.UserPoolId `
            --username $Email
        
        Write-Host "✓ Usuário deletado com sucesso!" -ForegroundColor Green
    }
    catch {
        Write-Host "✗ Erro ao deletar usuário: $_" -ForegroundColor Red
        exit 1
    }
}

# Listar usuários
function Get-CognitoUsers {
    param($Config)
    
    try {
        Write-Host "ℹ Listando usuários..." -ForegroundColor Cyan
        
        $users = aws cognito-idp list-users `
            --user-pool-id $Config.UserPoolId `
            --query 'Users[*].[Username,Attributes[?Name==`email`].Value|[0],Attributes[?Name==`name`].Value|[0],UserStatus]' `
            --output table
        
        Write-Host $users
    }
    catch {
        Write-Host "✗ Erro ao listar usuários: $_" -ForegroundColor Red
        exit 1
    }
}

# Main
$config = Get-CognitoConfig

switch ($Action) {
    "create" { New-CognitoUser -Config $config -Email $Email -Password $Password -Name $Name }
    "login" { Get-CognitoToken -Config $config -Email $Email -Password $Password }
    "delete" { Remove-CognitoUser -Config $config -Email $Email }
    "list" { Get-CognitoUsers -Config $config }
}
