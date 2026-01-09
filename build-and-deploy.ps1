# Build and Deploy Script for Tech Challenge Feedback System
# Autor: Tech Challenge FIAP - Fase 4
# Descrição: Compila todos os módulos Lambda e faz deploy com AWS SAM

param(
    [switch]$SkipTests,
    [switch]$BuildOnly,
    [switch]$DeployOnly
)

$ErrorActionPreference = "Stop"

Write-Host "🚀 Tech Challenge - Build & Deploy Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Função para verificar se comando existe
function Test-CommandExists {
    param($command)
    $null = Get-Command $command -ErrorAction SilentlyContinue
    return $?
}

# Verificar pré-requisitos
if (-not (Test-CommandExists "mvn")) {
    Write-Host "❌ Maven não encontrado! Instale o Maven primeiro." -ForegroundColor Red
    exit 1
}

if (-not $DeployOnly) {
    # BUILD
    Write-Host "📦 ETAPA 1: Compilando todos os módulos..." -ForegroundColor Yellow
    Write-Host ""
    
    $mvnArgs = @("clean", "package")
    if ($SkipTests) {
        $mvnArgs += "-DskipTests"
        Write-Host "⚠️  Testes unitários serão ignorados" -ForegroundColor Yellow
    }
    
    & mvn $mvnArgs
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "❌ Erro no build! Verifique os logs acima." -ForegroundColor Red
        exit 1
    }
    
    Write-Host ""
    Write-Host "✅ Build concluído com sucesso!" -ForegroundColor Green
    Write-Host ""
}

if ($BuildOnly) {
    Write-Host "🎉 Build finalizado! (modo --BuildOnly)" -ForegroundColor Green
    exit 0
}

if (-not (Test-CommandExists "sam")) {
    Write-Host "❌ AWS SAM CLI não encontrado! Instale o SAM CLI primeiro." -ForegroundColor Red
    Write-Host "   https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html" -ForegroundColor Yellow
    exit 1
}

# DEPLOY
Write-Host "📤 ETAPA 2: Fazendo deploy com AWS SAM..." -ForegroundColor Yellow
Write-Host ""

sam build

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Erro no sam build! Verifique os logs acima." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🚀 Iniciando deploy..." -ForegroundColor Cyan
sam deploy --no-confirm-changeset --no-fail-on-empty-changeset

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Erro no deploy! Verifique os logs acima." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Deploy concluído com sucesso!" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Próximos passos:" -ForegroundColor Cyan
Write-Host "   1. Obtenha as URLs da API: aws cloudformation describe-stacks --stack-name techchallenge-feedback --query 'Stacks[0].Outputs' --output table" -ForegroundColor White
Write-Host "   2. Configure as variáveis no Postman" -ForegroundColor White
Write-Host "   3. Teste a API com a collection em postman_collection.json" -ForegroundColor White
Write-Host ""
Write-Host "🎉 Sistema pronto para uso!" -ForegroundColor Green
