# 🚀 INÍCIO RÁPIDO - 3 PASSOS

## ✅ Deploy Concluído!

Seu sistema está online e funcionando. Siga estes 3 passos para testar:

---

## 📦 1. Importar Collection no Postman

1. Abra o **Postman**
2. Clique em **Import**
3. Selecione: `postman_collection.json`

---

## ⚙️ 2. Configurar Variáveis

Clique na collection > **Variables** > Cole:

```
api_url       = https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod
user_pool_id  = us-east-1_Etx3Vkioi
client_id     = 638r9k783e2571ev516nue1eji
username      = test@example.com
password      = TestPass123!
```

Clique em **Save**

---

## 🧪 3. Testar

Execute nesta ordem:

1. **"1. Get JWT Token"** → Obter autenticação
2. **"2. Insert Feedback"** → Criar feedback
3. **"3. List Feedbacks"** → Ver todos os feedbacks

---

## 🎯 Pronto!

Se tudo funcionar, você verá:
- ✅ Token obtido (Status 200)
- ✅ Feedback criado (Status 200)
- ✅ Lista de feedbacks (Status 200)

---

## 📚 Mais Informações

- **Guia Completo:** [GUIA_TESTES_POSTMAN.md](GUIA_TESTES_POSTMAN.md)
- **Configuração Detalhada:** [POSTMAN_CONFIG.md](POSTMAN_CONFIG.md)
- **Testes Executados:** [TESTES_EXECUTADOS.md](TESTES_EXECUTADOS.md)
- **Resumo Deploy:** [RESUMO_DEPLOY.md](RESUMO_DEPLOY.md)

---

## 🆘 Problemas?

### Erro 401 (Unauthorized)
→ Execute novamente "1. Get JWT Token"

### Erro 403 (Forbidden)
→ Verifique se as variáveis estão corretas

### Collection não aparece
→ Certifique-se de importar o arquivo correto: `postman_collection.json`

---

## 🔑 Credenciais

**Email:** test@example.com  
**Senha:** TestPass123!

---

## 📡 URLs da API

**Insert:** https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedback  
**List:** https://ooz1z63v31.execute-api.us-east-1.amazonaws.com/Prod/feedbacks

---

**Boa sorte! 🎉**
