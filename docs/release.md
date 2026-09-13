# Release

Como assinar e publicar uma versão do Cascata.

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
   `./gradlew --no-daemon test lint assembleRelease` com as variáveis
   `CASCATA_*` exportadas.
5. Verifica a assinatura do APK gerado com `apksigner verify --print-certs`.
6. Renomeia o APK para `Cascata-vX.Y.Z.apk` e gera `Cascata-vX.Y.Z.apk.sha256`.
7. Cria a GitHub Release da tag com `softprops/action-gh-release`, anexando o
   APK e o arquivo de checksum, com notas geradas automaticamente
   (`generate_release_notes: true`).

## 6. Verificar a assinatura de um APK baixado

Depois de baixar `Cascata-vX.Y.Z.apk` de uma release:

```bash
sha256sum -c Cascata-vX.Y.Z.apk.sha256

$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs Cascata-vX.Y.Z.apk
```

O `apksigner` (parte do Android SDK build-tools) confirma que a assinatura é
válida e imprime o certificado usado — compare o fingerprint com o que você
espera da sua própria chave, se estiver validando uma release de terceiros.
