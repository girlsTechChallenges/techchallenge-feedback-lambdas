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

### Teste 4: Executar Manualmente a Step Function (Gerar Relatório)

```bash
# PowerShell
$executionName = "test-exec-$(Get-Date -Format 'yyyyMMddHHmmss')"
aws stepfunctions start-execution `
  --state-machine-arn "arn:aws:states:us-east-1:XXXXXXXXXXXX:stateMachine:feedback-processing" `
  --input '{\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-10\"}' `
  --name $executionName
```

```bash
# Bash/Linux
EXECUTION_NAME="test-exec-$(date +%Y%m%d%H%M%S)"
aws stepfunctions start-execution \
  --state-machine-arn "arn:aws:states:us-east-1:XXXXXXXXXXXX:stateMachine:feedback-processing" \
  --input '{"startDate":"2026-01-01","endDate":"2026-01-10"}' \
  --name "$EXECUTION_NAME"
```

**Aguarde alguns segundos** e verifique o status:

```bash
# Substituir pelo ARN da execução retornado no comando anterior
aws stepfunctions describe-execution \
  --execution-arn "arn:aws:states:us-east-1:XXXX:execution:feedback-processing:test-exec-XXXXXXXX"
```

**Status esperado:**
```json
{
  "status": "SUCCEEDED",
  "output": "Relatório enviado com sucesso para seu-email@exemplo.com"
}
```

---

### Teste 5: Verificar Relatório no S3

```bash
# Listar relatórios gerados
aws s3 ls s3://feedback-reports-techchallenge-feedback-XXXXXXXXXXXX/ --recursive

# Baixar e visualizar o relatório
aws s3 cp s3://feedback-reports-techchallenge-feedback-XXXXXXXXXXXX/weekly-report-2026-01-08.txt -
```

**Conteúdo esperado do relatório:**
```
=== RELATÓRIO SEMANAL DE FEEDBACKS ===
Data de geração: 2026-01-08

Total de feedbacks: 10

Média geral das notas: 3.50

=== DISTRIBUIÇÃO POR URGÊNCIA ===
Alta: 3 feedbacks
Média: 4 feedbacks
Baixa: 3 feedbacks

=== QUANTIDADE DE AVALIAÇÕES POR DIA ===
2026-01-07: 3 avaliações
2026-01-08: 7 avaliações

=== DETALHES DOS FEEDBACKS ===
1. Nota: 5 | Urgência: MEDIA | Data: 2026-01-08T03:26:02Z
   Descrição: Excelente atendimento!
...
```

---

### Teste 6: Verificar Email Recebido

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

```bash
aws dynamodb scan --table-name FeedbacksTable --output table
```

### 4. Ver Logs das Lambdas

```bash
# Logs da função insert-feedback
aws logs tail /aws/lambda/insert-feedback --since 5m --format short

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

### Erro: ClassNotFoundException

**Problema:** Lambda não encontra a classe Java

**Causa:** O CodeUri no template.yaml estava apontando para o diretório ao invés do JAR

**Solução:** Já corrigido! O template.yaml agora aponta para os JARs corretos:
```yaml
CodeUri: insert-feedback/target/insert-feedback-1.0.jar
```

Se ainda houver erro, recompile e faça redeploy:
```bash
mvn clean package
sam deploy --no-confirm-changeset
```

---

### Erro: "The number of query conditions exceeds..."

**Problema:** Query do DynamoDB sem especificar o índice GSI

**Causa:** Faltava o `.indexName("pk-createdAt-index")` na query

**Solução:** Já corrigido! O código agora usa:
```java
QueryRequest.Builder queryBuilder = QueryRequest.builder()
    .tableName(tableName)
    .indexName("pk-createdAt-index")  // GSI adicionado
    .keyConditionExpression("pk = :pk AND createdAt BETWEEN :start AND :end");
```

---

### Email do relatório não chega

**Verificar:**

1. **Email verificado no SES:**
```bash
aws ses list-verified-email-addresses
```

2. **Variável de ambiente configurada:**
```bash
aws lambda get-function-configuration --function-name notify-report \
  --query 'Environment.Variables'
```

3. **Logs da função:**
```bash
aws logs tail /aws/lambda/notify-report --since 10m --format short
```

4. **Quota do SES:**
- Contas novas do SES estão em "sandbox mode"
- Só podem enviar emails para endereços verificados
- Para produção, solicite saída do sandbox no console SES

---

### API retorna "Internal Server Error"

**Verificar:**

1. **Logs da função Lambda:**
```bash
aws logs tail /aws/lambda/insert-feedback --since 5m --format short
```

2. **Código foi atualizado após mudanças:**
```bash
mvn clean package
sam deploy --no-confirm-changeset
```

3. **Permissões IAM:**
Verifique no console IAM se as roles das Lambdas têm as policies necessárias

---

### Step Function falha

**Verificar execução:**
```bash
# Listar execuções com falha
aws stepfunctions list-executions \
  --state-machine-arn "arn:aws:states:us-east-1:XXXX:stateMachine:feedback-processing" \
  --status-filter FAILED
```

**Ver detalhes do erro:**
```bash
# Substituir pelo ARN da execução com falha
aws stepfunctions describe-execution \
  --execution-arn "arn:aws:states:us-east-1:XXXX:execution:..."
```

**Ver histórico de eventos:**
```bash
aws stepfunctions get-execution-history \
  --execution-arn "arn:aws:states:us-east-1:XXXX:execution:..." \
  --reverse-order
```

---

### DynamoDB não recebe dados

**Verificar:**

1. **Tabela existe:**
```bash
aws dynamodb describe-table --table-name FeedbacksTable
```

2. **Permissões IAM da Lambda insert-feedback:**
```bash
aws iam get-role-policy --role-name techchallenge-feedback-InsertFeedbackFunctionRole-XXX --policy-name DynamoDBCrudPolicy
```

3. **Logs da Lambda:**
```bash
aws logs tail /aws/lambda/insert-feedback --since 5m --format short
```

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

## 📚 Recursos Adicionais

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

## 👥 Contribuindo

Para contribuir com o projeto:

1. Fork este repositório
2. Crie uma branch para sua feature (`git checkout -b feature/nova-funcionalidade`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/nova-funcionalidade`)
5. Abra um Pull Request

---

## 📝 Licença

Este projeto é parte do Tech Challenge da FIAP e foi desenvolvido para fins educacionais.

---

## ✨ Créditos

Desenvolvido como parte do **Tech Challenge - Fase 4** da FIAP.

**Tecnologias:** Java 21, AWS Lambda, DynamoDB, Step Functions, S3, SES, EventBridge, API Gateway

**Arquitetura:** Serverless, Event-Driven, Multi-Module Maven Project
