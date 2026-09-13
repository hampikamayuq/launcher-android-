# Release

Como assinar e publicar uma versão do Cascata.

## 0. As duas edições (`full` e `lite`)

Desde a Fase 3 cada release publica **dois APKs**, gerados pelos *product
flavors* da dimensão `network`:

| | `Cascata-vX.Y.Z-lite.apk` | `Cascata-vX.Y.Z-full.apk` |
|---|---|---|
| Permissões no manifesto | `ACCESS_HIDDEN_PROFILES`, `READ_CALENDAR` | as mesmas **+ `INTERNET` + `ACCESS_COARSE_LOCATION`** |
| Card de clima | não existe (nem aparece nas configurações) | Open-Meteo, sem chave, cache de 30 min |
| Resto do app | idêntico | idêntico |
| `BuildConfig.HAS_NETWORK` | `false` | `true` |

O `lite` é o padrão do projeto (`isDefault = true`): quem compila sem escolher
variante recebe a edição que não sabe abrir socket. A permissão de rede existe
por uma única razão — o clima —, e é por isso que ela mora num flavor em vez de
no manifesto principal: quem não quer rede tem um APK que *não pode* usá-la, e
não só a promessa de que não usa.

As duas edições têm o **mesmo `applicationId`** (`app.cascata.launcher`), sem
sufixo. São edições do mesmo app, não dois apps: instala-se uma **ou** outra, e
trocar de edição é uma atualização normal (mesma chave de assinatura, dados
preservados) — não dois ícones na gaveta. Instalar o `full` por cima do `lite`
substitui o APK e passa a pedir as permissões novas apenas quando o card de
clima for ligado.

Nem o `READ_CALENDAR` nem a localização são pedidos na instalação: estão
declarados, e o diálogo do sistema só aparece quando o usuário liga o card
correspondente.

## 1. Gerar a chave de assinatura

A chave de release **nunca** fica no repositório. Gere-a localmente com:

```bash
./scripts/make-keystore.sh
```

O script pede (ou lê de variáveis de ambiente `CASCATA_KEYSTORE_PATH`,
`CASCATA_KEYSTORE_PASSWORD`, `CASCATA_KEY_PASSWORD`) o caminho de saída e as
senhas, e gera um par de chaves RSA de 4096 bits, válido por 10.000 dias, com
alias `cascata`. Ao final ele imprime os nomes dos secrets do GitHub e o
comando `base64 -w0` para gerar o valor de `KEYSTORE_BASE64`.

Equivalente manual, se preferir rodar o `keytool` você mesmo:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias cascata \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

## 2. Onde guardar a chave

- Fora do repositório, sempre. `*.keystore` e `*.jks` estão no `.gitignore`
  como rede de segurança, mas o cuidado principal é nunca colocar o arquivo
  dentro da árvore do projeto para começo de conversa.
- Guarde o arquivo `.keystore` e as duas senhas (keystore e chave) num
  gerenciador de senhas ou cofre da organização. Se a chave se perder, não há
  como publicar atualizações do mesmo `applicationId` assinado por ela — o
  Cascata teria que trocar de identidade no Play Store/F-Droid.
- Mantenha uma cópia de backup (pendrive, cofre físico, gerenciador de
  senhas com histórico) — perder a chave é definitivo.

## 3. Configurar os secrets no GitHub

Em **Settings → Secrets and variables → Actions** do repositório, crie:

| Secret | Valor |
|---|---|
| `KEYSTORE_BASE64` | Conteúdo do keystore em base64: `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | Senha do keystore |
| `KEY_ALIAS` | `cascata` (ou o alias escolhido) |
| `KEY_PASSWORD` | Senha da chave |

O workflow de release decodifica `KEYSTORE_BASE64` para um arquivo temporário
em tempo de execução e exporta as quatro variáveis `CASCATA_KEYSTORE_PATH`,
`CASCATA_KEYSTORE_PASSWORD`, `CASCATA_KEY_ALIAS` e `CASCATA_KEY_PASSWORD` que
`app/build.gradle.kts` já sabe ler. Se `KEYSTORE_BASE64` estiver vazio, o
workflow falha cedo com a mensagem "Configure os secrets de assinatura (veja
docs/release.md)" em vez de compilar um APK sem assinatura e publicá-lo por
engano.

Sem esses secrets configurados, `assembleRelease` continua funcionando
localmente e no CI de push (`ci.yml`) — apenas gera um APK **sem assinatura**,
o que é esperado fora do fluxo de release.

## 4. Cortar uma release

1. Atualize `versionName` e `versionCode` em `app/build.gradle.kts`.
2. Commite essa mudança.
3. Crie uma tag anotada com o mesmo número do `versionName`, prefixada com
   `v`:

   ```bash
   git tag -a v0.2.0 -m "Cascata 0.2.0"
   git push origin v0.2.0
   ```

4. O push da tag dispara `.github/workflows/release.yml`.

Se a tag não bater com o `versionName` do `app/build.gradle.kts`, o workflow
falha imediatamente com uma mensagem explicando a diferença — corrija um dos
dois e refaça a tag.

## 5. O que o workflow de release faz

1. Faz checkout, configura JDK 21, Android SDK (`platforms;android-36`,
   `build-tools;36.0.0`) e cache do Gradle.
2. Confere que a tag (sem o `v`) é igual ao `versionName`.
3. Confere que o secret `KEYSTORE_BASE64` existe; se não, falha com a
   mensagem citada acima.
4. Decodifica o keystore para um arquivo temporário e roda
   `./gradlew --no-daemon testLiteReleaseUnitTest testFullReleaseUnitTest
   lintLiteRelease lintFullRelease assembleRelease` com as variáveis
   `CASCATA_*` exportadas. Com flavors, cada tarefa tem uma variante — as duas
   edições são testadas e passam pelo lint.
5. Verifica a assinatura de **cada** APK em `app/build/outputs/apk/*/release/`
   com `apksigner verify --print-certs`, e falha se não houver exatamente dois.
6. Renomeia para `Cascata-vX.Y.Z-lite.apk` e `Cascata-vX.Y.Z-full.apk`, cada um
   com seu `.sha256`.
7. Cria a GitHub Release da tag com `softprops/action-gh-release`, anexando os
   **quatro** arquivos (dois APKs e dois checksums), com notas geradas
   automaticamente (`generate_release_notes: true`).

## 6. Verificar a assinatura de um APK baixado

Depois de baixar `Cascata-vX.Y.Z-lite.apk` (ou `-full`) de uma release:

```bash
sha256sum -c Cascata-vX.Y.Z-lite.apk.sha256

$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs Cascata-vX.Y.Z-lite.apk

# E, se quiser conferir você mesmo que o lite não tem INTERNET:
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump permissions Cascata-vX.Y.Z-lite.apk
```

O `apksigner` (parte do Android SDK build-tools) confirma que a assinatura é
válida e imprime o certificado usado — compare o fingerprint com o que você
espera da sua própria chave, se estiver validando uma release de terceiros.
