# 🚀 Tech Challenge - Sistema de Feedbacks Serverless

Este repositório contém uma aplicação **serverless** desenvolvida em **Java 21** com **Maven**, empacotada como funções **AWS Lambda**. O sistema gerencia feedbacks de clientes com notificações automáticas para casos críticos.

## 📋 Visão Geral do Sistema

Este é um **sistema serverless de gerenciamento de feedbacks** que implementa uma arquitetura orientada a eventos na AWS. O sistema possui 3 funções Lambda conectadas em fluxo:

### **1. ingest-feedback (Ponto de Entrada)**
- Recebe feedbacks via **API Gateway** (POST `/feedback`)
- Protegida por autenticação **Cognito** (incluindo suporte a login social com Google)
- Salva o feedback no **DynamoDB** com ID único gerado automaticamente
- Retorna confirmação com `feedbackId` e `timestamp`

### **2. send-queue (Processador de Eventos)**
- Acionada automaticamente por **DynamoDB Streams** quando novo feedback é inserido
- Analisa se o feedback é crítico:
  - Categoria "Critical" **OU**
  - Rating ≤ 2
- Publica evento no **EventBridge** com campo `isCritical`

### **3. notify-critical (Notificador)**
- Acionada pelo **EventBridge** apenas para feedbacks críticos (`isCritical: true`)
- Envia e-mail via **API Mailtrap** para equipe de suporte
- Formata notificação com todos os dados do feedback

---

## 📂 Estrutura do Projeto

```
techchallenge-feedback/
├── events/
│   ├── event.json
│   ├── invoke-payload.json
│   └── notify-event.json
├── ingest-feedback/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── IngestFeedbackFunction.java
│   │   └── test/java/lambda/
│   │       └── IngestFeedbackFunctionTest.java
│   └── target/
├── notify-critical/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   ├── FeedbackEvent.java
│   │   │   └── NotifyCriticalFunction.java
│   │   └── test/java/lambda/
│   │       └── NotifyCriticalFunctionTest.java
│   └── target/
├── send-queue/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   └── test/java/lambda/
│   └── target/
├── pom.xml
├── template.yaml
├── samconfig.toml
└── README.md
```

---


## 📂 Arquivos importantes

- **template.yaml** → Template AWS SAM que declara funções Lambda, permissões e recursos necessários.
- **samconfig.toml** → Configurações de deploy do SAM (opcional).
- **events/event.json** → Exemplo de evento para invocar localmente a função.
- **pom.xml (raiz)** → Build multimódulo Maven.

---

## 🏗️ Arquitetura do Sistema

```
┌─────────────┐
│   Cliente   │
└──────┬──────┘
       │ POST /feedback
       ↓
┌─────────────────────────┐
│  API Gateway + Cognito  │ ← Autenticação JWT
└──────────┬──────────────┘
           │
           ↓
┌──────────────────────┐
│  Lambda: ingest      │ ← Handler de entrada
│  -feedback           │
└──────┬───────────────┘
       │ PutItem
       ↓
┌──────────────────┐
│    DynamoDB      │ ← Armazena feedbacks
│  FeedbacksTable  │
└──────┬───────────┘
       │ Streams
       ↓
┌──────────────────────┐
│  Lambda: send-queue  │ ← Processa eventos
└──────┬───────────────┘
       │ PutEvents (isCritical=true)
       ↓
┌──────────────────┐
│   EventBridge    │ ← Filtra eventos críticos
└──────┬───────────┘
       │ Invoke (apenas críticos)
       ↓
┌──────────────────────────┐
│  Lambda: notify-critical │ ← Envia notificações
└──────┬───────────────────┘
       │ HTTP POST
       ↓
┌──────────────┐
│  Mailtrap    │ ← Serviço de e-mail
│     API      │
└──────────────┘

          ┌────────────┐
          │  SQS DLQ   │ ← Captura falhas
          └────────────┘
```

### **Recursos AWS Utilizados**
- **API Gateway** com autorização Cognito
- **DynamoDB** com Streams habilitado
- **EventBridge** com regra de roteamento para eventos críticos
- **SQS Dead Letter Queue** para tratamento de falhas
- **CloudWatch Logs** para monitoramento e debugging
- **Cognito User Pool** com suporte a Google OAuth2

### **Tecnologias**
- Java 21
- Maven (arquitetura multi-módulo)
- AWS SAM (infraestrutura como código)
- AWS SDK v2
- Jackson para serialização JSON

---

## ⚙️ Pré-requisitos

- **AWS CLI** configurado com credenciais válidas
- **AWS SAM CLI** (versão 1.x ou superior)
- **Java 21** (JDK instalado)
- **Maven 3.8+**
- **Conta AWS** com permissões para criar recursos Lambda, DynamoDB, API Gateway, Cognito, etc.

### Verificar instalações:

```bash
java -version    # Deve mostrar Java 21
mvn -version     # Deve mostrar Maven 3.8+
sam --version    # Deve mostrar SAM CLI 1.x+
aws --version    # Deve mostrar AWS CLI
```

---

## 🔨 Build do Projeto

Na raiz do repositório, execute:

```bash
mvn clean package

```

Este comando irá:
- Compilar todos os módulos (ingest-feedback, send-queue, notify-critical)
- Executar os testes unitários
- Gerar os JARs empacotados com todas as dependências (uber JARs) em:
  - `ingest-feedback/target/ingest-feedback-1.0.jar`
  - `send-queue/target/send-queue-1.0.jar`
  - `notify-critical/target/notify-critical-1.0.jar`

---

## 📦 Deploy para AWS

### Passo 1: Compilar o Projeto

```bash
mvn clean package
```

### Passo 2: Deploy com SAM

```bash
sam deploy --guided
```

Durante o deploy guiado, você será questionado sobre:
- **Stack Name**: `techchallenge-feedback` (ou escolha outro nome)
- **AWS Region**: `us-east-1` (recomendado)
- **Confirm changes before deploy**: `Y`
- **Allow SAM CLI IAM role creation**: `Y`
- **Disable rollback**: `Y` (para debug, em produção use `N`)
- **Save arguments to configuration file**: `Y`

### Passo 3: Atualizar Códigos das Lambdas

Devido a limitações do SAM com projetos Maven multi-módulo, após o primeiro deploy, atualize manualmente os códigos:

```bash
# Atualizar ingest-feedback
aws lambda update-function-code --function-name ingest-feedback \
  --zip-file fileb://ingest-feedback/target/ingest-feedback-1.0.jar

# Atualizar send-queue
aws lambda update-function-code --function-name send-queue \
  --zip-file fileb://send-queue/target/send-queue-1.0.jar

# Atualizar notify-critical
aws lambda update-function-code --function-name notify-critical \
  --zip-file fileb://notify-critical/target/notify-critical-1.0.jar
```

### Passo 4: Obter URLs e IDs

Após o deploy, anote os outputs:

```bash
# Obter URL da API
aws cloudformation describe-stacks --stack-name techchallenge-feedback \
  --query 'Stacks[0].Outputs[?OutputKey==`FeedbackApiUrl`].OutputValue' \
  --output text

# Obter User Pool ID
aws cognito-idp list-user-pools --max-results 10 \
  --query 'UserPools[?Name==`FeedbackUserPool`].Id' \
  --output text

# Obter Client ID
aws cognito-idp list-user-pool-clients --user-pool-id <USER_POOL_ID> \
  --query 'UserPoolClients[0].ClientId' \
  --output text
```

---

## 👤 Configurar Usuário de Teste

### Criar usuário no Cognito:

```bash
# Substitua <USER_POOL_ID> pelo ID obtido anteriormente
aws cognito-idp admin-create-user \
  --user-pool-id <USER_POOL_ID> \
  --username teste@exemplo.com \
  --temporary-password TempPass123! \
  --message-action SUPPRESS

# Definir senha permanente
aws cognito-idp admin-set-user-password \
  --user-pool-id <USER_POOL_ID> \
  --username teste@exemplo.com \
  --password SenhaSegura123! \
  --permanent
```

### Habilitar autenticação via password:

```bash
aws cognito-idp update-user-pool-client \
  --user-pool-id <USER_POOL_ID> \
  --client-id <CLIENT_ID> \
  --explicit-auth-flows ALLOW_ADMIN_USER_PASSWORD_AUTH ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH
```

---

## 🧪 Testes via Linha de Comando

### 1. Obter Token de Autenticação

```bash
# PowerShell
$auth = aws cognito-idp admin-initiate-auth `
  --user-pool-id <USER_POOL_ID> `
  --client-id <CLIENT_ID> `
  --auth-flow ADMIN_NO_SRP_AUTH `
  --auth-parameters USERNAME="teste@exemplo.com",PASSWORD="SenhaSegura123!" | ConvertFrom-Json

$idToken = $auth.AuthenticationResult.IdToken
```

```bash
# Bash/Linux
export ID_TOKEN=$(aws cognito-idp admin-initiate-auth \
  --user-pool-id <USER_POOL_ID> \
  --client-id <CLIENT_ID> \
  --auth-flow ADMIN_NO_SRP_AUTH \
  --auth-parameters USERNAME=teste@exemplo.com,PASSWORD=SenhaSegura123! \
  --query 'AuthenticationResult.IdToken' \
  --output text)
```

### 2. Enviar Feedback via API

```bash
# PowerShell
$headers = @{
    "Authorization" = $idToken
    "Content-Type" = "application/json"
}

$body = @{
    fullName = "João Silva"
    category = "Critical"
    comment = "Sistema muito lento!"
    rating = 1
} | ConvertTo-Json

Invoke-RestMethod -Uri "<API_URL>" -Method Post -Headers $headers -Body $body
```

```bash
# Bash/Linux/Mac
curl -X POST "<API_URL>" \
  -H "Authorization: ${ID_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "João Silva",
    "category": "Critical",
    "comment": "Sistema muito lento!",
    "rating": 1
  }'
```

### 3. Verificar Feedbacks no DynamoDB

```bash
aws dynamodb scan --table-name FeedbacksTable --output table
```

### 4. Ver Logs das Lambdas

```bash
# Logs da função ingest-feedback
aws logs tail /aws/lambda/ingest-feedback --since 5m --format short

# Logs da função send-queue
aws logs tail /aws/lambda/send-queue --since 5m --format short

# Logs da função notify-critical (apenas feedbacks críticos)
aws logs tail /aws/lambda/notify-critical --since 5m --format short
```

---

## 📮 Testes com Postman

### Passo 1: Importar Collection

1. Abra o Postman
2. Clique em **Import** no canto superior esquerdo
3. Selecione o arquivo `postman_collection.json` deste repositório
4. A collection "Tech Challenge - Feedbacks API" será importada

### Passo 2: Configurar Variáveis de Ambiente

Na collection, configure as seguintes variáveis:

| Variável | Descrição | Valor |
|----------|-----------|---------|  
| `api_url` | URL da API Gateway | `https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedback` |
| `user_pool_id` | ID do Cognito User Pool | `us-east-1_tOiC4wx53` |
| `client_id` | ID do Cognito Client | `6rqg0qir3728q1eh00smouvm60` |
| `username` | Email do usuário de teste | `teste@fiap.com` |
| `password` | Senha do usuário | `FiapTeste123!` |

### Passo 3: Obter Token JWT

1. Execute a requisição **"1. Get JWT Token"**
2. O token será automaticamente salvo na variável `id_token`
3. Todas as outras requisições usarão este token automaticamente

### Passo 4: Enviar Feedbacks

Use as requisições pré-configuradas:

- **2. Send Critical Feedback** - Feedback crítico (gera notificação)
- **3. Send Low Rating Feedback** - Rating baixo (gera notificação)
- **4. Send Normal Feedback** - Feedback normal (não gera notificação)
- **5. Send Positive Feedback** - Feedback positivo (não gera notificação)

### Estrutura da Collection

```
Tech Challenge - Feedbacks API/
├── 1. Get JWT Token (POST) - Obtém token do Cognito
├── 2. Send Critical Feedback (POST) - Categoria Critical
├── 3. Send Low Rating Feedback (POST) - Rating 2
├── 4. Send Normal Feedback (POST) - Categoria General
└── 5. Send Positive Feedback (POST) - Rating 5
```

### Testando Diferentes Cenários

**Feedback Crítico (gera notificação):**
- `category`: "Critical" OU
- `rating`: 1 ou 2

**Feedback Normal (não gera notificação):**
- `category`: "General", "Service", "Suggestion"
- `rating`: 3, 4 ou 5

---

## 🔍 Monitoramento

### Script de Monitoramento

Execute o script para verificar o estado do sistema:

```bash
# PowerShell
.\monitor.ps1
```

O script mostra:
- Status das 3 Lambdas
- Total de feedbacks no DynamoDB
- Mensagens na Dead Letter Queue
- Invocações recentes (últimos 5 minutos)

### Métricas no CloudWatch

Acesse o CloudWatch Console para visualizar:
- **Invocations**: Número de execuções de cada Lambda
- **Errors**: Quantidade de erros
- **Duration**: Tempo médio de execução
- **Throttles**: Requisições bloqueadas por limite

---

## 🚨 Troubleshooting

### Lambda retorna ClassNotFoundException

**Problema**: Código não foi empacotado corretamente

**Solução**:
```bash
mvn clean package
aws lambda update-function-code --function-name ingest-feedback \
  --zip-file fileb://ingest-feedback/target/ingest-feedback-1.0.jar
```

### Notificação não foi enviada

**Causas possíveis**:
1. Feedback não é crítico (verifique categoria e rating)
2. Token Mailtrap inválido
3. Erro na Lambda notify-critical

**Verificar logs**:
```bash
aws logs tail /aws/lambda/notify-critical --since 10m --format short
```

### Erro de autenticação no Cognito

**Problema**: Token expirado ou credenciais inválidas

**Solução**:
- Tokens JWT expiram em 1 hora
- Gere um novo token com o comando de autenticação
- Verifique se o fluxo ADMIN_NO_SRP_AUTH está habilitado

### DynamoDB não recebe dados

**Verificar**:
1. Permissões IAM da Lambda ingest-feedback
2. Logs da Lambda: `aws logs tail /aws/lambda/ingest-feedback --since 5m`
3. Nome da tabela no código (deve ser "FeedbacksTable")

---

## 🗑️ Limpeza de Recursos

Para deletar todos os recursos criados na AWS:

```bash
# Deletar stack do CloudFormation
aws cloudformation delete-stack --stack-name techchallenge-feedback

# Aguardar conclusão
aws cloudformation wait stack-delete-complete --stack-name techchallenge-feedback

# Deletar bucket S3 do SAM (se necessário)
aws s3 rb s3://aws-sam-cli-managed-default-samclisourcebucket-xxxx --force
```

---

## 📊 Regras de Negócio

### Feedback Crítico

Um feedback é considerado **crítico** quando atende a **pelo menos uma** das condições:
- `category == "Critical"`
- `rating <= 2`

### Fluxo de Notificação

1. Feedback salvo no DynamoDB
2. DynamoDB Streams → Lambda send-queue
3. send-queue avalia criticidade e publica no EventBridge
4. EventBridge filtra eventos com `isCritical: true`
5. EventBridge → Lambda notify-critical
6. notify-critical envia e-mail via Mailtrap API

---

## 📚 Referências

- [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html)
- [AWS SAM](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html)
- [Maven](https://maven.apache.org/)
- [Java](https://www.oracle.com/java/)
