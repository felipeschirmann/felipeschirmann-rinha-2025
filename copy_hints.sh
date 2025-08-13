#!/bin/bash

# Verifica se o ficheiro sinalizador de sucesso existe
if [ -f ".test_successful" ]; then
  echo "Teste bem-sucedido detectado. A copiar ficheiros de dicas..."

  # Define os diretórios de origem e destino
  SOURCE_DIR="target/native/agent-output"
  DEST_DIR="src/main/resources/META-INF/native-image"

  # Verifica se o diretório de origem existe
  if [ -d "$SOURCE_DIR" ]; then
    # Cria o diretório de destino se não existir
    mkdir -p "$DEST_DIR"

    # Move os ficheiros gerados
    mv "$SOURCE_DIR"/* "$DEST_DIR"/

    echo "Ficheiros movidos para $DEST_DIR com sucesso."
  else
    echo "Aviso: O diretório de origem $SOURCE_DIR não foi encontrado. Nenhum ficheiro foi copiado."
  fi

  # Limpa o ficheiro sinalizador
  rm -f .test_successful
else
  echo "Nenhum teste bem-sucedido detectado. Nenhum ficheiro foi copiado."
fi