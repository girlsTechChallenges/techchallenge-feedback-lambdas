# 🚀 Tech Challenge - Sistema de Feedbacks Serverless

[![AWS Lambda](https://img.shields.io/badge/AWS-Lambda-FF9900?style=flat-square&logo=aws-lambda&logoColor=white)](https://aws.amazon.com/lambda/)
[![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=java&logoColor=white)](https://www.oracle.com/java/)
[![AWS Cognito](https://img.shields.io/badge/AWS-Cognito-FF9900?style=flat-square&logo=amazon-aws&logoColor=white)](https://aws.amazon.com/cognito/)
[![DynamoDB](https://img.shields.io/badge/AWS-DynamoDB-4053D6?style=flat-square&logo=amazon-dynamodb&logoColor=white)](https://aws.amazon.com/dynamodb/)
[![Maven](https://img.shields.io/badge/Maven-3.x-C71A36?style=flat-square&logo=apache-maven&logoColor=white)](https://maven.apache.org/)
[![AWS SAM](https://img.shields.io/badge/AWS-SAM-FF9900?style=flat-square&logo=amazon-aws&logoColor=white)](https://aws.amazon.com/serverless/sam/)

Sistema serverless completo para gerenciamento de feedbacks de clientes com notificações automáticas, relatórios semanais e autenticação enterprise-grade via AWS Cognito.

---

## 📋 Índice

- [Visão Geral](#-visão-geral)
- [Arquitetura do Sistema](#-arquitetura-do-sistema)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Pré-requisitos](#-pré-requisitos)
- [Quick Start](#-quick-start)
- [Instalação e Configuração](#-instalação-e-configuração)
  - [Build Local](#1-build-local)
  - [Deploy AWS (Automatizado)](#2-deploy-aws-automatizado)
  - [Deploy AWS (Manual)](#3-deploy-aws-manual)
- [Autenticação e Segurança (Cognito)](#-autenticação-e-segurança-cognito)
  - [Como Funciona](#como-funciona)
  - [Gerenciamento de Usuários](#gerenciamento-de-usuários)
  - [Ciclo de Vida dos Tokens](#ciclo-de-vida-dos-tokens)
  - [Checklist de Segurança](#checklist-de-segurança)
- [Testando o Sistema](#-testando-o-sistema)
  - [Scripts PowerShell](#1-scripts-powershell)
  - [Postman](#2-postman)
  - [cURL](#3-curl)
  - [Step Functions](#4-step-functions)
- [Monitoramento e Logs](#-monitoramento-e-logs)
- [Testes Unitários](#-testes-unitários)
- [Referência Rápida](#-referência-rápida)
- [Troubleshooting](#-troubleshooting)
- [Limpeza de Recursos](#-limpeza-de-recursos)
- [Melhorias Futuras](#-melhorias-futuras)
- [Recursos Adicionais](#-recursos-adicionais)

---

## 📖 Visão Geral

Este é um **sistema serverless de gerenciamento de feedbacks** desenvolvido em **Java 21** com **Maven**, empacotado como funções **AWS Lambda**. O sistema implementa uma arquitetura orientada a eventos na AWS com **6 funções Lambda** conectadas em dois fluxos principais:

### **Fluxo 1: Inserção e Notificação de Feedbacks Críticos**

1. **insert-feedback** - Recebe feedbacks via API Gateway (POST `/feedback`) com autenticação Cognito
2. **send-queue** - Acionada por DynamoDB Streams, analisa criticidade e publica no EventBridge
3. **notify-critical** - Notifica equipe via email quando feedback é crítico (rating ≤ 2 ou categoria "Critical")

### **Fluxo 2: Geração Automática de Relatórios Semanais**

4. **list-feedbacks** - Consulta feedbacks no DynamoDB (GET `/feedbacks`) com filtros
5. **generate-weekly-report** - Gera estatísticas e salva relatório no S3
6. **notify-report** - Envia relatório por email via Amazon SES

### **Orquestração**

- **Step Functions** coordena o fluxo de relatórios semanais
- **EventBridge** dispara automaticamente todo domingo às 23:00 UTC
- **DLQ** (Dead Letter Queue) trata falhas com retry automático

---

## 🏗️ Arquitetura do Sistema

```
┌─────────────┐
│   Cliente   │
└──────┬──────┘
       │ POST /feedback (Bearer Token JWT)
       ↓
┌─────────────────────────┐
│     API Gateway         │ ← Cognito Authorizer
│  (Cognito Protected)    │
└──────────┬──────────────┘
           │
           ↓
┌──────────────────────┐
│  Lambda:             │ ← Valida e salva feedback
│  insert-feedback     │
└──────┬───────────────┘
       │ PutItem
       ↓
┌──────────────────┐
│    DynamoDB      │ ← Armazena feedbacks
│  FeedbacksTable  │   (GSI: pk-createdAt-index)
└──────┬───────────┘
       │ Streams (NEW_IMAGE)
       ↓
┌──────────────────────┐
│  Lambda:             │ ← Analisa criticidade
│  send-queue          │   (rating ≤ 2 OR Critical)
└──────┬───────────────┘
       │ PutEvents (isCritical=true)
       ↓
┌──────────────────┐
│   EventBridge    │ ← Filtra eventos críticos
└──────┬───────────┘
       │ Invoke (apenas críticos)
       ↓
┌──────────────────────────┐
│  Lambda:                 │ ← Envia notificação
│  notify-critical         │
└──────┬───────────────────┘
       │ HTTP POST
       ↓
┌──────────────┐
│  Mailtrap    │ ← Serviço de e-mail
│     API      │
└──────────────┘

═════════════════════════════════════════════════════════════
          FLUXO DE RELATÓRIOS SEMANAIS
═════════════════════════════════════════════════════════════

┌──────────────────┐
│  EventBridge     │ ← Cron: domingo 23:00 UTC
│  Schedule Rule   │
└────────┬─────────┘
         │ Trigger
         ↓
┌───────────────────────┐
│  Step Function:       │ ← Orquestração
│  feedback-processing  │
└───────┬───────────────┘
        │
        ├─ Step 1 ────────────────────────┐
        ↓                                  │
┌──────────────────────┐                  │
│  Lambda:             │ ← Consulta       │
│  list-feedbacks      │                  │
└───────┬──────────────┘                  │
        │ Query                           │
        ↓                                  │
┌──────────────────┐                      │
│    DynamoDB      │                      │
│  FeedbacksTable  │                      │
└──────────────────┘                      │
        │                                  │
        ├─ Step 2 ────────────────────────┤
        ↓                                  │
┌──────────────────────────┐              │
│  Lambda:                 │ ← Calcula    │
│  generate-weekly-report  │   estatísticas│
└───────┬──────────────────┘              │
        │ PutObject                        │
        ↓                                  │
┌──────────────────┐                      │
│   S3 Bucket:     │ ← Armazena          │
│ feedback-reports │   relatórios         │
└───────┬──────────┘                      │
        │                                  │
        ├─ Step 3 ────────────────────────┘
        ↓
┌──────────────────────┐
│  Lambda:             │ ← Lê S3 e envia
│  notify-report       │
└───────┬──────────────┘
        │ SendEmail
        ↓
┌──────────────┐
│  Amazon SES  │ ← Email AWS
└──────────────┘
```

### **Recursos AWS**

- **6 Lambdas** (Java 21, 512MB RAM, timeout 30s)
- **API Gateway** com Cognito Authorizer
- **DynamoDB** com Streams e GSI (pk-createdAt-index)
- **EventBridge** (regras de roteamento + schedule semanal)
- **Step Functions** (orquestração de relatórios)
- **S3 Bucket** (armazenamento de relatórios)
- **Amazon SES** (envio de emails)
- **SQS DLQ** (tratamento de falhas)
- **CloudWatch Logs** (monitoramento)
- **Cognito User Pool** (autenticação JWT)

### **Tecnologias**

- Java 21
- Maven (arquitetura multi-módulo)
- AWS SAM (infraestrutura como código)
- AWS SDK v2 (DynamoDB, S3, SES, EventBridge)
- Jackson 2.17.2 (serialização JSON)
- JUnit 5 + Mockito (testes)

---

## 📂 Estrutura do Projeto

```
techchallenge-feedback-lambdas/
├── pom.xml                          # Build multi-módulo Maven
├── template.yaml                    # Infraestrutura AWS SAM
├── samconfig.toml                   # Configurações de deploy
├── README.md                        # Este arquivo
│
├── docs/                            # Documentação
│   └── TESTES_REALIZADOS.md         # Histórico de testes e QA
│
├── cognito-scripts/                 # Scripts de gerenciamento Cognito
│   ├── manage-users.ps1             # CRUD de usuários
│   ├── test-api-with-auth.ps1       # Testes automatizados
│   └── README_SCRIPTS.md            # Documentação dos scripts
│
├── postman/                         # Testes Postman
│   └── postman_collection.json      # Collection com todas APIs
│
├── test-payloads/                   # Payloads para testes
│   ├── insert-feedback.json
│   ├── list-feedbacks.json
│   ├── send-queue.json
│   ├── notify-critical.json
│   ├── generate-weekly-report.json
│   └── notify-report.json
│
├── events/                          # Eventos SAM local
│   └── event.json
│
├── examples/                        # Exemplos de JSON
│   ├── response.json
│   ├── test-payload.json
│   └── test-post.json
│
├── statemachine/                    # Step Functions
│   └── feedback-processing.asl.json
│
├── build-and-deploy.ps1             # Script deploy automatizado (Windows)
├── build-and-deploy.sh              # Script deploy automatizado (Linux/Mac)
│
├── insert-feedback/                 # Lambda: Inserir Feedback
│   ├── pom.xml
│   ├── src/main/java/lambda/
│   │   └── InsertFeedbackFunction.java
│   ├── src/test/java/lambda/
│   │   └── InsertFeedbackFunctionTest.java
│   └── target/
│
├── list-feedbacks/                  # Lambda: Listar Feedbacks
│   ├── pom.xml
│   ├── src/main/java/lambda/
│   │   └── ListFeedbacksFunction.java
│   └── src/test/java/lambda/
│
├── send-queue/                      # Lambda: Processar Streams
│   ├── pom.xml
│   ├── src/main/java/lambda/
│   │   └── SendQueueFunction.java
│   └── src/test/java/lambda/
│
├── notify-critical/                 # Lambda: Notificar Críticos
│   ├── pom.xml
│   ├── src/main/java/lambda/
│   │   ├── NotifyCriticalFunction.java
│   │   └── FeedbackEvent.java
│   └── src/test/java/lambda/
│
├── generate-weekly-report/          # Lambda: Gerar Relatório
│   ├── pom.xml
│   ├── src/main/java/lambda/
│   │   └── GenerateWeeklyReportFunction.java
│   └── src/test/java/lambda/
│
└── notify-report/                   # Lambda: Notificar Relatório
    ├── pom.xml
    ├── src/main/java/lambda/
    │   └── NotifyReportFunction.java
    └── src/test/java/lambda/
```

---

## ⚙️ Pré-requisitos

### **Obrigatórios**

- **AWS CLI** 2.x configurado com credenciais válidas
  ```bash
  aws configure
  ```

- **AWS SAM CLI** 1.x ou superior
  ```bash
  # Windows (via MSI installer)
  # https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html
  
  # Linux/Mac
  brew install aws-sam-cli
  ```

- **Java 21** (JDK instalado)
  ```bash
  java -version  # Deve mostrar versão 21.x
  ```

- **Maven** 3.6 ou superior
  ```bash
  mvn -version
  ```

### **Permissões AWS**

Sua credencial AWS precisa ter permissões para criar:
- Lambda Functions
- API Gateway
- DynamoDB Tables
- EventBridge Rules
- Step Functions
- S3 Buckets
- SES (Simple Email Service)
- Cognito User Pools
- IAM Roles e Policies
- CloudWatch Logs
- SQS Queues

### **Configurações Opcionais**

- **Mailtrap Account** (para notificações críticas)
  - API Token armazenado no Systems Manager Parameter Store como `/feedback/mailtrap-token`
  - Configurar no arquivo `template.yaml`

- **SES Email Verificado** (para relatórios semanais)
  ```bash
  aws ses verify-email-identity --email-address seu@email.com
  ```

---

## 🚀 Quick Start

```bash
# 1. Compilar projeto
mvn clean package

# 2. Build e Deploy
sam build
sam deploy --guided

# 3. Criar usuário de teste
cd cognito-scripts
.\manage-users.ps1 -Action create -Email "dev@test.com" -Password "Dev@Test123" -Name "Dev User"

# 4. Testar API
.\test-api-with-auth.ps1 -Action insert -Email "dev@test.com" -Password "Dev@Test123"
.\test-api-with-auth.ps1 -Action list -Email "dev@test.com" -Password "Dev@Test123"

# 5. Verificar logs
cd ..
aws logs tail /aws/lambda/insert-feedback --follow
```

✅ **Pronto!** Seu sistema serverless está funcionando.

---

## 🛠️ Instalação e Configuração

### 1. Build Local

```bash
# Na raiz do projeto
cd techchallenge-feedback-lambdas

# Compilar todos os módulos
mvn clean package

# Verificar sucesso (deve mostrar BUILD SUCCESS para todos)
```

**Output esperado:**
```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] ------------------------------------------------------------------------
[INFO] techchallenge-feedback ......................... SUCCESS [  0.234 s]
[INFO] insert-feedback ................................ SUCCESS [  3.456 s]
[INFO] list-feedbacks ................................. SUCCESS [  2.123 s]
[INFO] send-queue ..................................... SUCCESS [  2.234 s]
[INFO] notify-critical ................................ SUCCESS [  2.345 s]
[INFO] generate-weekly-report ......................... SUCCESS [  2.456 s]
[INFO] notify-report .................................. SUCCESS [  2.567 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

### 2. Deploy AWS (Automatizado)

**Windows:**

```powershell
.\build-and-deploy.ps1
```

**Linux/Mac:**

```bash
chmod +x build-and-deploy.sh
./build-and-deploy.sh
```

O script automatizado executa:
1. ✅ Compilação Maven (`mvn clean package`)
2. ✅ Build SAM (`sam build`)
3. ✅ Deploy SAM (`sam deploy`)
4. ✅ Captura e exibe URLs e IDs do Cognito
5. ✅ Salva configurações em arquivo `.env`

### 3. Deploy AWS (Manual)

#### **Passo 1: Build SAM**

```bash
sam build
```

#### **Passo 2: Deploy Guiado (Primeira Vez)**

```bash
sam deploy --guided
```

**Responda as perguntas:**

| Pergunta | Resposta Sugerida |
|----------|-------------------|
| Stack Name | `techchallenge-feedback-lambdas` |
| AWS Region | `us-east-1` (ou sua preferida) |
| Confirm changes before deploy | `Y` |
| Allow SAM CLI IAM role creation | `Y` |
| Disable rollback | `N` |
| InsertFeedbackFunction has no authorization. Continue? | `Y` |
| ListFeedbacksFunction has no authorization. Continue? | `Y` |
| Save arguments to samconfig.toml | `Y` |

#### **Passo 3: Capturar Outputs**

```bash
aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query 'Stacks[0].Outputs' \
  --output table
```

**Outputs importantes:**

| Output Key | Descrição |
|------------|-----------|
| `FeedbackApiUrl` | URL do endpoint POST /feedback |
| `ListFeedbacksApiUrl` | URL do endpoint GET /feedbacks |
| `CognitoUserPoolId` | ID do User Pool (ex: us-east-1_xxxxxxx) |
| `CognitoUserPoolClientId` | ID do Client (ex: xxxxxxxxxxxxxxxxxx) |
| `CognitoUserPoolArn` | ARN do User Pool |

#### **Deploys Subsequentes**

```bash
# Build e deploy rápido (sem confirmações)
sam build && sam deploy --no-confirm-changeset
```

---

## 🔐 Autenticação e Segurança (Cognito)

### Como Funciona

O sistema usa **AWS Cognito** para autenticação JWT nos endpoints da API:

```
┌─────────────┐
│   Cliente   │
└──────┬──────┘
       │ 1. Login (email + senha)
       ↓
┌────────────────────┐
│  Cognito Client    │ ← USER_PASSWORD_AUTH
└──────┬─────────────┘
       │ 2. Validar credenciais
       ↓
┌────────────────────┐
│  Cognito User Pool │
└──────┬─────────────┘
       │ 3. Retornar tokens JWT
       │    • IdToken (para API)
       │    • AccessToken
       │    • RefreshToken
       ↓
┌─────────────┐
│   Cliente   │ ← Salvar IdToken
└──────┬──────┘
       │ 4. POST /feedback
       │    Authorization: Bearer <IdToken>
       ↓
┌─────────────────────┐
│   API Gateway       │
│   (Authorizer)      │
└──────┬──────────────┘
       │ 5. Validar JWT com Cognito
       ↓
┌─────────────────────┐
│  Cognito Authorizer │ ← Valida assinatura, expiração
└──────┬──────────────┘
       │
       ├─ ✅ Token válido → Invoca Lambda
       │
       └─ ❌ Token inválido → 401 Unauthorized
```

### Gerenciamento de Usuários

#### **Criar Usuário**

```powershell
cd cognito-scripts

.\manage-users.ps1 -Action create `
  -Email "usuario@example.com" `
  -Password "SenhaForte@123" `
  -Name "Nome do Usuário"
```

**Requisitos de senha:**
- ✅ Mínimo 8 caracteres
- ✅ Pelo menos 1 letra maiúscula
- ✅ Pelo menos 1 letra minúscula
- ✅ Pelo menos 1 número
- ✅ Pelo menos 1 caractere especial (`!@#$%^&*`)

**Exemplo válido:** `FiapTeste@123`

#### **Obter Token (Login)**

```powershell
.\manage-users.ps1 -Action login `
  -Email "usuario@example.com" `
  -Password "SenhaForte@123"
```

**Output:**
```
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

#### **Listar Usuários**

```powershell
.\manage-users.ps1 -Action list
```

#### **Deletar Usuário**

```powershell
.\manage-users.ps1 -Action delete -Email "usuario@example.com"
```

### Ciclo de Vida dos Tokens

| Token | Validade | Uso |
|-------|----------|-----|
| **IdToken** | 1 hora | Header `Authorization: Bearer <token>` nas chamadas à API |
| **AccessToken** | 1 hora | Operações com recursos do Cognito (gerenciamento de usuário) |
| **RefreshToken** | 30 dias | Renovar IdToken e AccessToken sem reautenticar |

**Renovar token expirado:**

```powershell
# Executar login novamente para obter novo IdToken
.\manage-users.ps1 -Action login -Email "usuario@example.com" -Password "SenhaForte@123"
```

### Checklist de Segurança

- ✅ **JWT Token Validation**: API Gateway valida automaticamente assinatura e expiração
- ✅ **Password Policy**: Senha forte obrigatória (8+ chars, complexidade)
- ✅ **Email Verification**: Auto-verificação de email habilitada
- ✅ **Account Recovery**: Recuperação via email verificado
- ✅ **User Enumeration Prevention**: Não revela se usuário existe nos erros
- ✅ **Token Revocation**: Suporte para revogar tokens comprometidos
- ✅ **Short-Lived Tokens**: IdToken/AccessToken expiram em 1 hora
- ✅ **Refresh Token Rotation**: RefreshToken válido por 30 dias
- ✅ **HTTPS Only**: Toda comunicação via TLS 1.2+
- ✅ **CORS Protection**: Configurado no API Gateway

---

## 🧪 Testando o Sistema

### 1. Scripts PowerShell

#### **Inserir Feedback**

```powershell
cd cognito-scripts

.\test-api-with-auth.ps1 -Action insert `
  -Email "usuario@example.com" `
  -Password "SenhaForte@123"
```

**Payload enviado:**
```json
{
  "customerName": "Cliente Teste",
  "rating": 5,
  "comment": "Excelente serviço!",
  "category": "Atendimento"
}
```

**Resposta esperada (200 OK):**
```json
{
  "feedbackId": "abc123-def456-ghi789",
  "customerName": "Cliente Teste",
  "rating": 5,
  "comment": "Excelente serviço!",
  "category": "Atendimento",
  "createdAt": "2026-01-09T10:30:00Z"
}
```

#### **Listar Feedbacks**

```powershell
.\test-api-with-auth.ps1 -Action list `
  -Email "usuario@example.com" `
  -Password "SenhaForte@123"
```

**Resposta esperada:**
```json
{
  "feedbacks": [
    {
      "feedbackId": "abc123-def456-ghi789",
      "customerName": "Cliente Teste",
      "rating": 5,
      "category": "Atendimento",
      "createdAt": "2026-01-09T10:30:00Z"
    }
  ],
  "count": 1
}
```

### 2. Postman

#### **Importar Collection**

1. Abra o Postman
2. Clique em **Import**
3. Selecione `postman/postman_collection.json`
4. Collection "Tech Challenge - Feedbacks API" será importada

#### **Configurar Variáveis**

1. Clique na collection → **Variables**
2. Configure:

| Variável | Como Obter | Exemplo |
|----------|------------|---------|
| `api_url` | CloudFormation Output `FeedbackApiUrl` | `https://abc123.execute-api.us-east-1.amazonaws.com/Prod` |
| `list_api_url` | CloudFormation Output `ListFeedbacksApiUrl` | (mesma base URL) |
| `user_pool_id` | CloudFormation Output `CognitoUserPoolId` | `us-east-1_Abc123Xyz` |
| `client_id` | CloudFormation Output `CognitoUserPoolClientId` | `1a2b3c4d5e6f7g8h9i0j` |
| `username` | Email criado com `manage-users.ps1` | `dev@test.com` |
| `password` | Senha definida | `Dev@Test123` |

**Como obter valores:**

```powershell
# API URLs
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackApiUrl'].OutputValue" `
  --output text

# Cognito User Pool ID
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolId'].OutputValue" `
  --output text

# Cognito Client ID
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolClientId'].OutputValue" `
  --output text
```

3. Clique em **Save**

#### **Executar Testes**

**Ordem de execução:**

1. **"1. Get JWT Token"** - Obter autenticação (token salvo automaticamente em variável)
2. **"2. Insert Feedback - Positivo"** - Criar feedback positivo (rating 5)
3. **"3. Insert Feedback - Crítico"** - Criar feedback crítico (rating 1) → Dispara notificação
4. **"4. List Feedbacks"** - Listar todos os feedbacks

**Cenários disponíveis:**

- ✅ Feedback Positivo (rating 4-5)
- 🟡 Feedback Neutro (rating 3)
- ❌ Feedback Crítico (rating 1-2) → Dispara fluxo de notificação

### 3. cURL

#### **Obter Token**

```bash
# Obter IDs do Cognito
USER_POOL_ID=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolId'].OutputValue" \
  --output text)

CLIENT_ID=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolClientId'].OutputValue" \
  --output text)

# Autenticar
TOKEN=$(aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id $CLIENT_ID \
  --auth-parameters USERNAME=dev@test.com,PASSWORD=Dev@Test123 \
  --query 'AuthenticationResult.IdToken' \
  --output text)

echo $TOKEN
```

#### **Inserir Feedback**

```bash
# Obter URL da API
API_URL=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackApiUrl'].OutputValue" \
  --output text)

# Enviar feedback
curl -X POST $API_URL \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "customerName": "Teste cURL",
    "rating": 5,
    "comment": "Teste via cURL funcionou!",
    "category": "TESTE"
  }'
```

#### **Listar Feedbacks**

```bash
# Obter URL
LIST_URL=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='ListFeedbacksApiUrl'].OutputValue" \
  --output text)

# Listar com filtros
curl -X GET "$LIST_URL?startDate=2026-01-01&endDate=2026-12-31" \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Step Functions

#### **Testar Geração de Relatório Manualmente**

```bash
# Obter ARN da State Machine
STATE_MACHINE_ARN=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackProcessingStateMachine'].OutputValue" \
  --output text)

# Iniciar execução
aws stepfunctions start-execution \
  --state-machine-arn $STATE_MACHINE_ARN \
  --name "test-execution-$(date +%s)" \
  --input '{}'
```

#### **Verificar Execução**

```bash
# Listar execuções recentes
aws stepfunctions list-executions \
  --state-machine-arn $STATE_MACHINE_ARN \
  --max-results 5

# Ver detalhes de uma execução
aws stepfunctions describe-execution \
  --execution-arn arn:aws:states:us-east-1:123456789012:execution:feedback-processing:test-execution-123
```

#### **Verificar Logs**

```bash
# Logs de cada Lambda no fluxo
aws logs tail /aws/lambda/list-feedbacks --follow
aws logs tail /aws/lambda/generate-weekly-report --follow
aws logs tail /aws/lambda/notify-report --follow
```

---

## 📊 Monitoramento e Logs

### **CloudWatch Logs**

#### **Ver Logs em Tempo Real**

```bash
# Lambda insert-feedback
aws logs tail /aws/lambda/insert-feedback --follow

# Lambda send-queue
aws logs tail /aws/lambda/send-queue --follow

# Lambda notify-critical
aws logs tail /aws/lambda/notify-critical --follow

# Lambda list-feedbacks
aws logs tail /aws/lambda/list-feedbacks --follow

# Lambda generate-weekly-report
aws logs tail /aws/lambda/generate-weekly-report --follow

# Lambda notify-report
aws logs tail /aws/lambda/notify-report --follow

# API Gateway
aws logs tail /aws/apigateway/techchallenge-feedback --follow
```

#### **Logs de Período Específico**

```bash
# Últimos 30 minutos
aws logs tail /aws/lambda/insert-feedback --since 30m

# Últimas 2 horas
aws logs tail /aws/lambda/notify-critical --since 2h

# Filtrar por palavra-chave
aws logs tail /aws/lambda/send-queue --filter-pattern "ERROR"
```

### **Métricas CloudWatch**

#### **Ver Invocações**

```bash
# Invocações da última hora
aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Invocations \
  --dimensions Name=FunctionName,Value=insert-feedback \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum
```

#### **Ver Erros**

```bash
# Erros da última hora
aws cloudwatch get-metric-statistics \
  --namespace AWS/Lambda \
  --metric-name Errors \
  --dimensions Name=FunctionName,Value=insert-feedback \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Sum
```

### **DynamoDB**

#### **Consultar Feedbacks**

```bash
# Listar todos os feedbacks (limitado)
aws dynamodb scan --table-name FeedbacksTable --limit 10

# Buscar feedback específico
aws dynamodb get-item \
  --table-name FeedbacksTable \
  --key '{"feedbackId": {"S": "abc123-def456"}}'

# Contar total de itens
aws dynamodb scan --table-name FeedbacksTable --select COUNT
```

#### **Verificar Streams**

```bash
# Descrever tabela e ver Stream ARN
aws dynamodb describe-table --table-name FeedbacksTable \
  --query 'Table.LatestStreamArn'
```

### **S3 Bucket**

#### **Listar Relatórios**

```bash
# Obter nome do bucket
BUCKET_NAME=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='ReportsBucketName'].OutputValue" \
  --output text)

# Listar relatórios
aws s3 ls s3://$BUCKET_NAME/reports/
```

#### **Baixar Relatório**

```bash
# Baixar último relatório
aws s3 cp s3://$BUCKET_NAME/reports/weekly-report-2026-01-09.txt ./
```

### **Dead Letter Queue (DLQ)**

#### **Verificar Mensagens na DLQ**

```bash
# Obter URL da DLQ
DLQ_URL=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackDLQUrl'].OutputValue" \
  --output text)

# Verificar mensagens
aws sqs receive-message --queue-url $DLQ_URL --max-number-of-messages 10
```

**⚠️ Mensagens na DLQ indicam falhas que precisam investigação!**

### **Script de Monitoramento Completo**

```powershell
# Windows PowerShell
# Salvar como monitor.ps1

$stackName = "techchallenge-feedback-lambdas"

Write-Host "`n=== MONITORAMENTO DO SISTEMA ===" -ForegroundColor Cyan

# Invocações das Lambdas (última hora)
Write-Host "`n📊 Invocações (última hora):" -ForegroundColor Yellow
@("insert-feedback", "send-queue", "notify-critical", "list-feedbacks", "generate-weekly-report", "notify-report") | ForEach-Object {
    $count = (aws cloudwatch get-metric-statistics `
        --namespace AWS/Lambda `
        --metric-name Invocations `
        --dimensions Name=FunctionName,Value=$_ `
        --start-time ((Get-Date).AddHours(-1).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss")) `
        --end-time ((Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss")) `
        --period 3600 `
        --statistics Sum `
        --query 'Datapoints[0].Sum' `
        --output text)
    
    if ($count -eq "None") { $count = 0 }
    Write-Host "  $_: $count" -ForegroundColor White
}

# Feedbacks no DynamoDB
Write-Host "`n📝 Total de Feedbacks:" -ForegroundColor Yellow
$feedbackCount = (aws dynamodb scan --table-name FeedbacksTable --select COUNT --query 'Count' --output text)
Write-Host "  $feedbackCount feedbacks" -ForegroundColor White

# Mensagens na DLQ
Write-Host "`n⚠️  Dead Letter Queue:" -ForegroundColor Yellow
$dlqUrl = (aws cloudformation describe-stacks `
    --stack-name $stackName `
    --query "Stacks[0].Outputs[?OutputKey=='FeedbackDLQUrl'].OutputValue" `
    --output text)

if ($dlqUrl) {
    $dlqCount = (aws sqs get-queue-attributes `
        --queue-url $dlqUrl `
        --attribute-names ApproximateNumberOfMessages `
        --query 'Attributes.ApproximateNumberOfMessages' `
        --output text)
    
    if ($dlqCount -gt 0) {
        Write-Host "  $dlqCount mensagens (INVESTIGAR!)" -ForegroundColor Red
    } else {
        Write-Host "  0 mensagens (OK)" -ForegroundColor Green
    }
}

# Relatórios no S3
Write-Host "`n📦 Relatórios no S3:" -ForegroundColor Yellow
$bucketName = (aws cloudformation describe-stacks `
    --stack-name $stackName `
    --query "Stacks[0].Outputs[?OutputKey=='ReportsBucketName'].OutputValue" `
    --output text)

if ($bucketName) {
    $reportCount = (aws s3 ls s3://$bucketName/reports/ | Measure-Object).Count
    Write-Host "  $reportCount relatórios" -ForegroundColor White
}

Write-Host "`n=== FIM ===" -ForegroundColor Cyan
```

**Executar:**

```powershell
.\monitor.ps1
```

---

## ✅ Testes Unitários

O projeto possui **27 testes automatizados** cobrindo todas as Lambdas.

### **Executar Todos os Testes**

```bash
# Na raiz do projeto
mvn test
```

### **Executar Testes de uma Lambda Específica**

```bash
# insert-feedback
mvn test -pl insert-feedback

# list-feedbacks
mvn test -pl list-feedbacks

# send-queue
mvn test -pl send-queue

# notify-critical
mvn test -pl notify-critical

# generate-weekly-report
mvn test -pl generate-weekly-report

# notify-report
mvn test -pl notify-report
```

### **Coverage Report**

```bash
# Gerar relatório de cobertura
mvn jacoco:report

# Abrir relatório HTML
# target/site/jacoco/index.html
```

### **Estrutura dos Testes**

Cada Lambda possui testes para:
- ✅ Casos de sucesso (happy path)
- ✅ Validação de entrada (campos obrigatórios)
- ✅ Tratamento de erros
- ✅ Edge cases

**Exemplo:**

```java
// insert-feedback/src/test/java/lambda/InsertFeedbackFunctionTest.java

@Test
void testHandleRequest_Success() {
    // Testa inserção válida
}

@Test
void testHandleRequest_MissingFields() {
    // Testa campos obrigatórios
}

@Test
void testHandleRequest_InvalidRating() {
    // Testa validação de rating (1-5)
}

@Test
void testHandleRequest_DynamoDBException() {
    // Testa tratamento de erro DynamoDB
}
```

---

## 📚 Referência Rápida

### **Comandos Mais Usados**

```bash
# ============== BUILD E DEPLOY ==============
mvn clean package                          # Compilar projeto
sam build                                  # Build SAM
sam deploy --guided                        # Deploy guiado (primeira vez)
sam build && sam deploy                    # Build e deploy rápido

# ============== COGNITO ==============
cd cognito-scripts
.\manage-users.ps1 -Action create -Email "user@test.com" -Password "Pass@123" -Name "User"
.\manage-users.ps1 -Action login -Email "user@test.com" -Password "Pass@123"
.\manage-users.ps1 -Action list
.\manage-users.ps1 -Action delete -Email "user@test.com"

# ============== TESTES ==============
.\test-api-with-auth.ps1 -Action insert -Email "user@test.com" -Password "Pass@123"
.\test-api-with-auth.ps1 -Action list -Email "user@test.com" -Password "Pass@123"
mvn test                                   # Testes unitários

# ============== CLOUDWATCH LOGS ==============
aws logs tail /aws/lambda/insert-feedback --follow
aws logs tail /aws/lambda/send-queue --follow
aws logs tail /aws/lambda/notify-critical --follow
aws logs tail /aws/lambda/list-feedbacks --follow
aws logs tail /aws/lambda/generate-weekly-report --follow
aws logs tail /aws/lambda/notify-report --follow

# ============== DYNAMODB ==============
aws dynamodb scan --table-name FeedbacksTable --limit 10
aws dynamodb scan --table-name FeedbacksTable --select COUNT

# ============== STEP FUNCTIONS ==============
aws stepfunctions list-executions --state-machine-arn <ARN>
aws stepfunctions describe-execution --execution-arn <ARN>
aws stepfunctions start-execution --state-machine-arn <ARN> --input '{}'

# ============== S3 RELATÓRIOS ==============
aws s3 ls s3://feedback-reports-<ACCOUNT_ID>/reports/
aws s3 cp s3://feedback-reports-<ACCOUNT_ID>/reports/weekly-report.txt ./

# ============== CLOUDFORMATION ==============
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas --query 'Stacks[0].Outputs' --output table
aws cloudformation delete-stack --stack-name techchallenge-feedback-lambdas

# ============== LAMBDA ==============
aws lambda invoke --function-name insert-feedback --payload file://test-payloads/insert-feedback.json output.json
aws lambda invoke --function-name list-feedbacks --payload file://test-payloads/list-feedbacks.json output.json
```

### **Atalhos PowerShell**

```powershell
# Criar aliases permanentes (adicionar ao $PROFILE)
function deploy { sam build; sam deploy --no-confirm-changeset }
function logs-insert { aws logs tail /aws/lambda/insert-feedback --follow }
function logs-notify { aws logs tail /aws/lambda/notify-critical --follow }
function db-count { aws dynamodb scan --table-name FeedbacksTable --select COUNT }
function test-insert { cd cognito-scripts; .\test-api-with-auth.ps1 -Action insert -Email "dev@test.com" -Password "Dev@Test123"; cd .. }
```

---

## 🔧 Troubleshooting

### **Deploy Falha**

#### ❌ "Unable to upload artifact... Access Denied"

**Solução:**
```bash
# Verificar credenciais AWS
aws sts get-caller-identity

# Configurar credenciais corretas
aws configure

# Garantir permissões S3
aws iam list-attached-user-policies --user-name <seu-usuario>
```

#### ❌ "Stack ... already exists"

**Solução:**
```bash
# Deletar stack antiga
aws cloudformation delete-stack --stack-name techchallenge-feedback-lambdas

# Aguardar conclusão
aws cloudformation wait stack-delete-complete --stack-name techchallenge-feedback-lambdas

# Tentar deploy novamente
sam deploy --guided
```

#### ❌ "BUILD FAILURE" no Maven

**Solução:**
```bash
# Verificar Java 21
java -version

# Limpar cache Maven
mvn clean

# Compilar com debug
mvn clean package -X
```

---

### **Cognito**

#### ❌ "Não foi possível obter os IDs do Cognito"

**Solução:**
```powershell
# Verificar se a stack foi criada
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas

# Listar outputs manualmente
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query 'Stacks[0].Outputs' `
  --output table
```

#### ❌ "InvalidParameterException" ao criar usuário

**Solução:** Verificar requisitos de senha:
- Mínimo 8 caracteres
- Pelo menos 1 maiúscula
- Pelo menos 1 minúscula
- Pelo menos 1 número
- Pelo menos 1 caractere especial

**Exemplo válido:** `Fiap@Test123`

#### ❌ "401 Unauthorized" na API

**Causas possíveis:**

1. **Token expirado** (validade: 1 hora)
   ```powershell
   # Obter novo token
   .\manage-users.ps1 -Action login -Email "user@test.com" -Password "Pass@123"
   ```

2. **Token inválido no header**
   ```bash
   # Formato correto
   Authorization: Bearer <IdToken_completo>
   
   # Formato ERRADO
   Authorization: <IdToken>  # Faltou "Bearer"
   ```

3. **Usando AccessToken em vez de IdToken**
   ```bash
   # Use APENAS o IdToken retornado pelo login
   ```

---

### **API Gateway**

#### ❌ "403 Forbidden"

**Solução:**
```bash
# Verificar se o authorizer está configurado
aws apigateway get-authorizers --rest-api-id <api-id>

# Redeployar API
sam build && sam deploy --no-confirm-changeset
```

#### ❌ "Internal Server Error (500)"

**Solução:**
```bash
# Ver logs da Lambda
aws logs tail /aws/lambda/insert-feedback --since 10m

# Ver logs do API Gateway
aws logs tail /aws/apigateway/techchallenge-feedback --since 10m
```

---

### **DynamoDB**

#### ❌ "ResourceNotFoundException: Requested resource not found"

**Solução:**
```bash
# Verificar se a tabela existe
aws dynamodb list-tables | grep FeedbacksTable

# Verificar status da stack
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas --query 'Stacks[0].StackStatus'

# Se necessário, redeployar
sam deploy
```

#### ❌ "ProvisionedThroughputExceededException"

**Solução:** A tabela usa **on-demand** billing, então isso não deveria acontecer. Se ocorrer:
```bash
# Verificar modo de billing
aws dynamodb describe-table --table-name FeedbacksTable --query 'Table.BillingModeSummary'
```

---

### **Step Functions**

#### ❌ "Execution failed"

**Solução:**
```bash
# Ver detalhes da falha
aws stepfunctions describe-execution --execution-arn <execution-arn>

# Ver logs das Lambdas envolvidas
aws logs tail /aws/lambda/list-feedbacks --since 30m
aws logs tail /aws/lambda/generate-weekly-report --since 30m
aws logs tail /aws/lambda/notify-report --since 30m
```

#### ❌ "TaskTimedOut"

**Solução:**
```yaml
# No template.yaml, aumentar timeout
Properties:
  Timeout: 60  # Aumentar de 30 para 60 segundos
```

---

### **Notificações**

#### ❌ "Emails não estão sendo enviados"

**Para notify-critical (Mailtrap):**
```bash
# Verificar se token está configurado
aws ssm get-parameter --name /feedback/mailtrap-token --with-decryption

# Verificar logs
aws logs tail /aws/lambda/notify-critical --since 30m

# Testar Mailtrap API manualmente
curl -X POST https://send.api.mailtrap.io/api/send \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"from":{"email":"test@test.com"},"to":[{"email":"dest@test.com"}],"subject":"Test","text":"Test"}'
```

**Para notify-report (SES):**
```bash
# Verificar se email está verificado
aws ses list-verified-email-addresses

# Verificar se está no sandbox (limitação de emails)
aws ses get-account-sending-enabled

# Verificar logs
aws logs tail /aws/lambda/notify-report --since 30m
```

---

### **Testes Unitários**

#### ❌ "Tests compilation failure"

**Solução:**
```bash
# Limpar e recompilar
mvn clean test-compile

# Verificar dependências
mvn dependency:tree
```

#### ❌ "NoClassDefFoundError"

**Solução:**
```bash
# Atualizar dependências
mvn clean install -U
```

---

### **Dead Letter Queue (DLQ)**

#### ⚠️ "Mensagens na DLQ"

**Investigar:**
```bash
# Obter URL da DLQ
DLQ_URL=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackDLQUrl'].OutputValue" \
  --output text)

# Ler mensagens
aws sqs receive-message --queue-url $DLQ_URL --max-number-of-messages 10 > dlq-messages.json

# Analisar mensagem
cat dlq-messages.json
```

**Causas comuns:**
- Lambda com timeout muito curto
- Erro não tratado no código
- Dependência externa indisponível
- Problemas de permissão IAM

---

## 🗑️ Limpeza de Recursos

### **Deletar Stack Completa**

```bash
# ATENÇÃO: Isso deletará TODOS os recursos (Lambdas, DynamoDB, S3, etc.)
aws cloudformation delete-stack --stack-name techchallenge-feedback-lambdas

# Aguardar conclusão
aws cloudformation wait stack-delete-complete --stack-name techchallenge-feedback-lambdas

# Verificar se foi deletada
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas
# Deve retornar: An error occurred (ValidationError) when calling the DescribeStacks operation: Stack with id techchallenge-feedback-lambdas does not exist
```

### **Deletar Apenas Usuários Cognito**

```powershell
cd cognito-scripts

# Listar usuários
.\manage-users.ps1 -Action list

# Deletar usuário específico
.\manage-users.ps1 -Action delete -Email "usuario@example.com"
```

### **Esvaziar e Deletar Bucket S3 Manualmente**

```bash
# Obter nome do bucket
BUCKET_NAME=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='ReportsBucketName'].OutputValue" \
  --output text)

# Listar objetos
aws s3 ls s3://$BUCKET_NAME --recursive

# Esvaziar bucket (CUIDADO!)
aws s3 rm s3://$BUCKET_NAME --recursive

# Deletar bucket
aws s3 rb s3://$BUCKET_NAME
```

### **Limpar CloudWatch Logs**

```bash
# Listar log groups
aws logs describe-log-groups --log-group-name-prefix /aws/lambda/

# Deletar log group específico
aws logs delete-log-group --log-group-name /aws/lambda/insert-feedback
aws logs delete-log-group --log-group-name /aws/lambda/send-queue
aws logs delete-log-group --log-group-name /aws/lambda/notify-critical
aws logs delete-log-group --log-group-name /aws/lambda/list-feedbacks
aws logs delete-log-group --log-group-name /aws/lambda/generate-weekly-report
aws logs delete-log-group --log-group-name /aws/lambda/notify-report
```

### **Purgar Dead Letter Queue**

```bash
# Obter URL da DLQ
DLQ_URL=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback-lambdas \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackDLQUrl'].OutputValue" \
  --output text)

# Purgar mensagens
aws sqs purge-queue --queue-url $DLQ_URL
```

---

## 💡 Melhorias Futuras

### **Autenticação**

- [ ] Adicionar Google OAuth via Cognito Identity Providers
- [ ] Implementar MFA (Multi-Factor Authentication)
- [ ] Adicionar suporte para login social (Facebook, GitHub)
- [ ] Implementar refresh token rotation automático

### **Segurança**

- [ ] Implementar rate limiting no API Gateway
- [ ] Adicionar WAF (Web Application Firewall)
- [ ] Criptografia de dados sensíveis no DynamoDB
- [ ] Rotação automática de secrets (Mailtrap token)
- [ ] Implementar AWS X-Ray para tracing distribuído

### **Funcionalidades**

- [ ] API de busca por texto completo (ElasticSearch/OpenSearch)
- [ ] Sistema de categorização automática com ML
- [ ] Análise de sentimento dos comentários
- [ ] Dashboard em tempo real (QuickSight ou CloudWatch Dashboard)
- [ ] Exportação de relatórios em PDF e Excel
- [ ] Integração com Slack/Teams para notificações

### **Performance**

- [ ] Implementar cache com ElastiCache/DAX
- [ ] Otimizar queries DynamoDB com índices adicionais
- [ ] Implementar batching para relatórios grandes
- [ ] Usar Lambda Layers para dependências compartilhadas

### **Observabilidade**

- [ ] Dashboard CloudWatch customizado
- [ ] Alarmes CloudWatch para:
  - Taxa de erros > 5%
  - Latência > 3s
  - Mensagens na DLQ
  - Custo mensal > threshold
- [ ] Distributed tracing com AWS X-Ray
- [ ] Metrics detalhados por categoria de feedback

### **DevOps**

- [ ] CI/CD com GitHub Actions / CodePipeline
- [ ] Testes de integração automatizados
- [ ] Blue/Green deployment
- [ ] Ambientes separados (dev, staging, prod)
- [ ] Infrastructure as Code com CDK (alternativa ao SAM)

### **Escalabilidade**

- [ ] Particionar DynamoDB por período (sharding)
- [ ] Implementar event sourcing para auditoria
- [ ] Usar SQS FIFO para garantir ordem de processamento
- [ ] Implementar circuit breaker para integrações externas

---

## 📎 Recursos Adicionais

### **Documentação Auxiliar**

- [docs/TESTES_REALIZADOS.md](docs/TESTES_REALIZADOS.md) - Histórico detalhado de testes executados e resultados
- [cognito-scripts/README_SCRIPTS.md](cognito-scripts/README_SCRIPTS.md) - Documentação técnica dos scripts PowerShell

### **Collections e Payloads**

- [postman/postman_collection.json](postman/postman_collection.json) - Collection Postman completa
- [test-payloads/](test-payloads/) - Payloads JSON para testes manuais
- [events/](events/) - Eventos para testes SAM local

### **Arquitetura**

- [template.yaml](template.yaml) - Infraestrutura completa AWS SAM
- [statemachine/feedback-processing.asl.json](statemachine/feedback-processing.asl.json) - Definição Step Functions

### **Links Úteis**

- [AWS SAM Documentation](https://docs.aws.amazon.com/serverless-application-model/)
- [AWS Lambda Developer Guide](https://docs.aws.amazon.com/lambda/)
- [Amazon DynamoDB Developer Guide](https://docs.aws.amazon.com/dynamodb/)
- [AWS Cognito Documentation](https://docs.aws.amazon.com/cognito/)
- [AWS Step Functions](https://docs.aws.amazon.com/step-functions/)
- [Amazon EventBridge](https://docs.aws.amazon.com/eventbridge/)
- [Maven Documentation](https://maven.apache.org/guides/)

### **Suporte**

Para dúvidas ou problemas:

1. **Verifique a seção [Troubleshooting](#-troubleshooting)**
2. **Consulte os logs do CloudWatch**
3. **Revise a documentação auxiliar**
4. **Abra uma issue no repositório** (se aplicável)

---

## 📄 Licença

Este projeto foi desenvolvido como parte do **Tech Challenge FIAP - Fase 4**.

**Tecnologias:** Java 21 | Maven | AWS SAM | AWS Lambda | DynamoDB | Cognito | EventBridge | Step Functions | S3 | SES

---

<div align="center">

**🚀 Sistema Serverless de Feedbacks**

Desenvolvido com ❤️ para o Tech Challenge FIAP

</div>
