# Changelog

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [1.0.0] - 2026-01-09

### 🎉 Lançamento Inicial

Sistema serverless completo de gerenciamento de feedbacks com autenticação enterprise-grade.

### ✨ Adicionado

#### Infraestrutura AWS
- 6 Funções Lambda (Java 21) com arquitetura multi-módulo Maven
- API Gateway com autenticação AWS Cognito
- DynamoDB com Streams e Global Secondary Index (pk-createdAt-index)
- EventBridge para roteamento de eventos críticos
- Step Functions para orquestração de relatórios semanais
- S3 Bucket para armazenamento de relatórios
- Amazon SES para envio de emails
- SQS Dead Letter Queue para tratamento de falhas
- CloudWatch Logs para monitoramento

#### Lambdas Implementadas
1. **insert-feedback**: Recebe e valida feedbacks via API (POST /feedback)
2. **list-feedbacks**: Lista feedbacks com filtros (GET /feedbacks)
3. **send-queue**: Processa DynamoDB Streams e detecta feedbacks críticos
4. **notify-critical**: Envia notificações via Mailtrap para feedbacks críticos
5. **generate-weekly-report**: Gera relatórios estatísticos semanais
6. **notify-report**: Envia relatórios por email via SES

#### Autenticação e Segurança
- Cognito User Pool com política de senha forte
- JWT token validation automática no API Gateway
- IdToken válido por 1 hora, RefreshToken por 30 dias
- Prevenção de enumeração de usuários
- HTTPS obrigatório (TLS 1.2+)
- CORS configurado

#### Automação
- Scripts PowerShell para gerenciamento de usuários Cognito
  - `manage-users.ps1`: CRUD completo de usuários
  - `test-api-with-auth.ps1`: Testes automatizados da API
- Scripts de build e deploy automatizado
  - `build-and-deploy.ps1` (Windows)
  - `build-and-deploy.sh` (Linux/Mac)

#### Testes
- 27 testes unitários com JUnit 5 e Mockito
- Collection Postman completa com todos os endpoints
- Payloads de teste para cada Lambda
- Coverage report com JaCoCo

#### Documentação
- README consolidado com 14 seções principais
- Guia de troubleshooting completo
- Histórico de testes (docs/TESTES_REALIZADOS.md)
- Documentação de scripts (cognito-scripts/README_SCRIPTS.md)
- Referência rápida de comandos

#### Fluxos Implementados

**Fluxo 1: Notificação de Feedbacks Críticos**
```
Cliente → API Gateway → insert-feedback → DynamoDB
  → Streams → send-queue → EventBridge → notify-critical → Mailtrap
```

**Fluxo 2: Relatórios Semanais Automáticos**
```
EventBridge (cron semanal) → Step Functions
  → list-feedbacks → generate-weekly-report → S3
  → notify-report → Amazon SES
```

### 🔧 Configuração

#### Variáveis de Ambiente
- `MAILTRAP_TOKEN_PARAM`: Nome do parâmetro SSM com token Mailtrap
- `SENDER_EMAIL`: Email do remetente (SES verificado)
- `RECIPIENT_EMAIL`: Email do destinatário de relatórios
- `REPORTS_BUCKET_NAME`: Nome do bucket S3 para relatórios

#### Recursos AWS Criados
- 6 Lambda Functions (512MB RAM, 30s timeout)
- 1 DynamoDB Table com on-demand billing
- 1 S3 Bucket
- 1 Cognito User Pool com 1 Client
- 1 API Gateway REST API
- 1 Step Functions State Machine
- 2 EventBridge Rules (crítico + schedule)
- 1 SQS Queue (DLQ)
- 7 IAM Roles com políticas mínimas necessárias
- N CloudWatch Log Groups

### 📊 Métricas

- **Cobertura de Testes**: ~80% (27 testes)
- **Lambdas**: 6 funções Java 21
- **Endpoints API**: 2 (POST /feedback, GET /feedbacks)
- **Tempo de Deploy**: ~5-7 minutos
- **Custo Estimado**: AWS Free Tier elegível

### 🔐 Segurança

- [x] Autenticação JWT via Cognito
- [x] Validação de entrada em todas as Lambdas
- [x] Políticas IAM com least privilege
- [x] Secrets armazenados em Systems Manager
- [x] DLQ para tratamento de falhas
- [x] CloudWatch Logs habilitado
- [x] CORS configurado
- [x] HTTPS obrigatório

### 📝 Regras de Negócio

**Feedback Crítico**: Um feedback é considerado crítico quando:
- `category == "Critical"` **OU**
- `rating <= 2`

**Notificações**: Feedbacks críticos disparam notificação automática via Mailtrap

**Relatórios**: Gerados automaticamente todo domingo às 23:00 UTC com estatísticas da semana

### 🛠️ Stack Tecnológica

- **Linguagem**: Java 21
- **Build**: Maven 3.x (multi-módulo)
- **IaC**: AWS SAM (CloudFormation)
- **SDK**: AWS SDK for Java v2
- **Serialização**: Jackson 2.17.2
- **Testes**: JUnit 5, Mockito
- **Scripts**: PowerShell 5.1+
- **Cloud**: AWS (Lambda, DynamoDB, Cognito, EventBridge, etc.)

---

## [Unreleased]

### Planejado para Próximas Versões

- [ ] Google OAuth via Cognito Identity Providers
- [ ] Dashboard CloudWatch customizado
- [ ] Análise de sentimento com ML
- [ ] API de busca com OpenSearch
- [ ] CI/CD com GitHub Actions
- [ ] Blue/Green deployment
- [ ] AWS X-Ray tracing
- [ ] Rate limiting no API Gateway
- [ ] Cache com ElastiCache/DAX
- [ ] Ambientes separados (dev/staging/prod)

---

## Tipos de Mudanças

- **Adicionado** - Para novas funcionalidades
- **Modificado** - Para mudanças em funcionalidades existentes
- **Descontinuado** - Para funcionalidades que serão removidas
- **Removido** - Para funcionalidades removidas
- **Corrigido** - Para correções de bugs
- **Segurança** - Para vulnerabilidades corrigidas

---

[1.0.0]: https://github.com/seu-usuario/techchallenge-feedback-lambdas/releases/tag/v1.0.0
[Unreleased]: https://github.com/seu-usuario/techchallenge-feedback-lambdas/compare/v1.0.0...HEAD
