# Scripts de Gerenciamento do Cognito

Scripts PowerShell para gerenciar usuários do Cognito User Pool e testar a API com autenticação.

## Pré-requisitos

- AWS CLI configurado com credenciais válidas
- PowerShell 5.1 ou superior
- Stack do CloudFormation já implantada (`sam deploy`)

## Scripts Disponíveis

### 1. manage-users.ps1

Gerencia usuários no Cognito User Pool.

#### Criar Usuário

```powershell
.\manage-users.ps1 -Action create -Email "usuario@example.com" -Password "SenhaForte123!" -Name "Nome do Usuário"
```

**Requisitos de senha:**
- Mínimo 8 caracteres
- Pelo menos 1 letra maiúscula
- Pelo menos 1 letra minúscula
- Pelo menos 1 número
- Pelo menos 1 caractere especial

#### Fazer Login (Obter Token)

```powershell
.\manage-users.ps1 -Action login -Email "usuario@example.com" -Password "SenhaForte123!"
```

O token será exibido no console e salvo em `cognito-token.txt` para uso posterior.

#### Listar Usuários

```powershell
.\manage-users.ps1 -Action list
```

#### Deletar Usuário

```powershell
.\manage-users.ps1 -Action delete -Email "usuario@example.com"
```

### 2. test-api-with-auth.ps1

Testa os endpoints da API com autenticação Cognito.

#### Inserir Feedback

```powershell
.\test-api-with-auth.ps1 -Action insert -Email "usuario@example.com" -Password "SenhaForte123!"
```

#### Listar Feedbacks

```powershell
.\test-api-with-auth.ps1 -Action list -Email "usuario@example.com" -Password "SenhaForte123!"
```

## Parâmetros Opcionais

Ambos os scripts aceitam o parâmetro `-StackName` para especificar o nome da stack do CloudFormation:

```powershell
.\manage-users.ps1 -Action list -StackName "minha-stack-customizada"
```

Padrão: `techchallenge-feedback-lambdas`

## Fluxo de Uso Típico

1. **Implantar a stack:**
   ```bash
   sam build
   sam deploy --guided
   ```

2. **Criar usuário de teste:**
   ```powershell
   cd cognito-scripts
   .\manage-users.ps1 -Action create -Email "teste@fiap.com" -Password "Teste@123" -Name "Usuario Teste"
   ```

3. **Testar inserção de feedback:**
   ```powershell
   .\test-api-with-auth.ps1 -Action insert -Email "teste@fiap.com" -Password "Teste@123"
   ```

4. **Listar feedbacks:**
   ```powershell
   .\test-api-with-auth.ps1 -Action list -Email "teste@fiap.com" -Password "Teste@123"
   ```

## Troubleshooting

### "Não foi possível obter os IDs do Cognito"

Verifique se a stack foi implantada corretamente:
```powershell
aws cloudformation describe-stacks --stack-name techchallenge-feedback-lambdas
```

### "Erro ao autenticar"

- Verifique se o email e senha estão corretos
- Confirme que o usuário foi criado com sucesso
- Verifique se a senha atende aos requisitos de complexidade

### Token expirado

Os tokens IdToken e AccessToken expiram em 1 hora. Execute novamente o comando de login para obter um novo token.

## Estrutura dos Tokens

O script `manage-users.ps1` retorna três tokens:

- **IdToken**: Use este no header `Authorization: Bearer <token>` para chamadas à API
- **AccessToken**: Para operações com recursos do Cognito
- **RefreshToken**: Válido por 30 dias, pode ser usado para renovar os outros tokens

## Uso Manual com cURL

Se preferir testar manualmente com cURL:

1. Obtenha o token:
   ```powershell
   .\manage-users.ps1 -Action login -Email "teste@fiap.com" -Password "Teste@123"
   ```

2. Copie o IdToken e use:
   ```bash
   # Inserir feedback
   curl -X POST https://seu-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedback \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer SEU_ID_TOKEN_AQUI" \
     -d '{"customerId":"customer-123","rating":5,"comment":"Teste","category":"PRODUTO"}'
   
   # Listar feedbacks
   curl -X GET https://seu-api-id.execute-api.us-east-1.amazonaws.com/Prod/feedbacks \
     -H "Authorization: Bearer SEU_ID_TOKEN_AQUI"
   ```
