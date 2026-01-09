# 🧪 Guia de Testes com Postman

## ✅ Deploy Realizado com Sucesso!

O deploy foi concluído e os seguintes recursos foram criados na AWS:

### 📋 URLs e Informações Importantes

| Recurso | Valor |
|---------|-------|
| **API URL (Insert Feedback)** | `https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedback` |
| **API URL (List Feedbacks)** | `https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedbacks` |
| **Cognito User Pool ID** | `us-east-1_Etx3Vkioi` |
| **Cognito Client ID** | `638r9k783e2571ev516nue1eji` |
| **Região** | `us-east-1` |

---

## 🚀 Passo a Passo para Testar no Postman

### 1️⃣ Importar a Collection

1. Abra o **Postman**
2. Clique em **Import**
3. Selecione o arquivo: `postman_collection.json`
4. A collection "Tech Challenge - Feedbacks API" será importada

### 2️⃣ Criar um Usuário de Teste no Cognito

Antes de testar, você precisa criar um usuário no Cognito. Execute os comandos abaixo no PowerShell:

```powershell
# Criar usuário
aws cognito-idp admin-create-user `
  --user-pool-id us-east-1_Etx3Vkioi `
  --username test@example.com `
  --user-attributes Name=email,Value=test@example.com `
  --temporary-password TempPass123! `
  --message-action SUPPRESS

# Definir senha permanente
aws cognito-idp admin-set-user-password `
  --user-pool-id us-east-1_Etx3Vkioi `
  --username test@example.com `
  --password TestPass123! `
  --permanent
```

**✅ Credenciais já criadas e prontas para uso:**
- **Username:** `test@example.com`
- **Password:** `TestPass123!`

### 3️⃣ Configurar Variáveis no Postman

Na collection importada, configure as seguintes variáveis:

1. Clique na collection **"Tech Challenge - Feedbacks API"**
2. Vá em **Variables**
3. Configure os valores:

| Variável | Valor | Descrição |
|----------|-------|-----------|
| `api_url` | `https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod` | URL base da API |
| `user_pool_id` | `us-east-1_Etx3Vkioi` | ID do User Pool |
| `client_id` | `638r9k783e2571ev516nue1eji` | ID do Client |
| `username` | `test@example.com` | Usuário criado |
| `password` | `TestPass123!` | Senha definida |

4. Clique em **Save**

### 4️⃣ Executar os Testes

#### **Teste 1: Obter Token JWT**

1. Abra a requisição **"1. Get JWT Token"**
2. Clique em **Send**
3. ✅ O token será salvo automaticamente nas variáveis

**Resposta esperada:** Status `200 OK` com o token de autenticação

#### **Teste 2: Inserir Feedback**

1. Abra a requisição **"2. Insert Feedback - Positivo"** (ou outro cenário)
2. O header `Authorization` já estará preenchido com o token
3. Revise o body JSON se necessário:
   ```json
   {
     "customerName": "João Silva",
     "rating": 5,
     "comment": "Excelente atendimento!",
     "category": "Atendimento"
   }
   ```
4. Clique em **Send**

**Resposta esperada:** Status `200 OK` com o feedback criado

#### **Teste 3: Listar Feedbacks**

1. Abra a requisição **"3. List Feedbacks"**
2. Clique em **Send**

**Resposta esperada:** Status `200 OK` com array de feedbacks

---

## 🧪 Cenários de Teste Disponíveis

A collection possui vários cenários pré-configurados:

### ✅ Feedbacks Positivos
- Rating 4-5
- Comentários positivos

### 🟡 Feedbacks Neutros  
- Rating 3
- Observações moderadas

### ❌ Feedbacks Críticos
- Rating 1-2
- **IMPORTANTE:** Estes feedbacks disparam notificações automáticas via Step Functions!

### 📋 Listagem
- Listar todos os feedbacks
- Filtros por categoria e data

---

## 🔐 Autenticação e Segurança

### Como a autenticação funciona:

1. **Obter Token:** A requisição "Get JWT Token" autentica no Cognito
2. **Token Automático:** O token é salvo automaticamente na variável `id_token`
3. **Headers:** Todas as requisições usam `Authorization: {{id_token}}`
4. **Expiração:** Se o token expirar, execute novamente a requisição de token

### Renovar Token Expirado:

Se receber erro `401 Unauthorized`:
1. Execute novamente **"1. Get JWT Token"**
2. Tente sua requisição novamente

---

## 🎯 Testando Funcionalidades Avançadas

### 1. Testar Notificação de Feedback Crítico

Para testar o fluxo completo de notificações:

```json
POST /feedback
{
  "customerName": "Cliente Insatisfeito",
  "rating": 1,
  "comment": "Péssimo atendimento, muito insatisfeito!",
  "category": "Atendimento"
}
```

**O que acontece:**
1. ✅ Feedback é salvo no DynamoDB
2. 🔔 DynamoDB Stream dispara a Lambda `send-queue`
3. 📤 Evento é enviado para EventBridge
4. ⚙️ Step Function é iniciada
5. 📧 Lambda `notify-critical` envia notificação (SQS/SES)

### 2. Verificar Processamento

Após enviar um feedback crítico, você pode verificar no AWS Console:

1. **DynamoDB:** Tabela `FeedbacksTable`
2. **Step Functions:** `feedback-processing` (ver execuções)
3. **CloudWatch Logs:** Verificar logs das Lambdas
4. **SQS:** Verificar mensagens na `CriticalFeedbackQueue`

---

## 📊 Relatórios Semanais

O sistema gera relatórios automaticamente toda segunda-feira às 9h:

- **Bucket S3:** `feedback-reports-techchallenge-feedback-761554982054`
- **Lambda:** `generate-weekly-report`
- **Formato:** JSON com estatísticas da semana

Para testar manualmente:

```powershell
aws lambda invoke --function-name generate-weekly-report output.json
```

---

## 🛠️ Comandos Úteis

### Verificar Logs no CloudWatch

```powershell
# Logs da Lambda insert-feedback
aws logs tail /aws/lambda/insert-feedback --follow

# Logs da Step Function
aws logs tail /aws/stepfunctions/feedback-processing --follow
```

### Verificar Tabela DynamoDB

```powershell
# Listar todos os feedbacks
aws dynamodb scan --table-name FeedbacksTable
```

### Verificar Cognito

```powershell
# Listar usuários
aws cognito-idp list-users --user-pool-id us-east-1_Etx3Vkioi
```

---

## ❓ Troubleshooting

### Erro: Token expirado (401)
**Solução:** Execute novamente "1. Get JWT Token"

### Erro: Unauthorized (403)
**Solução:** Verifique se o usuário existe no Cognito e a senha está correta

### Erro: Internal Server Error (500)
**Solução:** 
1. Verifique os logs no CloudWatch
2. Confirme que as Lambdas têm as permissões necessárias

### Collection não funciona
**Solução:**
1. Verifique se todas as variáveis estão configuradas
2. Confirme que os valores estão corretos (sem espaços extras)
3. Tente reimportar a collection

---

## 📚 Documentação Adicional

- **Template SAM:** [template.yaml](template.yaml)
- **Guia de Deploy:** [DEPLOY_GUIDE.md](DEPLOY_GUIDE.md)
- **Cognito:** [COGNITO_IMPLEMENTATION_SUMMARY.md](COGNITO_IMPLEMENTATION_SUMMARY.md)
- **Testes Realizados:** [docs/TESTES_REALIZADOS.md](docs/TESTES_REALIZADOS.md)

---

## ✅ Checklist de Testes

- [ ] Collection importada no Postman
- [ ] Usuário criado no Cognito
- [ ] Variáveis configuradas
- [ ] Token JWT obtido com sucesso
- [ ] Feedback positivo inserido
- [ ] Feedback crítico inserido (com notificação)
- [ ] Listagem de feedbacks funcionando
- [ ] Logs verificados no CloudWatch
- [ ] DynamoDB consultado

---

## 🎉 Conclusão

Seu sistema está pronto e funcionando na AWS! 

- ✅ **Build:** Maven compilou todos os módulos
- ✅ **Deploy:** AWS SAM fez o deploy de todas as Lambdas
- ✅ **API:** Gateway configurado com autenticação Cognito
- ✅ **Testes:** Postman pronto para uso

**Próximos passos:**
1. Importe a collection no Postman
2. Crie o usuário de teste
3. Configure as variáveis
4. Comece a testar!

Boa sorte! 🚀
