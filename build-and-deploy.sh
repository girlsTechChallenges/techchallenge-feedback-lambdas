#!/bin/bash
# Build and Deploy Script for Tech Challenge Feedback System
# Autor: Tech Challenge FIAP - Fase 4
# Descrição: Compila todos os módulos Lambda e faz deploy com AWS SAM

set -e

SKIP_TESTS=false
BUILD_ONLY=false
DEPLOY_ONLY=false

# Cores
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Processar argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --build-only)
            BUILD_ONLY=true
            shift
            ;;
        --deploy-only)
            DEPLOY_ONLY=true
            shift
            ;;
        *)
            echo -e "${RED}❌ Argumento desconhecido: $1${NC}"
            echo "Uso: $0 [--skip-tests] [--build-only] [--deploy-only]"
            exit 1
            ;;
    esac
done

echo -e "${CYAN}🚀 Tech Challenge - Build & Deploy Script${NC}"
echo -e "${CYAN}===========================================${NC}"
echo ""

# Verificar se Maven está instalado
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven não encontrado! Instale o Maven primeiro.${NC}"
    exit 1
fi

if [ "$DEPLOY_ONLY" = false ]; then
    # BUILD
    echo -e "${YELLOW}📦 ETAPA 1: Compilando todos os módulos...${NC}"
    echo ""
    
    MVN_ARGS="clean package"
    if [ "$SKIP_TESTS" = true ]; then
        MVN_ARGS="$MVN_ARGS -DskipTests"
        echo -e "${YELLOW}⚠️  Testes unitários serão ignorados${NC}"
    fi
    
    mvn $MVN_ARGS
    
    echo ""
    echo -e "${GREEN}✅ Build concluído com sucesso!${NC}"
    echo ""
fi

if [ "$BUILD_ONLY" = true ]; then
    echo -e "${GREEN}🎉 Build finalizado! (modo --build-only)${NC}"
    exit 0
fi

# Verificar se SAM CLI está instalado
if ! command -v sam &> /dev/null; then
    echo -e "${RED}❌ AWS SAM CLI não encontrado! Instale o SAM CLI primeiro.${NC}"
    echo -e "${YELLOW}   https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html${NC}"
    exit 1
fi

# DEPLOY
echo -e "${YELLOW}📤 ETAPA 2: Fazendo deploy com AWS SAM...${NC}"
echo ""

sam build

echo ""
echo -e "${CYAN}🚀 Iniciando deploy...${NC}"
sam deploy --no-confirm-changeset --no-fail-on-empty-changeset

echo ""
echo -e "${GREEN}✅ Deploy concluído com sucesso!${NC}"
echo ""
echo -e "${CYAN}📋 Próximos passos:${NC}"
echo -e "${WHITE}   1. Obtenha as URLs da API: aws cloudformation describe-stacks --stack-name techchallenge-feedback --query 'Stacks[0].Outputs' --output table${NC}"
echo -e "${WHITE}   2. Configure as variáveis no Postman${NC}"
echo -e "${WHITE}   3. Teste a API com a collection em postman_collection.json${NC}"
echo ""
echo -e "${GREEN}🎉 Sistema pronto para uso!${NC}"
