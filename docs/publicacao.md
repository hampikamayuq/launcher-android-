# Publicação — checklist da 1.0

Três vias de distribuição, nessa ordem: GitHub Releases (já existe, é só
cortar a tag), F-Droid (falta submeter) e Play Store (opcional, sem prazo).
As três publicam o mesmo código-fonte; o que muda é o processo em volta.

## a. GitHub Releases

Já funciona desde a Fase 1 — a 1.0 só precisa da tag.

1. **Keystore e secrets.** Chave própria gerada com
   `./scripts/make-keystore.sh`, guardada fora do repositório; os quatro
   secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`) configurados em Settings → Secrets do repositório. Passo a
   passo completo em [`docs/release.md`](release.md) — não repetido aqui.
2. **Tag.** Atualizar `versionCode`/`versionName` em `app/build.gradle.kts`
   para `10` / `1.0.0`, commitar, e:

   ```bash
   git tag -a v1.0.0 -m "Cascata 1.0.0"
   git push origin v1.0.0
   ```

3. **O que `release.yml` produz.** Ao ver a tag `v1.0.0`: confere que ela bate
   com o `versionName`; roda testes e lint das duas edições, nas duas
   variantes `Release`; compila `assembleRelease` (as duas edições); confere
   a assinatura de cada APK com `apksigner verify --print-certs`; renomeia
   para `Cascata-v1.0.0-lite.apk` e `Cascata-v1.0.0-full.apk`, cada um com seu
   `.sha256`; publica a GitHub Release da tag com os quatro arquivos e notas
   automáticas.

Esta via não depende de revisão de terceiros — sai assim que a tag é
empurrada.

## b. F-Droid

### O que o app já cumpre

- **Sem dependência proprietária.** Todas as bibliotecas do
  [`docs/terceiros.md`](terceiros.md) são Apache-2.0 ou EPL-1.0, vêm do Maven
  Central público; nenhum SDK do Google Play Services, Firebase ou
  atribuição.
- **Sem rede no `lite`.** A edição sem `INTERNET` no manifesto é exatamente o
  que o F-Droid prefere: zero dependência de rede, verificável no próprio
  manifesto (`app/src/main/AndroidManifest.xml`) sem precisar confiar em
  ninguém.
- **Build reprodutível o bastante para o F-Droid reconstruir**: wrapper
  fixado, versões travadas no catálogo (`gradle/libs.versions.toml`), sem
  passo de rede durante a compilação além do download de dependências —
  detalhado na seção [Build reproduzível](#build-reproduzível) abaixo.

### O que falta

1. **Metadata desta fase** — os arquivos `fastlane/metadata/android/` deste
   commit (título, descrições, changelogs) são exatamente o que o F-Droid lê
   quando o repositório usa o formato fastlane.
2. **Uma receita de build** (`app.cascata.launcher.yml`) no repositório
   `fdroiddata`. Exemplo, com o cuidado abaixo:

   ```yaml
   Categories:
     - System
   License: MIT
   SourceCode: https://github.com/hampikamayuq/launcher-android-
   IssueTracker: https://github.com/hampikamayuq/launcher-android-/issues
   Changelog: https://github.com/hampikamayuq/launcher-android-/blob/main/CHANGELOG.md

   AutoName: Cascata
   AntiFeatures: []

   RepoType: git
   Repo: https://github.com/hampikamayuq/launcher-android-.git

   Builds:
     - versionName: "1.0.0"
       versionCode: 10
       commit: v1.0.0
       subdir: app
       gradle:
         - lite
       output: build/outputs/apk/lite/release/app-lite-release-unsigned.apk

   AutoUpdateMode: Version v%v
   UpdateCheckMode: Tags
   CurrentVersion: "1.0.0"
   CurrentVersionCode: 10
   ```

   **Atenção, e é importante:** `versionCode` hoje é único em
   `defaultConfig` (`app/build.gradle.kts`), compartilhado pelas duas
   flavors — `lite` e `full` saem com o **mesmo** `versionCode`. O F-Droid
   identifica cada build por `(packageName, versionCode)`, então **não dá
   para publicar as duas edições como builds simultâneos do mesmo pacote**
   sem antes dar a cada flavor um `versionCode` próprio (por exemplo,
   `versionCode * 10` para `lite` e `versionCode * 10 + 1` para `full`) — e
   mesmo assim o F-Droid trataria isso como duas *versões* na linha do
   tempo, não duas edições instaláveis lado a lado. A recomendação prática
   para a primeira submissão é publicar só a **`lite`** pelo F-Droid — é a
   edição que já bate 100% com a filosofia deles (zero rede) — e deixar a
   `full` para quem baixa do GitHub Release. Por isso o exemplo acima já usa
   só `gradle: [lite]`; um segundo bloco em `Builds` com `gradle: [full]`
   fica pronto para o dia em que os `versionCode` forem separados, mas não
   deve entrar na primeira submissão.
3. **`AntiFeatures` vazio para a `lite`.** Nenhuma das flags do F-Droid se
   aplica: sem rastreamento (`Tracking`), sem anúncio (`Ads`), sem DRM, sem
   dependência não-livre (`NonFreeDep`), sem promoção de app não-livre
   (`NonFreeAdd`). **`NonFreeNet` também não** — essa flag existe para apps
   que dependem de um serviço de rede proprietário (uma API fechada, uma
   conta paga, um backend que exige aceitar termos não-livres para
   funcionar); o Open-Meteo é uma API pública, sem cadastro, sem chave e sem
   termo de uso restritivo — e, de todo modo, é a edição `full`, não a `lite`
   que é submetida, quem fala com ele. A `lite` não abre socket nenhum.
4. **Processo de submissão**: um *Request For Packaging* (issue no
   [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata)) ou, mais direto, um
   merge request já com o arquivo de metadata acima em
   `metadata/app.cascata.launcher.yml`. O bot do F-Droid roda uma build de
   teste no MR; passar nela (mesmas verificações do CI próprio: versões
   travadas, sem rede durante o build) é o que libera a revisão humana.
   Não há prazo previsível — depende da fila de revisão do projeto F-Droid.

## c. Play Store

Opcional; incluído aqui como checklist, sem compromisso de prazo (revisão é
manual, do lado da Google, fora do nosso controle).

- **Play App Signing.** A Google passa a guardar a chave de assinatura final;
  o upload continua assinado pela chave própria (a mesma de
  `docs/release.md`), mas quem assina o APK que chega ao usuário é a chave
  gerenciada pela Play. Isso muda o processo de comparação de hash descrito
  abaixo — um APK baixado da Play nunca terá a mesma assinatura do publicado
  no GitHub, mesmo vindo do mesmo código-fonte.
- **Formulário Data Safety**: "nenhum dado coletado". É uma resposta honesta
  para as duas edições — a única chamada de rede (`full`, clima) não envia
  identificador nenhum, só coordenadas arredondadas; ver
  [`docs/privacidade.md`](privacidade.md) para o texto que sustenta cada
  resposta do formulário.
- **Declaração do Notification Listener** (`BIND_NOTIFICATION_LISTENER_SERVICE`)
  e do `PACKAGE_USAGE_STATS`: a Play exige um formulário de justificativa para
  os dois. Em ambos a resposta é a mesma — "app é um launcher, a função é
  central ao produto (notificações na lista, uso do aparelho) e é opcional,
  ligada pelo usuário" — o mesmo argumento que já está em
  [`docs/plano.md`](plano.md#6-riscos-que-já-conhecemos), seção de riscos.
- **Política de acesso a uso** (a página que a Play pede ligada ao
  `PACKAGE_USAGE_STATS`) pode apontar direto para
  [`docs/privacidade.md`](privacidade.md), que já cobre o que é lido, quando,
  e que nada é gravado.

## d. Screenshots

As seis imagens da ficha, nos três idiomas, estão em
`fastlane/metadata/android/{pt-BR,en-US,es-ES}/images/phoneScreenshots/1.png … 6.png`
(1080x2400, 440 dpi — a resolução que F-Droid e Play aceitam para telefone):

| # | Tela | Tema |
|---|------|------|
| 1 | Início: relógio, cards do *at a glance*, favoritos e o começo da gaveta | claro |
| 2 | Gaveta inteira com o índice alfabético | escuro |
| 3 | Busca: conta, contatos e telas do sistema | claro |
| 4 | Notificações abertas embaixo da linha do app | escuro |
| 5 | Uso do dia e limites por app | claro |
| 6 | Configurações: aparência | escuro |

**Nada de emulador.** As imagens são prévias do Compose
(`app/src/screenshotTest/java/app/cascata/launcher/preview/`) renderizadas por
layoutlib na JVM, pelo plugin `com.android.compose.screenshot`. Cada função de
prévia em `StoreScreenshots.kt` vale uma imagem; o nome dela diz a posição na
loja e o idioma (`Home1PtBr` é a imagem 1 de pt-BR), e é assim que o script
sabe para onde copiar.

Regerar depois de mexer na UI:

```bash
scripts/screenshots.sh          # renderiza e copia para o fastlane
scripts/screenshots.sh --copy   # só copia o que já está renderizado
```

O script chama `./gradlew :app:updateLiteDebugScreenshotTest`, que reescreve as
imagens de referência em `app/src/screenshotTestLiteDebug/reference/` — elas são
commitadas, e é o diff delas que mostra o que mudou de visual. Confira as
imagens antes de commitar: o `update` aceita qualquer renderização, inclusive
uma quebrada.

**Conferir sem regerar** (passo opcional, fora do CI):

```bash
./gradlew :app:validateLiteDebugScreenshotTest
```

Compara o que a UI desenha hoje com as imagens de referência e falha na
diferença, com o diff em `app/build/outputs/screenshotTest-results/`. Não está
no `.github/workflows/ci.yml` de propósito: o layoutlib baixa ~100 MB na
primeira execução e a comparação é pixel a pixel, sensível a troca de versão do
Compose — é uma conferência de quem mexe na UI, não um portão de CI. Duas
prévias mostram tempo relativo ("3 min", "18 min") calculado a partir do
relógio da máquina; elas podem divergir por um minuto entre uma renderização e
outra, e é o caso em que regerar é a resposta certa.

## Nota sobre `versionCode` por edição

Desde a 1.0.0 cada edição tem o seu `versionCode`: `base * 10 + 1` para a
`lite` e `base * 10 + 2` para a `full` (1.0.0 = base 10 → **101** e **102**).
Assim F-Droid e Play distinguem os dois builds do mesmo pacote, e uma
atualização de `lite` para `full` (102 > 101) é aceita pelo sistema; o caminho
inverso (`full` → `lite`) exige desinstalar, porque é um downgrade de código.
Os changelogs do `fastlane` seguem o código da edição publicada no F-Droid, a
`lite` (`changelogs/101.txt` para a 1.0.0; `9.txt` é a 0.9.0, anterior ao esquema).

## Build reproduzível

O que já está no lugar, hoje:

- **Versões travadas no catálogo.** `gradle/libs.versions.toml` fixa cada
  biblioteca e plugin numa versão exata — nenhuma entrada usa `+` ou range.
- **Wrapper fixado.** `gradle/wrapper/gradle-wrapper.properties` aponta para
  `gradle-8.14.3-bin.zip`, não `-latest`. (Melhoria possível, fora do escopo
  desta fase: adicionar `distributionSha256Sum` ao mesmo arquivo, para o
  wrapper recusar um zip adulterado antes mesmo de extrair.)
- **`--no-daemon`** em toda invocação do CI (`ci.yml`, `release.yml`,
  `baseline.yml`) — sem estado de um daemon Gradle vazando entre builds.
- **Mesmo SDK no CI e no README**: `platforms;android-36` e
  `build-tools;36.0.0`, declarados uma vez em cada workflow e no `## Como
  compilar` do `README.md`.

### Como comparar dois APKs

Útil para conferir que uma build local bate com a publicada, ou que duas
builds do CI em commits iguais produzem o mesmo resultado.

```bash
# 1. A assinatura é válida e usa a chave esperada
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs Cascata-v1.0.0-lite.apk

# 2. Hash bruto — ponto de partida, não prova final
sha256sum Cascata-v1.0.0-lite.apk build/outputs/apk/lite/release/app-lite-release.apk
```

**O hash bruto pode divergir mesmo entre builds idênticas no código-fonte.**
A assinatura de esquema v2/v3 embute um bloco de assinatura que cobre o ZIP
inteiro; qualquer diferença de metadados do ZIP (ordem de entradas, timestamps
gravados pelo empacotador) já muda o arquivo assinado byte a byte, mesmo com a
mesma chave e o mesmo conteúdo. Hash igual é uma prova forte de build
reprodutível; hash diferente **não** é prova de que o conteúdo mudou — só
prova que a comparação certa é outra:

```bash
# 3. Comparar o conteúdo, não o arquivo assinado
unzip -l Cascata-v1.0.0-lite.apk > /tmp/a.txt
unzip -l build/outputs/apk/lite/release/app-lite-release.apk > /tmp/b.txt
diff /tmp/a.txt /tmp/b.txt

# 4. Diferença completa, ignorando o que é só empacotamento
diffoscope Cascata-v1.0.0-lite.apk build/outputs/apk/lite/release/app-lite-release.apk
```

`unzip -l` já basta para conferir nomes, tamanhos e CRC de cada entrada —
suficiente para dizer "mesmo `classes.dex`, mesmos recursos". `diffoscope`
entra quando a lista bate mas se quer certeza sobre o conteúdo de cada
entrada, não só o CRC.
