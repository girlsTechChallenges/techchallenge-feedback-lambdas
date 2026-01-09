# 📁 Organização dos Arquivos JSON

Este documento descreve a estrutura de organização dos arquivos JSON no projeto.

## ✅ Arquivos Mantidos e Organizados

### 📋 Postman Collection
```
postman/
└── postman_collection.json    ← Collection com todas as APIs para testes
```

### 🧪 Payloads de Teste das Lambdas
```
test-payloads/
├── insert-feedback.json              ← Payload para testar inserção de feedback
├── list-feedbacks.json               ← Payload para testar listagem
├── send-queue.json                   ← Payload para testar envio para fila
├── notify-critical.json              ← Payload para testar notificação crítica
├── generate-weekly-report.json       ← Payload para testar geração de relatório
├── notify-report.json                ← Payload para testar envio de relatório
├── send-report.json                  ← Payload alternativo para relatório
└── send-full-report.json             ← Payload completo de relatório
```

### 🔧 Configurações do Step Functions
```
statemachine/
└── feedback-processing.asl.json      ← Definição ASL do fluxo Step Functions
```

### 📝 Exemplos de Referência
```
examples/
├── response.json                     ← Exemplo de resposta da API
├── test-payload.json                 ← Exemplo genérico de payload
├── test-post.json                    ← Exemplo de POST request
└── test2.json                        ← Outro exemplo de teste
```

### 🎯 Eventos para Testes SAM Local
```
events/
└── event.json                        ← Evento para testes locais com SAM CLI
```

## 🗑️ Arquivos Removidos (Temporários)

Os seguintes arquivos foram removidos por serem outputs temporários de testes:

- ❌ `output.json` - Output temporário de invocações Lambda
- ❌ `response-*.json` - Múltiplos arquivos de resposta de testes
- ❌ `payload*.json` - Payloads temporários criados durante testes
- ❌ `notify-payload*.json` - Payloads temporários de notificação
- ❌ `relatorio-completo-raw.json` - Relatório bruto temporário
- ❌ `report-*.txt` - Relatórios de texto temporários

## 🚫 Arquivos Ignorados pelo Git

O arquivo `.gitignore` foi atualizado para prevenir commit de arquivos temporários:

```gitignore
# Temporary JSON files (test outputs)
output.json
response-*.json
payload*.json
notify-payload*.json
relatorio-*.json
report-*.txt
```

## 📖 Como Usar

### Testes com Postman
```bash
# Importe a collection no Postman
postman/postman_collection.json
```

### Testes Diretos com AWS CLI
```bash
# Exemplo: testar insert-feedback
aws lambda invoke \
  --function-name insert-feedback \
  --payload file://test-payloads/insert-feedback.json \
  output.json
```

### Testes Locais com SAM CLI
```bash
# Exemplo: invocar localmente
sam local invoke InsertFeedbackFunction \
  --event events/event.json
```

## 🎯 Benefícios da Organização

✅ **Separação clara** entre arquivos de configuração e temporários  
✅ **Fácil localização** de payloads de teste por função  
✅ **Prevenção de commits** acidentais de arquivos temporários  
✅ **Estrutura limpa** e profissional do projeto  
✅ **Documentação clara** de onde encontrar cada tipo de arquivo
