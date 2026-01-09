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
    [string]$StackName = "techchallenge-feedback-lambdas"
)

# Cores para output
function Write-Success { param($message) Write-Host "✓ $message" -ForegroundColor Green }
function Write-Error { param($message) Write-Host "✗ $message" -ForegroundColor Red }
function Write-Info { param($message) Write-Host "ℹ $message" -ForegroundColor Cyan }

# Obter IDs do Cognito a partir dos outputs do CloudFormation
function Get-CognitoConfig {
    try {
        Write-Info "Obtendo configuração do Cognito..."
        
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
        Write-Error "Erro ao obter configuração: $_"
        exit 1
    }
}

# Criar novo usuário
function New-CognitoUser {
    param($Config, $Email, $Password, $Name)
    
    if (-not $Email -or -not $Password -or -not $Name) {
        Write-Error "Para criar usuário, forneça: -Email, -Password e -Name"
        exit 1
    }
    
    try {
        Write-Info "Criando usuário $Email..."
        
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
        
        Write-Success "Usuário criado com sucesso!"
        Write-Info "Email: $Email"
        Write-Info "Nome: $Name"
    }
    catch {
        Write-Error "Erro ao criar usuário: $_"
        exit 1
    }
}

# Autenticar usuário e obter token
function Get-CognitoToken {
    param($Config, $Email, $Password)
    
    if (-not $Email -or -not $Password) {
        Write-Error "Para login, forneça: -Email e -Password"
        exit 1
    }
    
    try {
        Write-Info "Autenticando usuário $Email..."
        
        $authResult = aws cognito-idp initiate-auth `
            --auth-flow USER_PASSWORD_AUTH `
            --client-id $Config.ClientId `
            --auth-parameters USERNAME=$Email,PASSWORD=$Password `
            --query "AuthenticationResult" `
            --output json | ConvertFrom-Json
        
        Write-Success "Autenticação realizada com sucesso!"
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
        Write-Info "`nIdToken salvo em: .\cognito-token.txt"
        
        return $authResult
    }
    catch {
        Write-Error "Erro ao autenticar: $_"
        exit 1
    }
}

# Deletar usuário
function Remove-CognitoUser {
    param($Config, $Email)
    
    if (-not $Email) {
        Write-Error "Para deletar usuário, forneça: -Email"
        exit 1
    }
    
    try {
        Write-Info "Deletando usuário $Email..."
        
        aws cognito-idp admin-delete-user `
            --user-pool-id $Config.UserPoolId `
            --username $Email
        
        Write-Success "Usuário deletado com sucesso!"
    }
    catch {
        Write-Error "Erro ao deletar usuário: $_"
        exit 1
    }
}

# Listar usuários
function Get-CognitoUsers {
    param($Config)
    
    try {
        Write-Info "Listando usuários..."
        
        $users = aws cognito-idp list-users `
            --user-pool-id $Config.UserPoolId `
            --query "Users[*].[Username,Attributes[?Name=='email'].Value|[0],Attributes[?Name=='name'].Value|[0],UserStatus]" `
            --output table
        
        Write-Host $users
    }
    catch {
        Write-Error "Erro ao listar usuários: $_"
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
