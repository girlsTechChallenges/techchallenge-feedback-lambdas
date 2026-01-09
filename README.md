# 🚀 Tech Challenge - Sistema de Feedbacks Serverless

Este repositório contém uma aplicação **serverless** desenvolvida em **Java 21** com **Maven**, empacotada como funções **AWS Lambda**. O sistema gerencia feedbacks de clientes com notificações automáticas para casos críticos.

## 📋 Visão Geral do Sistema

Este é um **sistema serverless de gerenciamento de feedbacks** que implementa uma arquitetura orientada a eventos na AWS. O sistema possui **6 funções Lambda** conectadas em dois fluxos principais:

### **Fluxo 1: Inserção e Notificação de Feedbacks Críticos**

#### **1. insert-feedback (Ponto de Entrada)**
- Recebe feedbacks via **API Gateway** (POST `/feedback`)
- **Autenticação via AWS Cognito** (Bearer Token JWT)
- Salva o feedback no **DynamoDB** com:
  - `feedbackId` gerado automaticamente (UUID)
  - `pk` = "FEEDBACK" (para consultas via GSI)
  - `createdAt` (timestamp ISO)
  - `descricao`, `nota`, `urgencia`
- Retorna confirmação com `feedbackId` e `createdAt`

#### **2. send-queue (Processador de Eventos)**
- Acionada automaticamente por **DynamoDB Streams** quando novo feedback é inserido
- Analisa se o feedback é crítico:
  - Categoria "Critical" **OU**
  - Rating ≤ 2
- Publica evento no **EventBridge** com campo `isCritical`

#### **3. notify-critical (Notificador de Críticos)**
- Acionada pelo **EventBridge** apenas para feedbacks críticos (`isCritical: true`)
- Envia e-mail via **API Mailtrap** para equipe de suporte
- Formata notificação com todos os dados do feedback

### **Fluxo 2: Geração Automática de Relatórios Semanais**

#### **4. list-feedbacks (Consulta de Feedbacks)**
- Endpoint: **GET `/feedbacks`** via API Gateway
- Consulta feedbacks no **DynamoDB** com filtros por data e urgência
- Paginação configurável (padrão: 100 itens)
- Também invocada pela **Step Function** para gerar relatórios

#### **5. generate-weekly-report (Gerador de Relatórios)**
- Recebe lista de feedbacks da lambda anterior
- Calcula estatísticas: média de notas, distribuição por urgência, feedbacks por dia
- Gera arquivo de texto formatado
- Salva relatório no **S3 Bucket**
- Retorna chave do arquivo para próxima etapa

#### **6. notify-report (Notificador de Relatórios)**
- Lê relatório salvo no **S3**
- Envia por e-mail via **Amazon SES**
- Destinatário configurável via variável de ambiente

### **Orquestração com Step Functions**
- **EventBridge Rule** dispara semanalmente (domingo 23:00 UTC)
- **Step Function** `feedback-processing` orquestra:
  1. Lista feedbacks → 2. Gera relatório → 3. Envia por e-mail
- Tratamento de erros com retry automático e DLQ

---

## 📂 Estrutura do Projeto

```
techchallenge-feedback/
├── docs/                          [Documentação do projeto]
│   └── TESTES_REALIZADOS.md       [Histórico de testes executados]
├── examples/                      [Exemplos de payloads de teste]
│   ├── response.json              [Exemplo de resposta da API]
│   ├── test-payload.json          [Payload para invoke local]
│   ├── test-post.json             [Exemplo de POST request]
│   └── test2.json                 [Outro exemplo de teste]
├── events/                        [Eventos para testes SAM local]
│   ├── event.json
│   ├── invoke-payload.json
│   └── notify-event.json
├── insert-feedback/               [Lambda: Inserir Feedback]
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── InsertFeedbackFunction.java
│   │   └── test/java/lambda/
│   │       └── InsertFeedbackFunctionTest.java
│   └── target/
├── send-queue/                    [Lambda: Processar DynamoDB Stream]
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── SendQueueFunction.java
│   │   └── test/java/lambda/
│   │       └── SendQueueFunctionTest.java
│   └── target/
├── notify-critical/               [Lambda: Notificar Feedbacks Críticos]
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   ├── FeedbackEvent.java
│   │   │   └── NotifyCriticalFunction.java
│   │   └── test/java/lambda/
│   │       └── NotifyCriticalFunctionTest.java
│   └── target/
├── list-feedbacks/                [Lambda: Listar Feedbacks]
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── ListFeedbacksFunction.java
│   │   └── test/java/lambda/
│   │       └── ListFeedbacksFunctionTest.java
│   └── target/
├── generate-weekly-report/        [Lambda: Gerar Relatório Semanal]
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── GenerateWeeklyReportFunction.java
│   │   └── test/java/lambda/
│   │       └── GenerateWeeklyReportFunctionTest.java
│   └── target/
├── notify-report/                 [Lambda: Enviar Relatório por Email]
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── NotifyReportFunction.java
│   │   └── test/java/lambda/
│   │       └── NotifyReportFunctionTest.java
│   └── target/
├── statemachine/                  [Definição Step Functions]
│   └── feedback-processing.asl.json
├── postman/                       [Testes Postman]
│   └── postman_collection.json    [Collection com todas as APIs]
├── test-payloads/                 [Payloads para testes das Lambdas]
│   ├── insert-feedback.json
│   ├── list-feedbacks.json
│   ├── send-queue.json
│   ├── notify-critical.json
│   ├── generate-weekly-report.json
│   └── notify-report.json
├── pom.xml                        [Build multi-módulo Maven]
├── template.yaml                  [Infraestrutura AWS SAM]
├── samconfig.toml                 [Configurações de deploy]
└── README.md                      [Este arquivo]
```

---


## 📂 Arquivos Principais

- **template.yaml** → Template AWS SAM que declara funções Lambda, permissões e recursos necessários.
- **samconfig.toml** → Configurações de deploy do SAM (gerado automaticamente após primeiro deploy).
- **pom.xml (raiz)** → Build multimódulo Maven que compila todas as 6 Lambdas.

### Pastas de Organização

- **docs/** → Documentação adicional e histórico de testes realizados.
- **postman/** → Collection Postman com todas as requisições prontas para testar as APIs.
- **test-payloads/** → Payloads JSON para testar cada Lambda individualmente via AWS CLI.
- **examples/** → Arquivos JSON de exemplo para referência de estrutura de dados.
- **events/** → Eventos de teste para invocar Lambdas localmente com SAM CLI.

---

## 🏗️ Arquitetura do Sistema

```
┌─────────────┐
│   Cliente   │
└──────┬──────┘
       │ POST /feedback (sem autenticação)
       ↓
┌─────────────────────────┐
│     API Gateway         │
└──────────┬──────────────┘
           │
           ↓
┌──────────────────────┐
│  Lambda: insert      │ ← Handler de entrada
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

═════════════════════════════════════════════════════════════
          FLUXO DE RELATÓRIOS SEMANAIS (NOVO)
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
        │ 1. Invoke
        ↓
┌──────────────────────┐
│  Lambda:             │ ← Consulta feedbacks
│  list-feedbacks      │    GET /feedbacks também
└───────┬──────────────┘
        │ Query
        ↓
┌──────────────────┐
│    DynamoDB      │
│  FeedbacksTable  │
└──────────────────┘
        │
        │ 2. Pass items
        ↓
┌──────────────────────────┐
│  Lambda:                 │ ← Gera estatísticas
│  generate-weekly-report  │
└───────┬──────────────────┘
        │ PutObject
        ↓
┌──────────────────┐
│   S3 Bucket:     │ ← Armazena relatórios
│ feedback-reports │
└───────┬──────────┘
        │
        │ 3. Pass reportKey
        ↓
┌──────────────────────┐
│  Lambda:             │ ← Lê S3 e envia email
│  notify-report       │
└───────┬──────────────┘
        │ SendEmail
        ↓
┌──────────────┐
│  Amazon SES  │ ← Serviço de e-mail AWS
└──────────────┘

          ┌────────────┐
          │  SQS DLQ   │ ← Captura falhas (ambos fluxos)
          └────────────┘
```

### **Recursos AWS Utilizados**
- **API Gateway** (sem autenticação para testes)
- **DynamoDB** com Streams habilitado e Global Secondary Index (pk-createdAt-index)
- **EventBridge** com regra de roteamento para eventos críticos + schedule semanal
- **Step Functions** para orquestração do fluxo de relatórios
- **S3 Bucket** para armazenamento de relatórios
- **Amazon SES** para envio de e-mails de relatórios
- **SQS Dead Letter Queue** para tratamento de falhas
- **CloudWatch Logs** para monitoramento e debugging

### **Tecnologias**
- Java 21
- Maven (arquitetura multi-módulo com 6 lambdas)
- AWS SAM (infraestrutura como código)
- AWS SDK v2 (DynamoDB, S3, SES)
- Jackson 2.17.2 para serialização JSON

---

## ⚙️ Pré-requisitos

- **AWS CLI** configurado com credenciais válidas
- **AWS SAM CLI** (versão 1.x ou superior)
- **Java 21** (JDK instalado)
- **Maven 3.8+**
- **Conta AWS** com permissões para criar recursos Lambda, DynamoDB, API Gateway, S3, SES, Step Functions, etc.

### Verificar instalações:

```bash
java -version    # Deve mostrar Java 21
mvn -version     # Deve mostrar Maven 3.8+
sam --version    # Deve mostrar SAM CLI 1.x+
aws --version    # Deve mostrar AWS CLI
```

---

## � Build e Deploy

### Opção 1: Script Automatizado (Recomendado)

Use os scripts prontos para compilar e fazer deploy de forma automatizada:

#### **Windows (PowerShell)**
```powershell
# Build completo + Deploy
.\build-and-deploy.ps1

# Build sem testes + Deploy
.\build-and-deploy.ps1 -SkipTests

# Apenas Build (sem deploy)
.\build-and-deploy.ps1 -BuildOnly

# Apenas Deploy (pula compilação)
.\build-and-deploy.ps1 -DeployOnly
```

#### **Linux/Mac (Bash)**
```bash
# Dar permissão de execução (primeira vez)
chmod +x build-and-deploy.sh

# Build completo + Deploy
./build-and-deploy.sh

# Build sem testes + Deploy
./build-and-deploy.sh --skip-tests

# Apenas Build (sem deploy)
./build-and-deploy.sh --build-only

# Apenas Deploy (pula compilação)
./build-and-deploy.sh --deploy-only
```

**O que os scripts fazem:**
1. ✅ Verificam se Maven e SAM CLI estão instalados
2. ✅ Compilam todos os 6 módulos Lambda de uma vez
3. ✅ Executam testes unitários (ou pulam se usar `--SkipTests`)
4. ✅ Fazem build com SAM CLI
5. ✅ Fazem deploy automático na AWS
6. ✅ Mostram próximos passos após deploy

---

### Opção 2: Comandos Manuais

Se preferir executar passo a passo:

#### **Build de Todos os Módulos**
```bash
# Na raiz do projeto - compila TODOS os 6 módulos
mvn clean package

# Sem executar testes
mvn clean package -DskipTests

# Apenas executar testes
mvn test

# Com relatório de cobertura
mvn clean test jacoco:report
```

**Saída esperada:**
```
[INFO] Reactor Summary for techchallenge-feedback 1.0:
[INFO]
[INFO] techchallenge-feedback ............................. SUCCESS
[INFO] Lambda Insert Feedback ............................. SUCCESS
[INFO] Lambda Send Queue .................................. SUCCESS
[INFO] Lambda Notify Critical ............................. SUCCESS
[INFO] Lambda List Feedbacks .............................. SUCCESS
[INFO] Lambda Generate Weekly Report ...................... SUCCESS
[INFO] Lambda Notify Report ............................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

#### **Build de um Módulo Específico**
```bash
# Navegar até o módulo
cd insert-feedback
mvn clean package

# Ou executar da raiz com -pl
mvn clean package -pl insert-feedback

# Módulos disponíveis:
# - insert-feedback
# - send-queue
# - notify-critical
# - list-feedbacks
# - generate-weekly-report
# - notify-report
```

#### **Deploy com AWS SAM**
```bash
# Build com SAM (prepara para deploy)
sam build

# Deploy guiado (primeira vez)
sam deploy --guided

# Deploy automático (usa samconfig.toml)
sam deploy

# Deploy sem confirmação
sam deploy --no-confirm-changeset
```

---

### 📊 Verificar Resultado do Deploy

Após o deploy, obtenha as informações do stack:

#### **Bash/Linux**
```bash
# Ver todos os outputs do stack
aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback \
  --query 'Stacks[0].Outputs' \
  --output table

# Obter apenas a URL da API de feedback
aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackApiUrl'].OutputValue" \
  --output text

# Obter apenas a URL da API de listagem
aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback \
  --query "Stacks[0].Outputs[?OutputKey=='ListFeedbacksApiUrl'].OutputValue" \
  --output text

# Obter ARN da Step Function
aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackProcessingStateMachineArn'].OutputValue" \
  --output text
```

#### **PowerShell/Windows**
```powershell
# Ver todos os outputs do stack
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback `
  --query 'Stacks[0].Outputs' `
  --output table

# Obter URLs e salvar em variáveis
$apiUrl = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback `
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackApiUrl'].OutputValue" `
  --output text

$listUrl = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback `
  --query "Stacks[0].Outputs[?OutputKey=='ListFeedbacksApiUrl'].OutputValue" `
  --output text

Write-Host "API Feedback: $apiUrl"
Write-Host "API Listagem: $listUrl"
```

---

## �🚀 Executando o Projeto Completo

### Passo 1: Compilar o Projeto

Na raiz do repositório, execute:

```bash
mvn clean package
```

Este comando irá:
- Compilar todos os 6 módulos (insert-feedback, send-queue, notify-critical, list-feedbacks, generate-weekly-report, notify-report)
- Executar os testes unitários
- Gerar os JARs empacotados com todas as dependências (uber JARs) em cada subdiretório `target/`

**Saída esperada:**
```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for techchallenge-feedback 1.0:
[INFO]
[INFO] techchallenge-feedback ............................. SUCCESS
[INFO] Lambda Insert Feedback ............................. SUCCESS
[INFO] Lambda Send Queue .................................. SUCCESS
[INFO] Lambda Notify Critical ............................. SUCCESS
[INFO] Lambda List Feedbacks .............................. SUCCESS
[INFO] Lambda Generate Weekly Report ...................... SUCCESS
[INFO] Lambda Notify Report ............................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 🚀 Deploy na AWS

Agora que você já sabe como compilar o projeto, vamos fazer o deploy na AWS.

### Passo 1: Verificar o Email no Amazon SES

Antes de fazer o deploy, você precisa verificar o endereço de email que receberá os relatórios:

```bash
# Verificar o email (substitua pelo seu email)
aws ses verify-email-identity --email-address seu-email@exemplo.com
```

**Importante:** Acesse o email e clique no link de verificação enviado pela AWS.

Para verificar se o email foi confirmado:
```bash
aws ses list-verified-email-addresses
```

---

### Passo 2: Validar o Template SAM

```bash
sam validate
```

**Saída esperada:**
```
template.yaml is a valid SAM Template
```

---

### Passo 3: Deploy com SAM

Execute o deploy usando o comando:

```bash
sam deploy --guided
```

Durante o deploy guiado, responda:

| Pergunta | Resposta Recomendada |
|----------|---------------------|
| **Stack Name** | `techchallenge-feedback` |
| **AWS Region** | `us-east-1` |
| **Confirm changes before deploy** | `Y` |
| **Allow SAM CLI IAM role creation** | `Y` |
| **Disable rollback** | `Y` (para debugging; use `N` em produção) |
| **InsertFeedbackFunction may not have authorization defined** | `y` |
| **ListFeedbacksFunction may not have authorization defined** | `y` |
| **Save arguments to samconfig.toml** | `Y` |

**Aguarde o deploy...** (pode levar 3-5 minutos)

**Saída esperada ao final:**
```
Successfully created/updated stack - techchallenge-feedback in us-east-1

CloudFormation outputs from deployed stack
----------------------------------------------------------
Key                 FeedbackApiUrl
Description         URL da API de feedback (sem autenticação)
Value               https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback

Key                 ListFeedbacksApiUrl
Description         URL da API para listar feedbacks
Value               https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks

Key                 FeedbackReportsBucketName
Description         Nome do bucket S3 para relatórios
Value               feedback-reports-techchallenge-feedback-XXXXXXXXXXXX

Key                 FeedbackProcessingStateMachineArn
Description         ARN da Step Function para processamento de feedbacks
Value               arn:aws:states:us-east-1:XXXXXXXXXXXX:stateMachine:feedback-processing
```

**Anote as URLs da API** - você vai precisar delas para os testes!

---

### Passo 4: Configurar Variável de Ambiente (Email do Relatório)

Atualize a função `notify-report` com o email verificado:

```bash
aws lambda update-function-configuration \
  --function-name notify-report \
  --environment "Variables={RECIPIENT_EMAIL=seu-email@exemplo.com}"
```

---

## 🔐 Autenticação com AWS Cognito

O sistema utiliza **AWS Cognito User Pool** para autenticar usuários e proteger os endpoints da API. Todos os requests para `/feedback` e `/feedbacks` requerem um token JWT válido no header `Authorization`.

### Configuração Inicial do Cognito

Após o deploy, o sistema cria automaticamente:
- **Cognito User Pool** para gerenciar usuários
- **User Pool Client** para autenticação
- **API Gateway Authorizer** que valida tokens JWT

### Scripts de Gerenciamento

O projeto inclui scripts PowerShell para facilitar o gerenciamento de usuários. Navegue até a pasta `cognito-scripts`:

```powershell
cd cognito-scripts
```

#### Criar Usuário

```powershell
.\manage-users.ps1 -Action create -Email "usuario@example.com" -Password "SenhaForte123!" -Name "Nome Completo"
```

**Requisitos de senha:**
- Mínimo 8 caracteres
- Pelo menos 1 letra maiúscula
- Pelo menos 1 letra minúscula
- Pelo menos 1 número
- Pelo menos 1 caractere especial (!@#$%^&*)

#### Fazer Login e Obter Token

```powershell
.\manage-users.ps1 -Action login -Email "usuario@example.com" -Password "SenhaForte123!"
```

**Saída:**
```
=== TOKENS ===
IdToken (use este para Authorization header):
eyJraWQiOiJ... [token completo]

AccessToken:
eyJraWQiOiJ... [token completo]

RefreshToken:
eyJjdHkiOiJ... [token completo]

Expira em: 3600 segundos

ℹ IdToken salvo em: .\cognito-token.txt
```

O IdToken é automaticamente salvo em `cognito-token.txt` para facilitar o uso.

#### Listar Usuários

```powershell
.\manage-users.ps1 -Action list
```

#### Deletar Usuário

```powershell
.\manage-users.ps1 -Action delete -Email "usuario@example.com"
```

### Testando API com Autenticação

Use o script de teste automatizado:

#### Inserir Feedback Autenticado

```powershell
.\test-api-with-auth.ps1 -Action insert -Email "usuario@example.com" -Password "SenhaForte123!"
```

#### Listar Feedbacks Autenticados

```powershell
.\test-api-with-auth.ps1 -Action list -Email "usuario@example.com" -Password "SenhaForte123!"
```

### Testes Manuais com cURL/PowerShell

#### PowerShell:

```powershell
# 1. Obter token
$token = Get-Content ".\cognito-scripts\cognito-token.txt" -Raw

# 2. Criar feedback
$apiUrl = "https://sua-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedback"
$body = @{
    descricao = "Feedback autenticado!"
    nota = "5"
    urgencia = "MEDIA"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

Invoke-RestMethod -Uri $apiUrl -Method POST -Headers $headers -Body $body

# 3. Listar feedbacks
$listUrl = "https://sua-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedbacks"
Invoke-RestMethod -Uri "$listUrl?startDate=2026-01-01&endDate=2026-01-31" -Method GET -Headers $headers
```

#### Bash/Linux:

```bash
# 1. Obter token (faça login primeiro com o script PowerShell)
TOKEN=$(cat ./cognito-scripts/cognito-token.txt)

# 2. Criar feedback
curl -X POST "https://sua-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedback" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Feedback autenticado!",
    "nota": "5",
    "urgencia": "MEDIA"
  }'

# 3. Listar feedbacks
curl -X GET "https://sua-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedbacks?startDate=2026-01-01&endDate=2026-01-31" \
  -H "Authorization: Bearer $TOKEN"
```

### Testando sem Autenticação (Erro Esperado)

```powershell
# Tentar criar feedback sem token
$apiUrl = "https://sua-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedback"
Invoke-RestMethod -Uri $apiUrl -Method POST -Body (@{descricao="Teste"} | ConvertTo-Json) -ContentType "application/json"
```

**Resposta esperada (401 Unauthorized):**
```json
{
  "message": "Unauthorized"
}
```

### Renovar Token Expirado

Os tokens IdToken e AccessToken expiram em **1 hora**. O RefreshToken é válido por **30 dias**.

Para obter novos tokens, basta fazer login novamente:

```powershell
.\cognito-scripts\manage-users.ps1 -Action login -Email "usuario@example.com" -Password "SenhaForte123!"
```

### Obter IDs do Cognito

Se precisar dos IDs manualmente:

```powershell
# User Pool ID
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolId'].OutputValue" `
  --output text

# User Pool Client ID
aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback-lambdas `
  --query "Stacks[0].Outputs[?OutputKey=='CognitoUserPoolClientId'].OutputValue" `
  --output text
```

### Usando Postman

1. **Importe a collection**: `postman/postman_collection.json`

2. **Configure as variáveis** (já vêm pré-configuradas nos exemplos):
   - `user_pool_id`: ID do Cognito User Pool  
   - `client_id`: ID do Cognito Client
   - `username`: Email do usuário
   - `password`: Senha do usuário

3. **Execute "1. Get JWT Token"** - O token será salvo automaticamente

4. **Execute os outros requests** - O token é incluído automaticamente no header Authorization

---

## 🧪 Testando o Sistema

### Teste 1: Criar Feedback via API (POST)

> ⚠️ **IMPORTANTE**: A API agora requer autenticação Cognito. Veja a seção **"🔐 Autenticação com AWS Cognito"** acima para obter um token JWT antes de fazer requests.

#### Usando PowerShell:

```powershell
# 1. Obter token (veja seção de autenticação)
$token = Get-Content ".\cognito-scripts\cognito-token.txt" -Raw

# 2. Definir a URL da API (substitua pela sua URL do output)
$apiUrl = "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback"

# 3. Criar um feedback
$body = @{
    descricao = "Excelente atendimento!"
    nota = "5"
    urgencia = "MEDIA"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

$response = Invoke-RestMethod -Uri $apiUrl -Method POST -Headers $headers -Body $body
$response
```

#### Usando Bash/Linux:

```bash
# 1. Obter token (veja seção de autenticação)
TOKEN=$(cat ./cognito-scripts/cognito-token.txt)

# 2. Definir a URL da API (substitua pela sua URL do output)
API_URL="https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback"

# 3. Criar um feedback
curl -X POST "$API_URL" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Excelente atendimento!",
    "nota": "5",
    "urgencia": "MEDIA"
  }'
```

**Resposta esperada:**
```json
{
  "createdAt": "2026-01-08T03:26:02.447Z",
  "feedbackId": "52e45233-cee4-4d97-a94d-e82436b2683e",
  "message": "Olá seu feedback foi enviado com sucesso"
}
```

---

### Teste 2: Listar Feedbacks via API (GET)

> ⚠️ **IMPORTANTE**: A API agora requer autenticação Cognito. Use o token obtido na seção de autenticação.

#### Usando PowerShell:

```powershell
# 1. Obter token
$token = Get-Content ".\cognito-scripts\cognito-token.txt" -Raw

# 2. Definir a URL da API (substitua pela sua URL do output)
$apiUrl = "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks"

# 3. Listar feedbacks entre datas
$params = @{
    startDate = "2026-01-01"
    endDate = "2026-01-10"
}

$headers = @{
    "Authorization" = "Bearer $token"
}

$response = Invoke-RestMethod -Uri $apiUrl -Method GET -Body $params -Headers $headers
$response
```

#### Usando Bash/Linux:

```bash
# 1. Obter token
TOKEN=$(cat ./cognito-scripts/cognito-token.txt)

# 2. Definir a URL da API
API_URL="https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks"

# 3. Listar feedbacks entre datas
curl "$API_URL?startDate=2026-01-01&endDate=2026-01-10" \
  -H "Authorization: Bearer $TOKEN"
```

**Resposta esperada:**
```json
{
  "count": 10,
  "items": [
    {
      "feedbackId": "52e45233-cee4-4d97-a94d-e82436b2683e",
      "pk": "FEEDBACK",
      "createdAt": "2026-01-08T03:26:02.447Z",
      "descricao": "Excelente atendimento!",
      "nota": "5",
      "urgencia": "MEDIA"
    }
  ],
  "startDate": "2026-01-01",
  "endDate": "2026-01-10"
}
```

---

### Teste 3: Verificar Dados no DynamoDB

```bash
# Escanear todos os feedbacks na tabela
aws dynamodb scan --table-name FeedbacksTable --output table

# Verificar apenas os últimos 5 feedbacks
aws dynamodb scan --table-name FeedbacksTable --limit 5 --output json
```

---

### Teste 4: Gerar Relatório Semanal via Terminal

#### **Passo 1: Obter o ARN da Step Function**

```powershell
# PowerShell - Obter o ARN da Step Function
$stateMachineArn = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback `
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackProcessingStateMachineArn'].OutputValue" `
  --output text

Write-Host "State Machine ARN: $stateMachineArn"
```

```bash
# Bash/Linux - Obter o ARN da Step Function
STATE_MACHINE_ARN=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackProcessingStateMachineArn'].OutputValue" \
  --output text)

echo "State Machine ARN: $STATE_MACHINE_ARN"
```

#### **Passo 2: Executar a Step Function**

```powershell
# PowerShell - Executar Step Function
$executionName = "manual-exec-$(Get-Date -Format 'yyyyMMddHHmmss')"
$executionArn = aws stepfunctions start-execution `
  --state-machine-arn $stateMachineArn `
  --input '{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-10\"}' `
  --name $executionName `
  --query 'executionArn' `
  --output text

Write-Host "Execução iniciada!"
Write-Host "Execution ARN: $executionArn"
Write-Host ""
Write-Host "Aguarde 10-15 segundos para o processamento..."
```

```bash
# Bash/Linux - Executar Step Function
EXECUTION_NAME="manual-exec-$(date +%Y%m%d%H%M%S)"
EXECUTION_ARN=$(aws stepfunctions start-execution \
  --state-machine-arn "$STATE_MACHINE_ARN" \
  --input '{"startDate":"2026-01-01","endDate":"2026-01-10"}' \
  --name "$EXECUTION_NAME" \
  --query 'executionArn' \
  --output text)

echo "Execução iniciada!"
echo "Execution ARN: $EXECUTION_ARN"
echo ""
echo "Aguarde 10-15 segundos para o processamento..."
```

#### **Passo 3: Verificar o Status da Execução**

```powershell
# PowerShell - Verificar status
Start-Sleep -Seconds 10

$status = aws stepfunctions describe-execution `
  --execution-arn $executionArn `
  --query 'status' `
  --output text

Write-Host "Status: $status"

if ($status -eq "SUCCEEDED") {
    Write-Host "✅ Relatório gerado e enviado com sucesso!" -ForegroundColor Green
    Write-Host "📧 Verifique seu e-mail para ver o relatório."
} elseif ($status -eq "RUNNING") {
    Write-Host "⏳ Ainda processando... Execute o comando novamente em alguns segundos." -ForegroundColor Yellow
} elseif ($status -eq "FAILED") {
    Write-Host "❌ Execução falhou!" -ForegroundColor Red
    aws stepfunctions describe-execution --execution-arn $executionArn --query 'cause' --output text
}
```

```bash
# Bash/Linux - Verificar status
sleep 10

STATUS=$(aws stepfunctions describe-execution \
  --execution-arn "$EXECUTION_ARN" \
  --query 'status' \
  --output text)

echo "Status: $STATUS"

if [ "$STATUS" = "SUCCEEDED" ]; then
    echo "✅ Relatório gerado e enviado com sucesso!"
    echo "📧 Verifique seu e-mail para ver o relatório."
elif [ "$STATUS" = "RUNNING" ]; then
    echo "⏳ Ainda processando... Execute o comando novamente em alguns segundos."
elif [ "$STATUS" = "FAILED" ]; then
    echo "❌ Execução falhou!"
    aws stepfunctions describe-execution --execution-arn "$EXECUTION_ARN" --query 'cause' --output text
fi
```

#### **Passo 4: Ver Detalhes Completos da Execução**

```powershell
# PowerShell - Ver detalhes completos
aws stepfunctions describe-execution --execution-arn $executionArn --output json
```

```bash
# Bash/Linux - Ver detalhes completos
aws stepfunctions describe-execution --execution-arn "$EXECUTION_ARN" --output json
```

#### **Passo 5: Verificar o Relatório no S3**

```powershell
# PowerShell - Listar relatórios gerados
$bucketName = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback `
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackReportsBucketName'].OutputValue" `
  --output text

Write-Host "Bucket: $bucketName"
Write-Host ""
Write-Host "Relatórios disponíveis:"
aws s3 ls s3://$bucketName/ --recursive
```

```bash
# Bash/Linux - Listar relatórios gerados
BUCKET_NAME=$(aws cloudformation describe-stacks \
  --stack-name techchallenge-feedback \
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackReportsBucketName'].OutputValue" \
  --output text)

echo "Bucket: $BUCKET_NAME"
echo ""
echo "Relatórios disponíveis:"
aws s3 ls s3://$BUCKET_NAME/ --recursive
```

#### **Passo 6: Baixar e Visualizar o Relatório**

```powershell
# PowerShell - Baixar último relatório
$latestReport = aws s3 ls s3://$bucketName/ --recursive | `
  Sort-Object -Descending | `
  Select-Object -First 1 | `
  ForEach-Object { $_.Split()[-1] }

Write-Host "Baixando: $latestReport"
aws s3 cp s3://$bucketName/$latestReport .\relatorio.txt

Write-Host ""
Write-Host "=== CONTEÚDO DO RELATÓRIO ===" -ForegroundColor Cyan
Get-Content .\relatorio.txt
```

```bash
# Bash/Linux - Visualizar último relatório
LATEST_REPORT=$(aws s3 ls s3://$BUCKET_NAME/ --recursive | tail -1 | awk '{print $4}')

echo "Visualizando: $LATEST_REPORT"
echo ""
echo "=== CONTEÚDO DO RELATÓRIO ==="
aws s3 cp s3://$BUCKET_NAME/$LATEST_REPORT -
```

---

### Teste 5: Script Completo para Gerar Relatório (PowerShell)

Copie e cole este script completo no terminal PowerShell:

```powershell
# Script completo para gerar e verificar relatório
Write-Host "🚀 Iniciando geração de relatório..." -ForegroundColor Cyan

# 1. Obter ARN da Step Function
$stateMachineArn = aws cloudformation describe-stacks `
  --stack-name techchallenge-feedback `
  --query "Stacks[0].Outputs[?OutputKey=='FeedbackProcessingStateMachineArn'].OutputValue" `
  --output text

# 2. Executar Step Function
$executionName = "manual-exec-$(Get-Date -Format 'yyyyMMddHHmmss')"
$executionArn = aws stepfunctions start-execution `
  --state-machine-arn $stateMachineArn `
  --input '{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-10\"}' `
  --name $executionName `
  --query 'executionArn' `
  --output text

Write-Host "✅ Execução iniciada: $executionName" -ForegroundColor Green
Write-Host ""

# 3. Aguardar processamento
Write-Host "⏳ Aguardando processamento (15 segundos)..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 4. Verificar status
$status = aws stepfunctions describe-execution `
  --execution-arn $executionArn `
  --query 'status' `
  --output text

Write-Host ""
if ($status -eq "SUCCEEDED") {
    Write-Host "✅ SUCESSO! Relatório gerado e enviado!" -ForegroundColor Green
    Write-Host "📧 Verifique seu e-mail para ver o relatório." -ForegroundColor Cyan
    
    # 5. Mostrar relatório do S3
    Write-Host ""
    Write-Host "📄 Listando relatórios no S3..." -ForegroundColor Cyan
    $bucketName = aws cloudformation describe-stacks `
      --stack-name techchallenge-feedback `
      --query "Stacks[0].Outputs[?OutputKey=='FeedbackReportsBucketName'].OutputValue" `
      --output text
    
    aws s3 ls s3://$bucketName/ --recursive --human-readable
    
} elseif ($status -eq "RUNNING") {
    Write-Host "⏳ Ainda processando... Execute novamente em alguns segundos." -ForegroundColor Yellow
} else {
    Write-Host "❌ Falha na execução: $status" -ForegroundColor Red
    aws stepfunctions describe-execution --execution-arn $executionArn
}
```

---

### Teste 6: Verificar Dados no DynamoDB

```bash
# Escanear todos os feedbacks na tabela
aws dynamodb scan --table-name FeedbacksTable --output table

# Verificar apenas os últimos 5 feedbacks
aws dynamodb scan --table-name FeedbacksTable --limit 5 --output json
```

---

### Teste 7: Verificar Email Recebido

1. Acesse sua caixa de email
2. Procure por email com assunto: **"Relatório Semanal de Feedbacks"**
3. O email conterá o mesmo conteúdo do arquivo no S3

**Observação:** Se o email não chegar, verifique:
- Se o email foi verificado no SES: `aws ses list-verified-email-addresses`
- Se a variável de ambiente foi configurada: `aws lambda get-function-configuration --function-name notify-report --query 'Environment'`
- Logs da função: `aws logs tail /aws/lambda/notify-report --since 10m`

---

## 📊 Monitoramento e Logs

### Ver Logs em Tempo Real

```bash
# Logs da função insert-feedback
aws logs tail /aws/lambda/insert-feedback --since 5m --format short --follow

# Logs da função send-queue
aws logs tail /aws/lambda/send-queue --since 5m --format short

# Logs da função notify-critical
aws logs tail /aws/lambda/notify-critical --since 5m --format short

# Logs da função list-feedbacks
aws logs tail /aws/lambda/list-feedbacks --since 5m --format short

# Logs da função generate-weekly-report
aws logs tail /aws/lambda/generate-weekly-report --since 5m --format short

# Logs da função notify-report
aws logs tail /aws/lambda/notify-report --since 5m --format short
```

### Verificar Execuções da Step Function

```bash
# Listar últimas execuções
aws stepfunctions list-executions \
  --state-machine-arn "arn:aws:states:us-east-1:XXXX:stateMachine:feedback-processing" \
  --max-results 10
```

### Métricas no CloudWatch

Acesse o [CloudWatch Console](https://console.aws.amazon.com/cloudwatch/) para visualizar:
- **Invocations**: Número de execuções de cada Lambda
- **Errors**: Quantidade de erros
- **Duration**: Tempo médio de execução
- **Throttles**: Requisições bloqueadas por limite

---

## 🔄 Atualizar o Código Após Mudanças

Sempre que modificar o código Java:

```bash
# 1. Recompilar
mvn clean package

# 2. Fazer redeploy
sam deploy --no-confirm-changeset
```

O SAM automaticamente detectará mudanças e atualizará apenas os recursos modificados.

---

## ⏰ Agendamento Automático

O sistema está configurado para gerar relatórios automaticamente:

- **Frequência:** Toda semana
- **Dia:** Domingo
- **Horário:** 23:00 UTC (20:00 Brasília)

Para alterar o agendamento, edite a regra no `template.yaml`:

```yaml
WeeklyReportScheduleRule:
  Type: AWS::Events::Rule
  Properties:
    ScheduleExpression: "cron(0 23 ? * SUN *)"  # Altere aqui
```

**Exemplos de cron:**
- `cron(0 9 * * MON-FRI *)` - Dias úteis às 09:00 UTC
- `cron(0 0 1 * ? *)` - Todo dia 1 do mês à meia-noite
- `cron(0 12 * * ? *)` - Todos os dias ao meio-dia

---

## 📮 Testes com cURL e Postman

### 🔧 Testando com cURL

#### 1. Criar Feedback Positivo (não gera notificação)

```bash
curl -X POST "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Excelente atendimento!",
    "nota": "5",
    "urgencia": "MEDIA"
  }'
```

#### 2. Criar Feedback Crítico por Nota (gera notificação)

```bash
curl -X POST "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Atendimento muito ruim, tive que esperar 2 horas!",
    "nota": "1",
    "urgencia": "MEDIA"
  }'
```

#### 3. Criar Feedback Crítico por Urgência (gera notificação)

```bash
curl -X POST "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Sistema fora do ar, clientes não conseguem fazer pedidos!",
    "nota": "3",
    "urgencia": "ALTA"
  }'
```

#### 4. Listar Feedbacks com Filtro de Data

```bash
curl "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks?startDate=2026-01-01&endDate=2026-01-10"
```

#### 5. Listar Feedbacks com Filtro de Urgência

```bash
curl "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks?urgency=ALTA&startDate=2026-01-01&endDate=2026-01-10"
```

**Resposta Esperada (POST):**
```json
{
  "createdAt": "2026-01-08T03:26:02.447Z",
  "feedbackId": "52e45233-cee4-4d97-a94d-e82436b2683e",
  "message": "Olá seu feedback foi enviado com sucesso"
}
```

**Resposta Esperada (GET):**
```json
{
  "count": 3,
  "items": [
    {
      "feedbackId": "52e45233-cee4-4d97-a94d-e82436b2683e",
      "pk": "FEEDBACK",
      "createdAt": "2026-01-08T03:26:02.447Z",
      "descricao": "Excelente atendimento!",
      "nota": "5",
      "urgencia": "MEDIA"
    }
  ],
  "startDate": "2026-01-01",
  "endDate": "2026-01-10"
}
```

---

### 📬 Testando com Postman

#### Opção 1: Importar Collection Existente (se disponível)

1. Abra o Postman
2. Clique em **Import** no canto superior esquerdo
3. Selecione o arquivo `postman/postman_collection.json` deste repositório
4. A collection será importada automaticamente

#### Opção 2: Criar Requisições Manualmente

##### **1. Criar um Feedback (POST)**

**Configuração:**
- **Método:** POST
- **URL:** `https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback`
  - ⚠️ **Substitua** `xxxxxxxxxx` pela sua URL do API Gateway (obtida no output do `sam deploy`)
- **Headers:**
  - `Content-Type: application/json`
- **Body (raw JSON):**
  ```json
  {
    "descricao": "Produto chegou com defeito",
    "nota": "2",
    "urgencia": "ALTA"
  }
  ```

**Passos no Postman:**
1. Crie uma nova requisição
2. Selecione **POST** no dropdown de métodos
3. Cole a URL no campo de endereço
4. Vá na aba **Headers** e adicione:
   - Key: `Content-Type` | Value: `application/json`
5. Vá na aba **Body**
6. Selecione **raw** e escolha **JSON** no dropdown
7. Cole o JSON do body acima
8. Clique em **Send**

**Resposta Esperada (Status 200):**
```json
{
  "createdAt": "2026-01-08T14:32:15.223Z",
  "feedbackId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Olá seu feedback foi enviado com sucesso"
}
```

##### **2. Listar Feedbacks (GET)**

**Configuração:**
- **Método:** GET
- **URL:** `https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks`
- **Query Parameters (aba Params no Postman):**
  - `startDate`: `2026-01-01`
  - `endDate`: `2026-01-10`
  - `urgency`: `ALTA` (opcional)
  - `limit`: `50` (opcional, padrão: 100)

**Passos no Postman:**
1. Crie uma nova requisição
2. Selecione **GET** no dropdown
3. Cole a URL base no campo de endereço
4. Vá na aba **Params**
5. Adicione os parâmetros:
   - Key: `startDate` | Value: `2026-01-01`
   - Key: `endDate` | Value: `2026-01-10`
   - Key: `urgency` | Value: `ALTA` (opcional)
6. Clique em **Send**

**Resposta Esperada (Status 200):**
```json
{
  "count": 2,
  "items": [
    {
      "feedbackId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "pk": "FEEDBACK",
      "createdAt": "2026-01-08T14:32:15.223Z",
      "descricao": "Produto chegou com defeito",
      "nota": "2",
      "urgencia": "ALTA"
    },
    {
      "feedbackId": "b2c3d4e5-f6g7-8901-bcde-fg2345678901",
      "pk": "FEEDBACK",
      "createdAt": "2026-01-07T10:15:30.456Z",
      "descricao": "Sistema caiu durante o pagamento",
      "nota": "1",
      "urgencia": "ALTA"
    }
  ],
  "startDate": "2026-01-01",
  "endDate": "2026-01-10",
  "urgency": "ALTA"
}
```

##### **3. Criar Collection Organizada**

Para organizar melhor seus testes:

1. Crie uma **Collection** chamada "Tech Challenge - Feedbacks"
2. Adicione as seguintes requisições:
   - 📝 **POST Feedback Normal** (nota 4-5, urgência BAIXA/MEDIA)
   - 🔴 **POST Feedback Crítico por Nota** (nota 1-2)
   - 🚨 **POST Feedback Crítico por Urgência** (urgência ALTA)
   - 📋 **GET Listar Todos** (sem filtros)
   - 🔍 **GET Listar Críticos** (urgency=ALTA)
   - 📅 **GET Listar por Período** (com startDate e endDate)

##### **4. Usar Variáveis de Ambiente**

Para facilitar a troca de ambientes (dev, prod):

1. Clique no ícone de engrenagem (⚙️) no canto superior direito
2. Crie um **Environment** chamado "Tech Challenge - Prod"
3. Adicione a variável:
   - `base_url`: `https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod`
4. Use `{{base_url}}` nas URLs:
   - POST: `{{base_url}}/feedback`
   - GET: `{{base_url}}/feedbacks`

##### **5. Testar Cenários Diferentes**

**Feedback que GERA notificação (crítico):**
- ✅ Nota ≤ 2 (independente da urgência)
- ✅ Urgência = "ALTA" (independente da nota)

**Feedback que NÃO gera notificação:**
- ❌ Nota ≥ 3 E urgência = "MEDIA" ou "BAIXA"

**Exemplos para testar:**

```json
// ✅ CRÍTICO - Nota baixa
{
  "descricao": "Atendimento péssimo",
  "nota": "1",
  "urgencia": "MEDIA"
}

// ✅ CRÍTICO - Urgência alta
{
  "descricao": "Sistema fora do ar",
  "nota": "3",
  "urgencia": "ALTA"
}

// ❌ NORMAL - Não é crítico
{
  "descricao": "Entrega demorou um pouco",
  "nota": "3",
  "urgencia": "BAIXA"
}

// ❌ NORMAL - Feedback positivo
{
  "descricao": "Adorei o produto!",
  "nota": "5",
  "urgencia": "BAIXA"
}
```

##### **6. Verificar se Notificação Foi Enviada**

Após enviar um feedback crítico:

1. Aguarde 2-3 segundos
2. Verifique os logs no terminal:
   ```bash
   aws logs tail /aws/lambda/notify-critical --since 1m --format short
   ```
3. Procure por mensagens de sucesso ou erro
4. Verifique sua caixa de e-mail configurada no Mailtrap

---

### 📊 Testando Diferentes Cenários

| Cenário | Nota | Urgência | É Crítico? | Notificação? |
|---------|------|----------|------------|-------------|
| Feedback Positivo | 5 | BAIXA | ❌ Não | Não enviada |
| Feedback Normal | 3 | MEDIA | ❌ Não | Não enviada |
| Nota Baixa | 2 | BAIXA | ✅ Sim | Enviada |
| Nota Muito Baixa | 1 | MEDIA | ✅ Sim | Enviada |
| Urgência Alta | 4 | ALTA | ✅ Sim | Enviada |
| Crítico Total | 1 | ALTA | ✅ Sim | Enviada |

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


## 🗑️ Limpeza de Recursos

Para deletar todos os recursos criados na AWS e evitar cobranças:

### Opção 1: Deletar via SAM

```bash
sam delete --stack-name techchallenge-feedback
```

### Opção 2: Deletar via CloudFormation

```bash
# Deletar stack
aws cloudformation delete-stack --stack-name techchallenge-feedback

# Aguardar conclusão (pode levar alguns minutos)
aws cloudformation wait stack-delete-complete --stack-name techchallenge-feedback
```

### Limpeza Manual (se necessário)

Se houver recursos que não foram deletados automaticamente:

```bash
# 1. Esvaziar e deletar bucket S3 de relatórios
aws s3 rm s3://feedback-reports-techchallenge-feedback-XXXXXXXXXXXX --recursive
aws s3 rb s3://feedback-reports-techchallenge-feedback-XXXXXXXXXXXX

# 2. Deletar bucket S3 do SAM (se desejar)
aws s3 rb s3://aws-sam-cli-managed-default-samclisourcebucket-xxxx --force

# 3. Remover email verificado do SES (opcional)
aws ses delete-verified-email-address --email-address seu-email@exemplo.com
```

---

## 📊 Regras de Negócio

### Estrutura do Feedback

Campos obrigatórios:
- `descricao` (String): Descrição do feedback
- `nota` (String): Nota de 1 a 5
- `urgencia` (String): ALTA, MEDIA ou BAIXA

Campos gerados automaticamente:
- `feedbackId` (UUID): Identificador único
- `pk` (String): Sempre "FEEDBACK" (para query no GSI)
- `createdAt` (ISO String): Timestamp de criação

### Fluxo de Inserção de Feedback

1. Cliente envia POST para `/feedback`
2. Lambda `insert-feedback` valida e salva no DynamoDB
3. DynamoDB Streams dispara Lambda `send-queue`
4. `send-queue` publica evento no EventBridge
5. EventBridge pode disparar `notify-critical` (se crítico)

### Fluxo de Geração de Relatórios

1. **EventBridge Rule** dispara domingo 23:00 UTC (ou execução manual)
2. **Step Function** inicia com parâmetros de data
3. **Lambda list-feedbacks**: Consulta DynamoDB com filtro de datas
4. **Lambda generate-weekly-report**: Processa estatísticas e salva no S3
5. **Lambda notify-report**: Lê S3 e envia email via SES

### Feedback Crítico

Um feedback é considerado **crítico** quando atende a **pelo menos uma** das condições:
- `urgencia == "ALTA"`
- `nota <= 2`

**Ações automáticas:**
- Evento publicado no EventBridge com `isCritical: true`
- Lambda `notify-critical` pode enviar notificação à equipe

---

## 🧪 Testes Unitários

O projeto possui **cobertura completa de testes unitários** para todas as 6 funções Lambda, utilizando **JUnit 5** e **Mockito** para criar mocks dos serviços AWS.

### 📊 Cobertura de Testes Alcançada

| Lambda | Arquivo de Teste | Testes | Cobertura |
|--------|-----------------|--------|-----------|
| **insert-feedback** | `InsertFeedbackFunctionTest.java` | 5 testes | ✅ 100% |
| **send-queue** | `SendQueueFunctionTest.java` | 4 testes | ✅ 100% |
| **notify-critical** | `NotifyCriticalFunctionTest.java` | 4 testes | ✅ 100% |
| **list-feedbacks** | `ListFeedbacksFunctionTest.java` | 4 testes | ✅ 100% |
| **generate-weekly-report** | `GenerateWeeklyReportFunctionTest.java` | 4 testes | ✅ 100% |
| **notify-report** | `NotifyReportFunctionTest.java` | 6 testes | ✅ 100% |

**Total:** 27 testes unitários cobrindo todos os fluxos principais, casos de erro e validações.

---

### 🔍 Cenários Testados por Lambda

#### **1. insert-feedback** (InsertFeedbackFunctionTest)
- ✅ Criação de feedback com sucesso
- ✅ Geração automática de UUID para feedbackId
- ✅ Persistência no DynamoDB com timestamp
- ✅ Validação de campos obrigatórios
- ✅ Tratamento de erros do DynamoDB

#### **2. send-queue** (SendQueueFunctionTest)
- ✅ Detecção de feedback crítico por nota baixa (≤2)
- ✅ Detecção de feedback crítico por urgência "Critical"
- ✅ Publicação de evento no EventBridge com flag `isCritical`
- ✅ Processamento de múltiplos registros do DynamoDB Stream

#### **3. notify-critical** (NotifyCriticalFunctionTest)
- ✅ Envio de email via Mailtrap para feedbacks críticos
- ✅ Formatação correta do corpo do email
- ✅ Validação de dados do feedback
- ✅ Tratamento de erros de envio

#### **4. list-feedbacks** (ListFeedbacksFunctionTest)
- ✅ Listagem de feedbacks do DynamoDB
- ✅ Filtro por urgência (Critical, High, Medium, Low)
- ✅ Integração com API Gateway (query parameters)
- ✅ Retorno de lista vazia quando não há feedbacks

#### **5. generate-weekly-report** (GenerateWeeklyReportFunctionTest)
- ✅ Geração de relatório e upload para S3
- ✅ Criação automática de bucket se não existir
- ✅ Cálculo de estatísticas (média de notas, distribuição)
- ✅ Geração de relatório mesmo com lista vazia

#### **6. notify-report** (NotifyReportFunctionTest)
- ✅ Leitura de relatório do S3
- ✅ Envio por email via Amazon SES
- ✅ Validação de parâmetros obrigatórios (reportKey)
- ✅ Log detalhado de envio de email
- ✅ Tratamento de erro ao ler do S3
- ✅ Tratamento de erro ao enviar email via SES

---

### ⚡ Comandos para Executar os Testes

#### **Executar TODOS os testes do projeto**
```bash
# Maven - Raiz do projeto (testa todos os módulos)
mvn clean test
```

**Saída esperada:**
```
[INFO] Reactor Summary for techchallenge-feedback 1.0:
[INFO]
[INFO] Lambda Insert Feedback ............................. SUCCESS
[INFO] Lambda Send Queue .................................. SUCCESS
[INFO] Lambda Notify Critical ............................. SUCCESS
[INFO] Lambda List Feedbacks .............................. SUCCESS
[INFO] Lambda Generate Weekly Report ...................... SUCCESS
[INFO] Lambda Notify Report ............................... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  15.432 s
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
```

---

#### **Executar testes de uma Lambda específica**

```bash
# Testar apenas insert-feedback
cd insert-feedback
mvn test

# Testar apenas send-queue
cd send-queue
mvn test

# Testar apenas notify-critical
cd notify-critical
mvn test

# Testar apenas list-feedbacks
cd list-feedbacks
mvn test

# Testar apenas generate-weekly-report
cd generate-weekly-report
mvn test

# Testar apenas notify-report
cd notify-report
mvn test
```

---

#### **Executar testes com relatório de cobertura**

Para gerar relatório de cobertura com **JaCoCo**:

```bash
# Executar testes com cobertura
mvn clean test jacoco:report

# Ver relatório HTML (substitua pelo módulo desejado)
start insert-feedback/target/site/jacoco/index.html  # Windows
open insert-feedback/target/site/jacoco/index.html   # macOS
xdg-open insert-feedback/target/site/jacoco/index.html  # Linux
```

---

#### **Executar testes em modo de observação (watch)**

Para executar testes automaticamente ao modificar o código:

```bash
# Instalar Maven Wrapper Watch (se não tiver)
mvn wrapper:wrapper

# Executar em watch mode
mvn fizzed-watcher:run
```

---

### 🛠️ Tecnologias de Teste Utilizadas

- **JUnit 5** (Jupiter) - Framework de testes
- **Mockito** - Criação de mocks para AWS SDK
- **AWS SDK v2** - Clientes mockados (DynamoDB, S3, SES, EventBridge)
- **Reflection API** - Injeção de mocks em campos privados
- **Maven Surefire Plugin** - Execução de testes

---

### 📝 Padrão de Testes Implementado

Todos os testes seguem o mesmo padrão:

```java
@BeforeEach
void setUp() {
    // Criar mocks dos clientes AWS
    mockDynamoDb = mock(DynamoDbClient.class);
    
    // Configurar comportamento dos mocks
    when(mockDynamoDb.putItem(any(PutItemRequest.class)))
        .thenReturn(PutItemResponse.builder().build());
    
    // Injetar mock usando Reflection
    Field field = FunctionClass.class.getDeclaredField("dynamoDb");
    field.setAccessible(true);
    field.set(functionInstance, mockDynamoDb);
}

@Test
void testSuccessScenario() {
    // Preparar entrada
    Map<String, Object> input = Map.of("key", "value");
    
    // Executar função
    Map<String, Object> result = function.handleRequest(input, mockContext);
    
    // Verificar resultado
    assertNotNull(result);
    assertEquals(200, result.get("statusCode"));
    
    // Verificar interação com mock
    verify(mockDynamoDb, times(1)).putItem(any(PutItemRequest.class));
}
```

---

## 📈 Melhorias Futuras

### Segurança
- [x] ✅ **Autenticação Cognito nas APIs** (IMPLEMENTADO - veja seção "🔐 Autenticação com AWS Cognito")
- [ ] Adicionar WAF no API Gateway para proteção contra ataques
- [ ] Habilitar encryption at rest no DynamoDB
- [ ] Implementar rate limiting por usuário

### Funcionalidades
- [ ] Dashboard web para visualização de feedbacks
- [ ] Filtros avançados na API (por urgência, nota, período)
- [ ] Notificações por SMS via SNS para feedbacks críticos
- [ ] Análise de sentimento com Amazon Comprehend
- [ ] Export de relatórios em PDF

### Monitoramento
- [ ] CloudWatch Dashboard customizado
- [ ] Alarmes para erros e latência
- [ ] X-Ray para rastreamento distribuído
- [ ] Métricas customizadas no CloudWatch

### DevOps
- [ ] Pipeline CI/CD com GitHub Actions ou CodePipeline
- [ ] Testes de integração automatizados
- [ ] Deploy multi-ambiente (dev, staging, prod)
- [ ] Versionamento de APIs

---

### AWS CLI - Comandos Úteis

```bash
# Ver logs em tempo real
aws logs tail /aws/lambda/insert-feedback --follow

# Listar feedbacks no DynamoDB
aws dynamodb scan --table-name FeedbacksTable --limit 10

# Executar Step Function manualmente
aws stepfunctions start-execution \
  --state-machine-arn "arn:aws:states:REGION:ACCOUNT:stateMachine:feedback-processing" \
  --input '{"startDate":"2026-01-01","endDate":"2026-01-10"}'

# Ver relatórios no S3
aws s3 ls s3://feedback-reports-techchallenge-feedback-XXXXXXXXXXXX/

# Verificar email no SES
aws ses list-verified-email-addresses

# Ver status do stack
aws cloudformation describe-stacks --stack-name techchallenge-feedback
```

---

## �📚 Recursos Adicionais

### Documentação AWS
- [AWS Lambda Developer Guide](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html)
- [AWS SAM Documentation](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html)
- [DynamoDB Streams](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Streams.html)
- [Step Functions Developer Guide](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html)
- [Amazon SES Developer Guide](https://docs.aws.amazon.com/ses/latest/dg/Welcome.html)

### Ferramentas Utilizadas
- [Java 21 Documentation](https://docs.oracle.com/en/java/javase/21/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [Jackson Databind](https://github.com/FasterXML/jackson-databind)
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html)

---


## 📝 Licença

Este projeto é parte do Tech Challenge da FIAP e foi desenvolvido para fins educacionais.

---

## ✨ Créditos

Desenvolvido como parte do **Tech Challenge - Fase 4** da FIAP.

**Tecnologias:** Java 21, AWS Lambda, DynamoDB, Step Functions, S3, SES, EventBridge, API Gateway

**Arquitetura:** Serverless, Event-Driven, Multi-Module Maven Project
