# techchallenge-feedback

Este repositório contém uma aplicação **serverless** desenvolvida em **Java** com **Maven**, empacotada como funções **AWS Lambda**. O projeto está estruturado em módulos independentes, cada um representando uma função:

- **ingest-feedback**: função Lambda que ingere feedbacks (entrada principal).
- **notify-critical**: função Lambda responsável por detectar feedbacks críticos e notificar.
- **send-queue**: função Lambda que publica eventos no EventBridge.

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

## 🏗️ Visão geral da arquitetura

1. O cliente envia um request para o endpoint (API Gateway), que aciona a função **ingest-feedback**.
2. **ingest-feedback** grava o feedback no **DynamoDB**, que retorna `201 CREATED`.
3. O **DynamoDB Streams** aciona a função **send-queue**, que publica o evento `feedback.created` no **EventBridge**.
4. O **EventBridge** roteia o evento para a função **notify-critical**, caso o campo `"isCritical": true`.
5. A função **notify-critical** envia uma notificação para um servidor de e-mail.
6. Uma **DLQ (Dead Letter Queue)** está configurada para capturar mensagens com falha.
7. **CloudWatch Logs** é utilizado para monitoramento e debugging.

---

## ⚙️ Dependências e pré-requisitos (Windows)

- AWS SAM CLI (versão estável)
- Docker (para executar Lambdas localmente)
- Java 21
- Maven 3.x
- Credenciais AWS configuradas

---

## 🔨 Build do projeto

Na raiz do repositório, execute:

```bash
  mvn clean package

```

## 🚀 Executar localmente com SAM
Para executar a função **ingest-feedback** localmente, use o comando:

```bash
  sam local invoke IngestFeedbackFunction --event events/event.json --docker-network host

```

## 📦 Deploy para AWS
Para fazer o deploy do projeto na AWS, utilize:
```bash
  sam build
```
```bash
  sam deploy --guided
```

## 🧪 Testes

Os testes unitários estão localizados na pasta `src/test/java/lambda/` de cada módulo:
- `ingest-feedback/src/test/java/lambda/`
- `notify-critical/src/test/java/lambda/`
- `send-queue/src/test/java/lambda/`

Para executar todos os testes do projeto, utilize o Maven na raiz do repositório:
```bash
mvn test
```

Após a execução, os relatórios de teste são gerados em:
- `ingest-feedback/target/surefire-reports/`
- `notify-critical/target/surefire-reports/`
- `send-queue/target/surefire-reports/`

Para rodar um teste específico de um módulo, utilize:
```bash
cd ingest-feedback
mvn -Dtest=IngestFeedbackFunctionTest test
```

Os resultados dos testes podem ser visualizados nos arquivos `.txt` e `.xml` dentro das pastas `surefire-reports` de cada módulo.

---

## 🐞 Debugging e Logs

- Os logs das funções Lambda são enviados automaticamente para o **AWS CloudWatch Logs**.
- Para acessar os logs, utilize o console AWS ou o comando:
  ```bash
  aws logs tail /aws/lambda/NOME_DA_FUNCAO --follow
  ```
- Recomenda-se adicionar logs informativos e de erro no código para facilitar o troubleshooting.
- Utilize DLQ (Dead Letter Queue) para capturar eventos com falha.

---

## 🏅 Boas Práticas

- Utilize nomes claros para funções, variáveis e eventos.
- Escreva testes unitários para cada função Lambda.
- Faça tratamento de erros e valide entradas.
- Mantenha o código modular e documentado.
- Use versionamento semântico no Maven.

---

## 📦 Exemplos de Payloads e Comandos

- Exemplo de evento para ingestão de feedback: [`events/event.json`](events/event.json)
  ```json
  {
    "feedbackId": "123",
    "userId": "456",
    "message": "Ótimo atendimento!",
    "isCritical": false
  }
  ```
- Exemplo de evento crítico: [`events/notify-event.json`](events/notify-event.json)
  ```json
  {
    "feedbackId": "789",
    "userId": "456",
    "message": "Problema grave detectado!",
    "isCritical": true
  }
  ```
- Comando para invocar função localmente:
  ```bash
  sam local invoke IngestFeedbackFunction --event events/event.json --docker-network host
  ```
- Exemplo de chamada via curl para API Gateway (ajuste a URL conforme seu endpoint):
  ```bash
  curl -X POST https://<API_ID>.execute-api.<REGIAO>.amazonaws.com/prod/feedback \
    -H "Content-Type: application/json" \
    -d '{
      "feedbackId": "123",
      "userId": "456",
      "message": "Ótimo atendimento!",
      "isCritical": false
    }'
  ```

---

## 📊 Status do Projeto

![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)

---

## 📚 Referências

- [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html)
- [AWS SAM](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/what-is-sam.html)
- [Maven](https://maven.apache.org/)
- [Java](https://www.oracle.com/java/)
