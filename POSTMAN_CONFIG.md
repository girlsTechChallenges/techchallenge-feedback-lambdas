# Configuração das Variáveis do Postman

## Como Configurar

1. Abra o Postman
2. Importe a collection: `postman_collection.json`
3. Clique na collection "Tech Challenge - Feedbacks API"
4. Vá em "Variables"
5. Cole os valores abaixo:

## Variáveis para Copiar e Colar

```
VARIABLE          | INITIAL VALUE                                              | CURRENT VALUE
------------------|------------------------------------------------------------|-----------------
api_url           | https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod | (same)
user_pool_id      | us-east-1_Etx3Vkioi                                        | (same)
client_id         | 638r9k783e2571ev516nue1eji                                | (same)
username          | test@example.com                                           | (same)
password          | TestPass123!                                               | (same)
id_token          | (deixe vazio - será preenchido automaticamente)            | (vazio)
```

## Formato CSV para Importação

Você também pode criar um arquivo CSV e importar:

```csv
variable,type,value
api_url,default,https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod
user_pool_id,default,us-east-1_Etx3Vkioi
client_id,default,638r9k783e2571ev516nue1eji
username,default,test@example.com
password,default,TestPass123!
id_token,default,
```

Salve como `postman_variables.csv` e importe no Postman.

## Próximos Passos

1. ✅ Variáveis configuradas
2. ▶️ Execute "1. Get JWT Token"
3. ✅ Token salvo automaticamente
4. 🚀 Teste as outras requisições!

## Credenciais de Teste

- **Email/Username:** test@example.com
- **Password:** TestPass123!
- **User Pool:** us-east-1_Etx3Vkioi
- **Client ID:** 638r9k783e2571ev516nue1eji

## URLs das APIs

- **Insert Feedback:** https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedback
- **List Feedbacks:** https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedbacks

---

## Testando Manualmente (sem Postman)

Se preferir testar via cURL:

### 1. Obter Token

```powershell
$body = @{
    AuthFlow = "USER_PASSWORD_AUTH"
    ClientId = "638r9k783e2571ev516nue1eji"
    AuthParameters = @{
        USERNAME = "test@example.com"
        PASSWORD = "TestPass123!"
    }
} | ConvertTo-Json

$response = aws cognito-idp initiate-auth --cli-input-json $body --region us-east-1
$token = ($response | ConvertFrom-Json).AuthenticationResult.IdToken
```

### 2. Inserir Feedback

```powershell
$headers = @{
    "Authorization" = $token
    "Content-Type" = "application/json"
}

$body = @{
    customerName = "João Silva"
    rating = 5
    comment = "Excelente!"
    category = "Atendimento"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedback" `
    -Headers $headers `
    -Body $body
```

### 3. Listar Feedbacks

```powershell
Invoke-RestMethod -Method Get `
    -Uri "https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedbacks" `
    -Headers $headers
```
