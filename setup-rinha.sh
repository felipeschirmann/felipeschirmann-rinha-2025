#!/bin/bash

# --- Configurações ---
set -e # Para o script parar imediatamente se um comando falhar

# URL do repositório oficial da Rinha
RINHA_REPO_URL="https://github.com/zanfranceschi/rinha-de-backend-2025.git"
# Nome da pasta local para o repositório oficial
RINHA_REPO_DIR="rinha-de-backend-2025-oficial"


# --- Início do Script ---

echo "🚀 Iniciando setup avançado do ambiente da Rinha de Backend 2025..."
echo "----------------------------------------------------------------"

# 1. Gerenciar o clone do repositório oficial da Rinha
echo "PASSO 1: Sincronizando repositório oficial da Rinha (apenas pastas necessárias)..."

if [ -d "$RINHA_REPO_DIR" ]; then
  # Se o diretório já existe, apenas atualiza
  echo "Diretório '$RINHA_REPO_DIR' encontrado. Atualizando..."
  cd "$RINHA_REPO_DIR"
  git pull
  cd ..
else
  # Se o diretório não existe, faz um clone esparso
  echo "Diretório '$RINHA_REPO_DIR' não encontrado. Fazendo um clone esparso..."
  # Clona o repositório sem baixar nenhum arquivo ainda (--no-checkout)
  git clone --depth 1 --filter=blob:none --no-checkout "$RINHA_REPO_URL" "$RINHA_REPO_DIR"
  cd "$RINHA_REPO_DIR"
  # Configura o sparse-checkout para baixar apenas as pastas desejadas
  git sparse-checkout init --cone
  git sparse-checkout set payment-processor rinha-test
  # Agora, "baixa" de fato os arquivos das pastas configuradas
  git checkout
  cd ..
fi
echo "✅ Repositório oficial sincronizado."
echo ""


# 2. Executar os processadores de pagamento a partir da pasta clonada
echo "PASSO 2: Iniciando os containers dos processadores de pagamento..."
# O caminho para o arquivo agora é dentro da pasta que clonamos
docker compose -f "$RINHA_REPO_DIR/payment-processor/docker-compose.yml" up -d
sleep 5 # Pausa para estabilização da rede externa
echo "✅ Processadores de pagamento iniciados."
echo ""


# 3. Executar o arquivo docker-compose.yml do seu projeto
echo "PASSO 3: Iniciando a stack da sua aplicação (Nginx, API, Redis)..."
# Usa o seu arquivo docker-compose.yml principal
docker compose up -d --build --force-recreate
echo "✅ Sua aplicação está no ar."
echo ""


echo "----------------------------------------------------------------"
echo "🎉 Ambiente pronto para os testes!"
echo "Os scripts de teste estão em: '$RINHA_REPO_DIR/rinha-test/'"
echo "Para rodar o teste, execute: cd $RINHA_REPO_DIR/rinha-test && k6 run rinha.js"
