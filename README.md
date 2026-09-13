# Cascata

Launcher Android minimalista: uma lista alfabética vertical, índice de letras
lateral para saltar pela lista, favoritos fixados no topo e busca que ignora
acentos. Código original em Kotlin + Jetpack Compose.

O projeto nasceu de uma [análise técnica do Niagara Launcher](docs/analise-niagara-launcher-1.16.28.md)
— o conceito de navegação serviu de inspiração; nenhuma linha de código, asset ou
tradução daquele app foi reaproveitada. Ver [inspiração e licenças](docs/inspiracao-e-licencas.md).

## Estado atual (v0.1.0)

| | |
|---|---|
| Lista alfabética com cabeçalhos de seção | ✅ |
| Índice lateral A–Z com arraste e retorno háptico | ✅ |
| Busca sem acento, por prefixo de palavra | ✅ |
| Favoritos fixados (DataStore) | ✅ |
| Relógio + data, acordando só a cada minuto | ✅ |
| Menu de contexto (fixar, informações do app) | ✅ |
| Atualização automática ao instalar/remover apps | ✅ |
| Perfis de trabalho (multi-usuário) | ✅ |
| Material You quando disponível | ✅ |
| Widgets, gestos, notificações, clima | ainda não |

## Decisões que valem explicar

- **Sem `QUERY_ALL_PACKAGES`.** A visibilidade de pacotes vem de um `<queries>`
  com o filtro `MAIN`/`LAUNCHER` — o suficiente para `LauncherApps`, sem a
  permissão de maior alcance do Android. O app não pede **nenhuma** permissão em
  tempo de execução.
- **Nada de rede, analytics ou conta.** Zero dependências de Firebase, Play
  Services ou SDK de atribuição. O app não abre socket.
- **O que é persistido:** só o conjunto de favoritos, num DataStore. Sem
  histórico de uso, sem metadados de notificação, sem banco.
- **Regras de backup separadas por canal:** `cloud-backup` e `device-transfer`
  são declarados um a um, em vez de repetir o mesmo bloco nos dois.
- **Sem framework de injeção.** As dependências são três objetos criados sob
  demanda em `CascataApp`; o custo de inicialização aparece direto no tempo até
  a primeira tela.

## Como compilar

```bash
export ANDROID_HOME=/caminho/do/sdk   # precisa de platform 36 e build-tools 36
./gradlew :app:assembleDebug          # APK em app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # testes de normalização e seções
```

Requisitos: JDK 17+, Android SDK com `platforms;android-36` e `build-tools;36.0.0`.
`minSdk` 26, `targetSdk` 36.

## Estrutura

```
app/src/main/java/app/cascata/launcher/
├── CascataApp.kt          # dependências da aplicação
├── HomeActivity.kt        # a home (MAIN + HOME + LAUNCHER)
├── HomeViewModel.kt       # estado da tela: linhas, seções, busca
├── data/
│   ├── AppEntry.kt        # modelo + normalização de rótulo
│   ├── AppRepository.kt   # LauncherApps, ícones, mudanças de pacote
│   └── FavoritesStore.kt  # DataStore de favoritos
└── ui/
    ├── HomeScreen.kt      # lista, busca, favoritos, menu de contexto
    ├── AlphabetIndex.kt   # índice lateral arrastável
    ├── AppIcon.kt         # rasterização de ícone fora da main thread
    ├── ClockHeader.kt     # relógio e data
    └── theme/Theme.kt     # Material You com fallback próprio
```
