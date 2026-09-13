#!/usr/bin/env bash
# Gera o keystore de release do Cascata.
#
# Uso interativo:
#   ./scripts/make-keystore.sh
#
# Uso não interativo (por exemplo, num script de setup):
#   CASCATA_KEYSTORE_PATH=/caminho/release.keystore \
#   CASCATA_KEYSTORE_PASSWORD=senha-do-keystore \
#   CASCATA_KEY_PASSWORD=senha-da-chave \
#   ./scripts/make-keystore.sh
#
# Nunca comite o arquivo gerado — *.keystore e *.jks já estão no .gitignore.

set -euo pipefail

ALIAS="cascata"
VALIDITY_DAYS=10000
KEY_SIZE=4096

OUT_PATH="${CASCATA_KEYSTORE_PATH:-}"
if [ -z "$OUT_PATH" ]; then
  read -r -p "Caminho de saída do keystore [./release.keystore]: " OUT_PATH
  OUT_PATH="${OUT_PATH:-./release.keystore}"
fi

if [ -e "$OUT_PATH" ]; then
  echo "Erro: já existe um arquivo em '$OUT_PATH'. Escolha outro caminho ou remova-o antes." >&2
  exit 1
fi

STORE_PASSWORD="${CASCATA_KEYSTORE_PASSWORD:-}"
if [ -z "$STORE_PASSWORD" ]; then
  read -r -s -p "Senha do keystore: " STORE_PASSWORD
  echo
  read -r -s -p "Confirme a senha do keystore: " STORE_PASSWORD_CONFIRM
  echo
  if [ "$STORE_PASSWORD" != "$STORE_PASSWORD_CONFIRM" ]; then
    echo "Erro: as senhas não coincidem." >&2
    exit 1
  fi
fi

KEY_PASSWORD="${CASCATA_KEY_PASSWORD:-}"
if [ -z "$KEY_PASSWORD" ]; then
  read -r -s -p "Senha da chave (Enter para usar a mesma do keystore): " KEY_PASSWORD
  echo
  KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"
fi

read -r -p "Nome e sobrenome (CN) [Cascata Launcher]: " CN
CN="${CN:-Cascata Launcher}"

keytool -genkeypair \
  -v \
  -keystore "$OUT_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize "$KEY_SIZE" \
  -validity "$VALIDITY_DAYS" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "CN=$CN, OU=Cascata, O=Cascata, L=, ST=, C=BR"

echo
echo "Keystore criado em: $OUT_PATH"
echo
echo "Guarde este arquivo e as senhas fora do repositório (gerenciador de senhas"
echo "ou cofre da organização). Nunca o comite — *.keystore e *.jks já estão no .gitignore."
echo
echo "Configure estes secrets no GitHub (Settings > Secrets and variables > Actions):"
echo "  KEYSTORE_BASE64   -> saída do comando abaixo"
echo "  KEYSTORE_PASSWORD -> a senha do keystore"
echo "  KEY_ALIAS         -> $ALIAS"
echo "  KEY_PASSWORD      -> a senha da chave"
echo
echo "Comando para gerar o valor de KEYSTORE_BASE64:"
echo "  base64 -w0 \"$OUT_PATH\""
