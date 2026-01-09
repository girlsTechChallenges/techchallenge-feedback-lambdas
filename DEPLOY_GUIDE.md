# 🚀 Guia de Deploy com Cognito

Este guia detalha como fazer o deploy da aplicação com autenticação AWS Cognito habilitada.

## Pré-requisitos

- AWS CLI configurado com credenciais válidas
- AWS SAM CLI instalado (versão 1.x ou superior)
- Java 21 e Maven instalados
- Permissões AWS para criar recursos (Lambda, DynamoDB, API Gateway, Cognito, etc.)

## Passo 1: Compilar o Projeto

```bash
# Na raiz do projeto
mvn clean package

# Verificar se todos os módulos compilaram com sucesso
```

## Passo 2: Build com SAM

```bash
# Build da aplicação
sam build

# Verificar output
```

## Passo 3: Deploy

```bash
# Deploy guiado (primeira vez)
sam deploy --guided

# Responda as perguntas:
# - Stack Name: techchallenge-feedback-lambdas (ou nome de sua preferência)
# - AWS Region: us-east-1 (ou sua região preferida)
# - Confirmar mudanças: Y
# - Permitir criação de roles IAM: Y
# - Autorizar criação de recursos: Y
# - Salvar parâmetros em samconfig.toml: Y
```

**Output esperado:**
```
CloudFormation outputs from deployed stack
-------------------------------------------------
Outputs
-------------------------------------------------
Key                 FeedbackApiUrl
Description         URL da API de feedback (protegida por Cognito)
Value               https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback

Key                 ListFeedbacksApiUrl
Description         URL da API para listar feedbacks (protegida por Cognito)
Value               https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks

Key                 CognitoUserPoolId
Description         ID do Cognito User Pool
Value               us-east-1_xxxxxxxxx

Key                 CognitoUserPoolClientId
Description         ID do Cognito User Pool Client
Value               xxxxxxxxxxxxxxxxxxxxxxxxxx
-------------------------------------------------
```

## Passo 4: Criar Usuário de Teste

Navegue até a pasta de scripts:

```powershell
cd cognito-scripts
```

Crie um usuário de teste:

```powershell
.\manage-users.ps1 -Action create -Email "teste@fiap.com" -Password "FiapTeste@123" -Name "Usuario Teste"
```

**Saída esperada:**
```
ℹ Obtendo configuração do Cognito...
ℹ Criando usuário teste@fiap.com...
✓ Usuário criado com sucesso!
ℹ Email: teste@fiap.com
ℹ Nome: Usuario Teste
```

## Passo 5: Testar Autenticação

Obtenha um token JWT:

```powershell
.\manage-users.ps1 -Action login -Email "teste@fiap.com" -Password "FiapTeste@123"
```

**Saída esperada:**
```
ℹ Autenticando usuário teste@fiap.com...
✓ Autenticação realizada com sucesso!

=== TOKENS ===
IdToken (use este para Authorization header):
eyJraWQiOiJ... [token JWT completo]

AccessToken:
eyJraWQiOiJ... [token completo]

RefreshToken:
eyJjdHkiOiJ... [token completo]

Expira em: 3600 segundos

ℹ IdToken salvo em: .\cognito-token.txt
```

## Passo 6: Testar API

### Teste Automatizado

```powershell
# Inserir feedback
.\test-api-with-auth.ps1 -Action insert -Email "teste@fiap.com" -Password "FiapTeste@123"

# Listar feedbacks
.\test-api-with-auth.ps1 -Action list -Email "teste@fiap.com" -Password "FiapTeste@123"
```

### Teste Manual com PowerShell

```powershell
# 1. Obter URLs do CloudFormation
$apiUrl = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackApiUrl'].OutputValue" `
  --output text

$listUrl = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='ListFeedbacksApiUrl'].OutputValue" `
  --output text

# 2. Ler token do arquivo
$token = Get-Content ".\cognito-token.txt" -Raw

# 3. Criar feedback
$body = @{
    descricao = "Teste com autenticação Cognito!"
    nota = "5"
    urgencia = "MEDIA"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

$response = Invoke-RestMethod -Uri $apiUrl -Method POST -Headers $headers -Body $body
Write-Host "Feedback criado:" -ForegroundColor Green
$response | ConvertTo-Json

# 4. Listar feedbacks
$response = Invoke-RestMethod -Uri "$listUrl?startDate=2026-01-01&endDate=2026-12-31" -Method GET -Headers $headers
Write-Host "Feedbacks encontrados:" -ForegroundColor Green
$response | ConvertTo-Json -Depth 5
```

## Passo 7: Testar Erro de Autenticação

Tente acessar sem token:

```powershell
# Deve retornar 401 Unauthorized
Invoke-RestMethod -Uri $apiUrl -Method POST -Body (@{descricao="Teste sem auth"} | ConvertTo-Json) -ContentType "application/json"
```

**Resposta esperada (erro):**
```json
{
  "message": "Unauthorized"
}
```

## Passo 8: Testar com Postman

1. **Importe a collection**: Abra Postman e importe `postman_collection.json`

2. **Atualize as variáveis da collection**:
   - Clique na collection → Variables
   - Atualize `api_url` com o valor de `FeedbackApiUrl` do deploy
   - Atualize `list_api_url` com o valor de `ListFeedbacksApiUrl`
   - Atualize `user_pool_id` com o valor de `CognitoUserPoolId`
   - Atualize `client_id` com o valor de `CognitoUserPoolClientId`
   - Defina `username` como "teste@fiap.com"
   - Defina `password` como "FiapTeste@123"

3. **Execute "1. Get JWT Token"**: O token será salvo automaticamente

4. **Execute os outros requests**: Teste envio de feedbacks críticos, normais, listagem, etc.

## Troubleshooting

### "Não foi possível obter os IDs do Cognito"

```powershell
# Verificar se a stack foi criada corretamente
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas

# Listar todos os outputs
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query 'Stacks[0].Outputs' `
  --output table
```

### "Erro ao autenticar: InvalidParameterException"

Verifique se a senha atende aos requisitos:
- Mínimo 8 caracteres
- Pelo menos 1 letra maiúscula
- Pelo menos 1 letra minúscula
- Pelo menos 1 número
- Pelo menos 1 caractere especial

### Token expirado

Os tokens IdToken e AccessToken expiram em 1 hora. Execute novamente:

```powershell
.\manage-users.ps1 -Action login -Email "teste@fiap.com" -Password "FiapTeste@123"
```

### API retorna 403 Forbidden

Verifique se o token está sendo enviado corretamente no header:
```
Authorization: Bearer <token_aqui>
```

## Próximos Passos

Após validar que a autenticação está funcionando:

1. ✅ Configure email do SES para relatórios semanais
2. ✅ Configure Mailtrap para notificações críticas
3. ✅ Crie mais usuários de teste
4. ✅ Execute a Step Function de relatórios manualmente
5. ✅ Monitore logs no CloudWatch

## Comandos Úteis

```powershell
# Ver logs da API Gateway
aws logs tail /aws/apigateway/welcome --follow

# Ver logs do Lambda insert-feedback
aws logs tail /aws/lambda/insert-feedback --follow

# Listar usuários do Cognito
.\manage-users.ps1 -Action list

# Ver feedbacks no DynamoDB
aws dynamodb scan --table-name FeedbacksTable --limit 10

# Redeployar após mudanças
sam build && sam deploy --no-confirm-changeset
```

## Deploy em Ambiente de Produção

Para produção, considere:

1. **Usar domínio customizado**:
   ```bash
   # Adicionar Custom Domain no API Gateway
   ```

2. **Habilitar logs de acesso**:
   ```yaml
   # Adicionar no template.yaml
   AccessLogSettings:
     DestinationArn: !GetAtt ApiGatewayLogGroup.Arn
   ```

3. **Adicionar WAF**:
   ```bash
   # Proteger API Gateway com AWS WAF
   ```

4. **Configurar alertas CloudWatch**:
   ```bash
   # Criar alarmes para erros 4xx e 5xx
   ```

5. **Habilitar X-Ray**:
   ```yaml
   # Adicionar tracing no template.yaml
   Tracing: Active
   ```

---

**Deploy concluído com sucesso! 🎉**

Para dúvidas ou problemas, consulte o [README.md](README.md) completo ou a documentação oficial da AWS.
