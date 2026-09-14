# Cascata

Launcher Android minimalista: uma lista alfabética vertical, índice de letras
lateral para saltar pela lista, favoritos fixados no topo e busca que ignora
acentos. Código original em Kotlin + Jetpack Compose.

O projeto nasceu de uma [análise técnica do Niagara Launcher](docs/analise-niagara-launcher-1.16.28.md)
— o conceito de navegação serviu de inspiração; nenhuma linha de código, asset ou
tradução daquele app foi reaproveitada. Ver [inspiração e licenças](docs/inspiracao-e-licencas.md).

## Plano

O roteiro completo, fase a fase, está em [`docs/plano.md`](docs/plano.md).

## Edições e download

- **`lite`** compila sem `INTERNET`; não tem card de clima. **`full`** soma
  só isso: `INTERNET` + `ACCESS_COARSE_LOCATION`, para o clima. Mesmo
  `applicationId` nas duas — instala-se uma ou outra, nunca as duas juntas.
- Onde baixar: [GitHub Releases](https://github.com/hampikamayuq/launcher-android-/releases),
  dois APKs por tag (`Cascata-vX.Y.Z-lite.apk` e `-full.apk`), cada um com seu
  `.sha256`. A versão 1.0 sai assim que a tag `v1.0.0` for criada — antes
  disso, a lista de releases mostra as versões anteriores.

**Privacidade:** o app não coleta nem envia dados — detalhes e como conferir
em [`docs/privacidade.md`](docs/privacidade.md).

## Estado atual (v1.0.0 — as nove fases concluídas)

| | |
|---|---|
| Backup em arquivo (`.cascata-backup`) com restauração entre aparelhos e "apagar tudo" | ✅ |
| Onboarding de três telas; pt-BR, en e es; acessibilidade revisada para TalkBack | ✅ |
| Baseline Profile por Macrobenchmark (módulo e job de CI) | ✅ |
| Uso do aparelho: card "Uso hoje", limites por app e pausa deliberada antes de abrir — sem guardar histórico | ✅ |
| Busca ampliada: calculadora, atalhos, contatos, configurações do sistema, web por intent, tolerância a 1 erro | ✅ |
| Widgets acima da lista: pilha com swipe, redimensionar, mover, restauração após backup | ✅ |
| Notificações na lista: badge por app, expansão inline com ações e resposta direta | ✅ |
| Card de mídia com controles | ✅ |
| Relógio em 4 estilos (básico, dígitos grandes, duas linhas, analógico) | ✅ |
| Cards: próximo alarme, bateria, próximo evento, clima (edição `full`) | ✅ |
| Cada card liga sozinho e pede só a sua permissão | ✅ |
| Configurações: modo escuro, cores (Material You / papel de parede / destaque), opacidade | ✅ |
| Densidade da lista e tamanho do texto | ✅ |
| Fontes embutidas (OFL) ou arquivo próprio | ✅ |
| Pacotes de ícones no formato aberto (`appfilter.xml`) | ✅ |
| Temas exportáveis (`.cascata-theme`) | ✅ |
| Lista alfabética com cabeçalhos de seção | ✅ |
| Índice lateral A–Z com arraste e retorno háptico | ✅ |
| Busca sem acento, por prefixo de palavra; swipe-up abre com teclado | ✅ |
| Favoritos ordenáveis por arraste (DataStore) | ✅ |
| Esconder e renomear apps | ✅ |
| Atalhos de app no toque longo (quando é o launcher padrão) | ✅ |
| Pedido para virar launcher padrão (`RoleManager`) | ✅ |
| Desinstalar por intent, informações do app | ✅ |
| Relógio + data, acordando só a cada minuto | ✅ |
| Ícones legados mascarados em círculo | ✅ |
| Perfis de trabalho e perfil privado (Android 15) | ✅ |
| Atualização automática ao instalar/remover apps | ✅ |
| Material You quando disponível | ✅ |
| CI com testes, lint e release assinado por tag | ✅ |
| Publicação (v1.0) | próxima fase ([plano](docs/plano.md)) |

## Tamanho

| | Cascata 0.9.0 `lite` (release, R8) | Cascata 0.9.0 `full` (release, R8) | Niagara 1.16.28 |
|---|---|---|---|
| APK | **2.162.291 bytes** (≈ 2,06 MB) | **2.186.143 bytes** (≈ 2,08 MB) | 13,4 MB |
| Permissões | `ACCESS_HIDDEN_PROFILES`, `READ_CALENDAR`, `READ_CONTACTS`, `PACKAGE_USAGE_STATS`, `BIND_APPWIDGET` (sistema) | as mesmas + `INTERNET`, `ACCESS_COARSE_LOCATION` | 26 |

Todas as permissões listadas são pedidas só ao ligar o recurso correspondente,
exceto as duas marcadas "sistema" — `PACKAGE_USAGE_STATS` e `BIND_APPWIDGET`
nunca são concedidas a um app comum na instalação, mesmo declaradas; veja
[`docs/privacidade.md`](docs/privacidade.md) para o que cada uma faz. (O
detalhamento por `classes.dex`/`resources.arsc`/contagem de entradas, medido
na v0.1.0 para a comparação inicial com o Niagara, não foi remedido desde
então — os números de APK acima são os atuais e verificáveis a qualquer
momento com `ls -l` no arquivo baixado.)

A comparação não é justa em recursos — o Niagara entrega 116 locales, temas e
vídeos, e este aqui ainda não faz metade do que aquele faz. Serve como linha de
base: é o custo de um launcher funcional antes de qualquer gordura.

## Decisões que valem explicar

- **Sem `QUERY_ALL_PACKAGES`.** A visibilidade de pacotes vem de um `<queries>`
  com o filtro `MAIN`/`LAUNCHER` — o suficiente para `LauncherApps`, sem a
  permissão de maior alcance do Android.
- **Permissão só quando a função liga.** O app instala sem pedir nada. Ligar o
  card de agenda pede `READ_CALENDAR`; ligar o de clima pede localização
  grosseira; ligar contatos na busca pede `READ_CONTACTS`. Recurso desligado é
  permissão não pedida e fonte não consultada.
- **Duas edições, mesmo app.** `lite` compila **sem `INTERNET`** — o card de
  clima nem aparece. `full` traz `INTERNET` e `ACCESS_COARSE_LOCATION` por uma
  única razão, o clima (Open-Meteo, sem chave, coordenadas arredondadas a ~1 km,
  cache de 30 min). Mesmo `applicationId`: instala-se uma **ou** outra.
- **Nada de analytics ou conta.** Zero dependências de Firebase, Play Services
  ou SDK de atribuição. Fora o clima na edição `full`, o app não abre socket.
- **Notificações vivem só na memória.** O acesso ao listener é concedido pelo
  usuário na tela do sistema; o serviço nem é instanciado antes disso. Nenhum
  título, texto, chave ou horário de notificação é gravado — o DataStore guarda
  apenas as preferências (silenciados, estilo do indicador).
- **Uso do aparelho sem histórico próprio.** O acesso a estatísticas de uso é
  concedido na tela do sistema; o Cascata consulta os eventos do dia quando
  precisa e não grava nada — só os limites por app e os segundos de pausa.
- **O que é persistido:** favoritos, apps ocultos, apelidos, as preferências de
  aparência, dos cards, de notificações, de busca e de uso (limites), o layout
  de widgets e o último clima, em DataStores; mais a fonte importada, se
  houver. Sem histórico de uso, sem banco.
- **Regras de backup separadas por canal:** `cloud-backup` e `device-transfer`
  são declarados um a um, em vez de repetir o mesmo bloco nos dois.
- **Sem framework de injeção.** As dependências são três objetos criados sob
  demanda em `CascataApp`; o custo de inicialização aparece direto no tempo até
  a primeira tela.

## Como compilar

```bash
export ANDROID_HOME=/caminho/do/sdk   # precisa de platform 36 e build-tools 36
./gradlew assembleLiteDebug           # app/build/outputs/apk/lite/debug/
./gradlew assembleFullDebug           # app/build/outputs/apk/full/debug/
./gradlew testLiteDebugUnitTest testFullDebugUnitTest
./gradlew lintLiteDebug lintFullDebug
```

Requisitos: JDK 17+, Android SDK com `platforms;android-36` e `build-tools;36.0.0`.
`minSdk` 26, `targetSdk` 36.

## Estrutura

```
app/src/main/java/app/cascata/launcher/
├── CascataApp.kt              # dependências da aplicação, criadas sob demanda
├── HomeActivity.kt            # a home (MAIN + HOME + LAUNCHER)
├── HomeViewModel.kt           # estado da tela, notificações, ações
├── HomeStateBuilder.kt        # seções, busca e reordenação — lógica pura
├── data/
│   ├── AppEntry.kt, AppRepository.kt, LauncherPrefs.kt, PrefsCodec.kt
│   ├── theme/                 # ThemeSettings, ThemePrefs, ThemeFile, SeedPalette,
│   │                          # WallpaperColorsSource, FontStore
│   ├── iconpack/              # pacotes no formato aberto (appfilter.xml)
│   ├── glance/                # alarme, bateria, agenda, mídia, weather/
│   ├── notifications/         # store em memória, texto, agrupamento, prefs
│   ├── search/                # calculadora (EvalEx), contatos, web, configurações do sistema
│   ├── usage/                 # acesso, agregação pura dos eventos do dia, limites
│   ├── backup/                # .cascata-backup versionado, BackupManager
│   └── onboarding/            # flag de primeiro uso
│   └── widgets/               # WidgetLayout (puro), WidgetPrefs, WidgetHostManager
├── notifications/             # CascataNotificationListener (bind só do sistema)
├── widgets/                   # WidgetPickerActivity, AppWidgetsRestoredReceiver
├── settings/                  # tela de configurações, uma seção por arquivo
└── ui/
    ├── HomeScreen.kt, AlphabetIndex.kt, AppIcon.kt, FavoritesRow.kt, sheets…
    ├── clock/                 # quatro estilos de relógio
    ├── glance/                # chips do topo
    ├── notifications/         # badge e expansão inline
    ├── search/                # resultados extras da busca
    ├── usage/                 # folha de uso, pausa, limites
    ├── onboarding/            # três páginas do primeiro uso
    ├── widgets/               # área de widgets, pilha, folha de edição
    └── theme/                 # CascataTheme, Fonts
app/src/full/                  # clima Open-Meteo + INTERNET/COARSE_LOCATION
app/src/lite/                  # stub de clima; edição sem rede
baselineprofile/               # gerador de Baseline Profile e Macrobenchmarks
```
