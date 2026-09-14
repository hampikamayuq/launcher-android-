#!/usr/bin/env bash
# Gera as imagens da ficha da loja e as copia para o fastlane.
#
#   scripts/screenshots.sh          # renderiza e copia
#   scripts/screenshots.sh --copy   # só copia o que já foi renderizado
#
# As telas são prévias do Compose (`app/src/screenshotTest/`) renderizadas por
# layoutlib na JVM: não há emulador nem aparelho no caminho. Cada função de
# prévia vale uma imagem, e o nome dela diz a posição na loja e o idioma —
# `Home1PtBr` é a imagem 1 de pt-BR. Ver docs/publicacao.md.
set -euo pipefail

cd "$(dirname "$0")/.."

REFERENCE="app/src/screenshotTestLiteDebug/reference/app/cascata/launcher/preview/StoreScreenshotsKt"

if [ "${1:-}" != "--copy" ]; then
  ./gradlew :app:updateLiteDebugScreenshotTest
fi

if [ ! -d "$REFERENCE" ]; then
  echo "nada renderizado em $REFERENCE" >&2
  exit 1
fi

copied=0
for file in "$REFERENCE"/*.png; do
  name="$(basename "$file")"
  # Home1PtBr_1-home_eeed8062_0.png -> função "Home1PtBr", posição "1"
  function="${name%%_*}"
  position="$(printf '%s' "$name" | sed -n 's/^[^_]*_\([0-9]\+\)-.*/\1/p')"
  case "$function" in
    *PtBr) locale="pt-BR" ;;
    *En) locale="en-US" ;;
    *Es) locale="es-ES" ;;
    *) echo "idioma desconhecido em $name" >&2; exit 1 ;;
  esac
  if [ -z "$position" ]; then
    echo "posição desconhecida em $name" >&2
    exit 1
  fi
  target="fastlane/metadata/android/$locale/images/phoneScreenshots"
  mkdir -p "$target"
  cp "$file" "$target/$position.png"
  copied=$((copied + 1))
done

echo "$copied imagens copiadas para fastlane/metadata/android/{pt-BR,en-US,es-ES}/images/phoneScreenshots/"
