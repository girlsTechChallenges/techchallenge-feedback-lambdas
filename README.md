# 🚀 Tech Challenge - Sistema de Feedbacks Serverless

Este repositório contém uma aplicação **serverless** desenvolvida em **Java 21** com **Maven**, empacotada como funções **AWS Lambda**. O sistema gerencia feedbacks de clientes com notificações automáticas para casos críticos.

## 📋 Visão Geral do Sistema

Este é um **sistema serverless de gerenciamento de feedbacks** que implementa uma arquitetura orientada a eventos na AWS. O sistema possui **6 funções Lambda** conectadas em dois fluxos principais:

### **Fluxo 1: Inserção e Notificação de Feedbacks Críticos**

#### **1. insert-feedback (Ponto de Entrada)**
- Recebe feedbacks via **API Gateway** (POST `/feedback`)
- **Sem autenticação** (API pública)
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
├── events/
│   ├── event.json
│   ├── invoke-payload.json
│   └── notify-event.json
├── insert-feedback/
│   ├── pom.xml
│   ├── Makefile
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── InsertFeedbackFunction.java
│   │   └── test/java/lambda/
│   │       └── InsertFeedbackFunctionTest.java
│   └── target/
├── send-queue/
│   ├── pom.xml
│   ├── Makefile
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── SendQueueFunction.java
│   │   └── test/java/lambda/
│   │       └── SendQueueFunctionTest.java
│   └── target/
├── notify-critical/
│   ├── pom.xml
│   ├── Makefile
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   ├── FeedbackEvent.java
│   │   │   └── NotifyCriticalFunction.java
│   │   └── test/java/lambda/
│   │       └── NotifyCriticalFunctionTest.java
│   └── target/
├── list-feedbacks/
│   ├── pom.xml
│   ├── Makefile
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── ListFeedbacksFunction.java
│   │   └── test/java/lambda/
│   └── target/
├── generate-weekly-report/
│   ├── pom.xml
│   ├── Makefile
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── GenerateWeeklyReportFunction.java
│   │   └── test/java/lambda/
│   └── target/
├── notify-report/
│   ├── pom.xml
│   ├── Makefile
│   ├── src/
│   │   ├── main/java/lambda/
│   │   │   └── NotifyReportFunction.java
│   │   └── test/java/lambda/
│   └── target/
├── statemachine/
│   └── feedback-processing.asl.json
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

## 🚀 Executando o Projeto Completo

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

### Passo 2: Verificar o Email no Amazon SES

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

### Passo 3: Validar o Template SAM

```bash
sam validate
```

**Saída esperada:**
```
template.yaml is a valid SAM Template
```

---

### Passo 4: Deploy com SAM

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

### Passo 5: Configurar Variável de Ambiente (Email do Relatório)

Atualize a função `notify-report` com o email verificado:

```bash
aws lambda update-function-configuration \
  --function-name notify-report \
  --environment "Variables={RECIPIENT_EMAIL=seu-email@exemplo.com}"
```

---

## 🧪 Testando o Sistema

### Teste 1: Criar Feedback via API (POST)

#### Usando PowerShell:

```powershell
# Definir a URL da API (substitua pela sua URL do output)
$apiUrl = "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback"

# Criar um feedback
$body = @{
    descricao = "Excelente atendimento!"
    nota = "5"
    urgencia = "MEDIA"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri $apiUrl -Method POST -ContentType "application/json" -Body $body
$response
```

#### Usando Bash/Linux:

```bash
# Definir a URL da API (substitua pela sua URL do output)
API_URL="https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedback"

# Criar um feedback
curl -X POST "$API_URL" \
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

#### Usando PowerShell:

```powershell
# Definir a URL da API (substitua pela sua URL do output)
$apiUrl = "https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks"

# Listar feedbacks entre datas
$params = @{
    startDate = "2026-01-01"
    endDate = "2026-01-10"
}

$response = Invoke-RestMethod -Uri $apiUrl -Method GET -Body $params
$response
```

#### Usando Bash/Linux:

```bash
# Definir a URL da API
API_URL="https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/Prod/feedbacks"

# Listar feedbacks entre datas
curl "$API_URL?startDate=2026-01-01&endDate=2026-01-10"
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
3. Selecione o arquivo `postman_collection.json` deste repositório (se existir)
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

## 📈 Melhorias Futuras

### Segurança
- [ ] Reativar autenticação Cognito nas APIs
- [ ] Implementar API Keys para controle de acesso
- [ ] Adicionar WAF no API Gateway
- [ ] Habilitar encryption at rest no DynamoDB

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
