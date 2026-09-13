# Análise técnica — Niagara Launcher v1.16.28 (`bitpit.launcher`)

Análise estática do pacote `Niagara_Launcher_v1_16_28_arquivos.zip` (conteúdo de APK já
extraído). Ferramentas: androguard 4.1.4 (AXML/ARSC/DEX), inspeção de strings do DEX e dos
recursos. Nenhum código foi executado.

## 1. Ressalvas sobre o material analisado

Três limitações importantes, porque afetam o que se pode afirmar:

1. **O pacote não está assinado.** Em `META-INF/` não há `MANIFEST.MF`, `*.SF` nem `*.RSA/EC`.
   Não é possível verificar autoria nem integridade — não dá para afirmar que este é o APK
   oficial publicado na Play Store, apenas que o conteúdo é coerente com ele.
2. **Os nomes de arquivo sofreram colisão de maiúsculas/minúsculas.** A extração foi feita em
   sistema de arquivos case-insensitive (Windows/macOS): 198 arquivos em `res/` aparecem com
   sufixo `1` (ex.: `dS.xml` → `dS1.xml`), e alguns pares como `res/fb.xml` / `res/fB.xml`
   colidiram. Um recurso — o `dataExtractionRules` — não pôde ser recuperado por causa disso.
3. **O código está ofuscado (R8).** 11.455 das 12.713 classes foram reempacotadas em `b.*`
   com nomes gerados; os metadados Kotlin foram removidos. A leitura de funcionalidade abaixo
   se apoia nas classes que o R8 preservou (as referenciadas pelo manifesto, por reflexão ou
   por serialização) e nas strings — não em decompilação completa.

## 2. Identidade e build

| Item | Valor |
|---|---|
| Package | `bitpit.launcher` |
| Versão | 1.16.28 (versionCode 1634) |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 (codename 17) |
| Android Gradle Plugin | 9.3.2 (Gradle 9.6.1) |
| Kotlin | 2.2.20 (JVM target 11) |
| Commit de origem | `98b1cf65a1e73c573943b335792cf92f252e28c7` (`META-INF/version-control-info.textproto`) |
| Classes / métodos / campos (DEX) | 12.713 / 63.093 / 44.917 |
| DEX | `classes.dex` (10,7 MB) + `classes2.dex` (1 KB, só `j$.time.DesugarDuration`) |
| Recursos | 1.446 arquivos em `res/`, `resources.arsc` de 5,2 MB, 46.346 strings |
| Idiomas | 116 locales (de `af` a `zu`) |
| ABIs nativas | armeabi-v7a, arm64-v8a, x86, x86_64 — apenas `libandroidx.graphics.path.so` e `libdatastore_shared_counter.so` (~96 KB total) |
| Otimizações | R8 com repackaging, `extractNativeLibs=false`, Baseline Profile em `assets/dexopt/` |

Observação de engenharia: 63.093 métodos num único DEX é confortável mas não folgado frente ao
limite de 65.536 referências por DEX — o `classes2.dex` existe só para uma classe de desugaring,
sinal de que o corte está sendo administrado pelo R8.

## 3. Manifesto

### 3.1 Permissões (26 + 1 própria)

Agrupadas por finalidade aparente:

- **Função de launcher:** `QUERY_ALL_PACKAGES`, `BIND_APPWIDGET`, `SET_WALLPAPER`,
  `SET_WALLPAPER_HINTS`, `EXPAND_STATUS_BAR`, `REQUEST_DELETE_PACKAGES`,
  `ACCESS_HIDDEN_PROFILES` (perfis privados do Android 15+), `VIBRATE`, `SET_ALARM`.
- **Conteúdo em tela:** `READ_CALENDAR`, `READ_CONTACTS`, `ACCESS_COARSE_LOCATION` (clima),
  `ACCESS_NOTIFICATION_POLICY`, `POST_NOTIFICATIONS`, `BLUETOOTH`/`BLUETOOTH_CONNECT`.
- **Uso e bem-estar digital:** `PACKAGE_USAGE_STATS`.
- **Rede/infra:** `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`,
  `RECEIVE_BOOT_COMPLETED`, `WRITE_EXTERNAL_STORAGE` (maxSdk 28).
- **Monetização e identidade:** `com.android.vending.BILLING`, `USE_BIOMETRIC`/`USE_FINGERPRINT`,
  `c2dm.permission.RECEIVE` (FCM), `BIND_GET_INSTALL_REFERRER_SERVICE`.
- **Publicidade/atribuição:** `com.google.android.gms.permission.AD_ID`,
  `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`. Não há SDK de exibição de anúncios;
  o uso é de atribuição de instalação (ver §7).
- **IA no dispositivo:** `com.google.android.apps.aicore.service.BIND_SERVICE` + `<queries>` para
  `com.google.android.aicore`.

`QUERY_ALL_PACKAGES` é esperado para um launcher, mas é a permissão de maior alcance do conjunto.

### 3.2 Componentes expostos

| Componente | Tipo | Proteção |
|---|---|---|
| `ui.HomeActivity` | activity | exported (é o launcher; concentra todos os deep links) |
| `notification.NotificationListener` | service | `BIND_NOTIFICATION_LISTENER_SERVICE` (só o sistema) |
| `lock_screen.LockScreenService` | service | `BIND_ACCESSIBILITY_SERVICE` (só o sistema) |
| `ui.PermissionUsageActivity` | activity | `START_VIEW_PERMISSION_USAGE` |
| `icon.icon_pack.ApplyIconPackActivity` | activity | exported, ação custom `bitpit.launcher.APPLY_ICONS`, **sem permissão** |
| `shortcut.AddItemActivity` | activity | exported para `CONFIRM_PIN_SHORTCUT`/`CONFIRM_PIN_APPWIDGET` (padrão) |
| `sesame.SesameConfigActivity` | activity | exported, ação `ninja.sesame.app.action.CONFIG_INTEGRATION` |
| `ninja.sesame.lib.bridge.v1.access.{RelayActivity,BeaconActivity}` | activities | exported, **sem permissão** |
| `ninja.sesame.lib.bridge.v1.access.{CommandProvider,IconProvider}` | providers | exported, **sem permissão declarada no manifesto** |
| `widget.AppWidgetsRestoredReceiver` | receiver | exported (broadcast do sistema) |

O serviço de acessibilidade é declarado de forma restrita (`res/DS.xml`):
`packageNames="bitpit.launcher"`, `canRetrieveWindowContent="false"` — ou seja, não lê conteúdo
de tela de outros apps; serve para bloquear a tela (ação global). É a configuração correta para
esse caso de uso.

### 3.3 Deep links e App Links

- Esquema próprio `niagara://` com hosts `agenda`, `search` e `secret-command`.
- App Links verificados (`autoVerify`): `https://niagaralauncher.app/app-link/`,
  `http(s)://niagaralauncher.com/app-link/`, `http(s)://nlaun.ch/c/`,
  `https://get.niagaralauncher.app`. O `assetlinks` aponta para
  `https://niagaralauncher.app/.well-known/assetlinks.json`.
- Alias `OpenNiagaraFile` para abrir arquivos `.nlb` (backup) e `.nlt` (tema) via `content://`
  e `file://`.
- Alias `CreateTheme` responde a `SEND`, `ATTACH_DATA` e `CROP_AND_SET_WALLPAPER` de `image/*`.

### 3.4 Backup

`allowBackup=true`. As regras (`res/fB.xml`) incluem `database/.` (exceto `leaks.db`),
`sharedpref/.` e `file/fonts` (exceto `fonts/no-backup` e `fonts/remote`). Ou seja, o banco
local — que inclui histórico de uso e metadados de notificação (§6) — entra no backup do
Android. O `dataExtractionRules` (que separa backup em nuvem de transferência device-to-device
no Android 12+) está declarado, mas o arquivo se perdeu na colisão de nomes da extração.

## 4. Arquitetura e stack

- **Linguagem/UI:** Kotlin com Views clássicas (`RecyclerView`, `ConstraintLayout`,
  `CoordinatorLayout`, Material Components) e **Compose parcial** (`compose.material3`,
  `runtime`, `ui`, `foundation`) — arquitetura híbrida, com Compose provavelmente em telas
  novas (settings, temas). Views customizadas próprias: relógios (analógico, flip, minimalista,
  horizontal, vertical, overlap), scrollbar alfabético, `WallpaperCropView`,
  `LaurelGoldShaderView` (usa `android.graphics.RuntimeShader`, AGSL).
- **DI:** Koin, inicializado via `androidx.startup` (`core.startup.KoinInitializer`).
- **Persistência:** Room (`savesystem.Database`) + DataStore (Preferences e Proto) + Protobuf.
- **Concorrência:** Coroutines/Flow; trabalho em background com WorkManager
  (`core.LauncherWorker`, workers de analytics, refresh de conta, upload de ícone custom).
- **Serialização:** kotlinx.serialization (JSON e CBOR) para os modelos de backend; Gson
  presente por dependência transitiva do Google.
- **Rede:** OkHttp (versão recente — há classes `okhttp3.internal.ech`, Encrypted Client Hello)
  **e** Cronet (`org.chromium.net`, provider do Play Services — não há `libcronet.so` embutido).
- **Mídia:** AndroidX Media3/ExoPlayer para os vídeos de onboarding/teasers em DASH (`.mpd`).
- **Imagens:** Coil 3; animações Lottie.

### 4.1 Bibliotecas de terceiros

A própria tela de licenças do app enumera as dependências (chaves `license_*` em
`resources.arsc`), o que dá uma lista autoritativa:

AndroidX, AOSP/Launcher3, Coil, Cronet, EvalEx, Firebase, Koin, Kotlin, libsu, Lottie,
Material Components, Modern Android Preferences (de.Maxr1998), Muzei API, OkHttp, Oklab,
Plaid (rounded image view), Timber, dots-indicator, looping-layout, jwt-decode (Auth0),
locale plugin, ícones Feather / Fluent / Phosphor, e assets de áudio (sons de moeda e bounce
do mini-jogo).

Do lado Google/Play: Billing 7.1.1, Play Core, Play Services (auth, measurement,
credentials/identity), Firebase (Analytics, Crashlytics, Messaging, Remote Config, Installations,
Sessions, ABT, DataTransport), ML Kit (`common` + **`genai`**) e AICore.

Duas escolhas chamam atenção:

- **libsu (`com.topjohnwu.superuser`)** — biblioteca de execução de shell root. Há também
  detecção de root (strings `isRooted`, `com.topjohnwu.magisk`, `/system/app/Superuser.apk`).
  Não consegui, sem decompilar o `b.*`, determinar se o root é apenas *detectado* (para
  diagnóstico/telemetria) ou *usado* para alguma função opcional. É o ponto que mais mereceria
  aprofundamento.
- **EvalEx** — motor de expressões que implementa a calculadora integrada da busca
  (documentada em `help.niagaralauncher.app/article/173-integrated-calculator`).

## 5. Funcionalidades identificadas

Mapeadas pelos pacotes preservados e pelas strings:

- **Lista alfabética / busca:** busca local + sugestões web com múltiplos motores (Google, Bing,
  Brave, DuckDuckGo, Startpage, Kagi, Perplexity), com possibilidade de desativação remota de um
  motor (`SearchEngineDisabledRemotelyException`).
- **At a Glance:** clima, agenda/calendário, relógios em vários estilos, perfis contextuais
  (`ContextualProfile`).
- **Notificações:** listener próprio, agrupamento/batching (`BatchedNotificationsActivity`),
  categorização e resumo (`notification_summary_enabled`).
- **GenAI no dispositivo:** `bitpit.launcher.genai` + ML Kit GenAI + AICore — sumarização local
  (compatível com o resumo de notificações), sem chamadas a LLM remoto detectadas.
- **Temas e ícones:** editor de temas, compartilhamento de temas (`.nlt`), pacotes de ícones,
  "Anycons" (com votação por app: `icon.anycon_voting.AppVoteRecord`), ícones custom com upload
  (`CustomIconUploadWorker`), fontes remotas, paletas Material You e cálculo de cor em Oklab,
  wallpapers próprios + integração Muzei.
- **Widgets:** pilha de widgets (`widget.stack.WidgetStackLayoutManager`), restauração de widgets
  após backup.
- **Bem-estar digital:** `AppUseSession`/`Activity` (sessões de uso locais), "usage breaker",
  pesquisas (`Survey`/`SurveyResponse`) ligadas à "digital wellbeing initiative".
- **Bloqueio de tela** por serviço de acessibilidade.
- **Backup/restore** em arquivo `.nlb`.
- **Integração Sesame** (busca profunda dentro de apps, via bridge `ninja.sesame.lib`).
- **Secret commands:** comandos acionáveis por `niagara://secret-command?cmd=…&key=…` e por
  app link, com diálogo de confirmação e uma chave que permite pular a confirmação
  (`bitpit.launcher.key.SECRET_COMMAND_SKIP_CONFIRMATION_KEY`).
- **Monetização:** Niagara Pro via Google Play Billing **e** via Stripe (modelos
  `backend.purchase.stripe.*`: `PricingOverview`, `PurchaseOffer`, `CustomerPortalResponse`),
  trial gratuito, programa de convites com recompensas (`invite`, `reward`), conta "Niagara ID"
  ("niagarald") com login por e-mail (código de verificação) e por Google, tokens JWT
  (jwt-decode) e biometria.
- **Onboarding com experimentos A/B:** variantes persistidas em banco
  (`OnboardingPaywallVariant`, `OnboardingTipsVariant`, `OnboardingValueExplainerVariant`,
  `OnboardingAlphabetTutorialVariant`, …), controladas por Firebase Remote Config
  (`onboarding_remote_config_fetched`, `pro_onboarding_bundle_flags`).

## 6. Dados persistidos localmente

Esquema Room recuperado dos comandos `CREATE TABLE` no DEX:

| Tabela | Conteúdo |
|---|---|
| `App`, `Shortcut`, `Widget`, `Feed` | inventário de apps/atalhos/widgets, rótulos, ícones (BLOB), datas, flags |
| `AppUseId`, `AppUseSession`, `Activity`, `Oc` | sessões de uso por app, duração, fuso, agregados diários |
| `Notification`, `NotificationChannel`, `NotificationTemp` | **metadados apenas**: `key_hash`, canal, `post_time`, `removed_time`, `importance`, `user_sentiment`, `removal_reason`, `category`, `flags` |
| `CustomIcon`, `RemoteFontMetadata` | personalização visual |
| `Purchase` | `purchase_token`, `sku`, `order_id`, `jwt` |
| `Post`, `VoteOption`, `Survey`, `SurveyResponse` | "message hub" e pesquisas, com `upload_timestamp` |
| `SimilarApp`, `ContextualProfile`, `Preference` | recomendação/contexto/preferências |

Ponto positivo relevante: **o conteúdo das notificações não é persistido**. A tabela guarda um
hash da chave e metadados — nada de título ou texto. Para um app com acesso ao
`NotificationListenerService`, é a decisão de design mais importante e está feita da forma
conservadora.

Em contrapartida, o histórico de uso por app é detalhado e entra no backup do Android (§3.4).

## 7. Telemetria, consentimento e privacidade

- **Coleta desligada por padrão no manifesto**: `firebase_analytics_collection_enabled=false`,
  `firebase_crashlytics_collection_enabled=false`, `firebase_performance_collection_enabled=false`,
  `google_analytics_adid_collection_enabled=false`, `firebase_messaging_auto_init_enabled=false`.
  A ativação depende de consentimento em runtime — há `onboarding.privacy_prompt`,
  `backend.analytics.consent.model.AnalyticsConsent` e `GdprConsentSyncWorker` (sincroniza o
  consentimento com o backend), além de detecção de região DMA (`is_dma_region`).
- **Atribuição de instalação**: SDK do Play Install Referrer + integração **Singular**
  (`SingularFirstSessionReportWorker`, `SingularSessionReportWorker`, modelos
  `SingularRequest`/`SingularConsentContext`/`DataSharingOptions`) — chamada pelo backend próprio,
  não pelo SDK oficial da Singular. É o que justifica as permissões de AD_ID/AdServices.
- **Backend próprio** ("niagarald") intermedia conta, compras (Play e Stripe), clima,
  categorização de apps, convites/recompensas, push (FCM), relatórios de uso
  (`backend.analytics.usage.model.UsageReport`) e "takeout" de dados
  (`backend.takeout.model.*` — exportação de dados do usuário, provável requisito GDPR).
- **Configuração Firebase** (visível no APK, como em qualquer app público): projeto
  `launcher-id`, sender `112739013535`, app id `1:112739013535:android:fad4210f4b2fe1f5`,
  bucket `launcher-id.appspot.com`, chave de API Android `AIzaSyArHfNgk5R_…` (chave de cliente,
  não é segredo — a proteção depende das regras/restrições do lado do Firebase).
- Nenhuma chave privada, credencial de serviço ou segredo real foi encontrado no pacote.

Hosts contactados (extraídos das strings): `niagaralauncher.app` / `.com`, `nlaun.ch`,
`help.niagaralauncher.app`, `wallpapers.niagaralauncher.app`, `videos.niagaralauncher.app`,
`launcher-id.firebaseio.com`, `app-measurement.com`, `play.google.com`, além dos motores de
busca escolhidos pelo usuário. Não há URL de API do backend em texto claro — os endpoints são
montados em runtime dentro do código ofuscado.

## 8. Observações de segurança

Nenhum indício de comportamento malicioso. Os pontos abaixo são superfície de ataque a
verificar, não vulnerabilidades comprovadas — confirmá-las exigiria decompilar o pacote `b.*`:

1. **`CommandProvider` e `IconProvider` (Sesame) exportados sem permissão no manifesto.** São
   componentes da biblioteca de integração; a validação do chamador provavelmente é feita em
   código (checagem de assinatura/pacote). Vale confirmar, porque um provider exportado é
   alcançável por qualquer app instalado.
2. **`ApplyIconPackActivity` exportada** com ação custom: qualquer app pode disparar a aplicação
   de um pacote de ícones. Impacto baixo (cosmético) se houver confirmação do usuário.
3. **`niagara://secret-command`** é alcançável por qualquer app e por página web (via App Link).
   Existe confirmação, mas também uma chave para pulá-la — a força dessa chave e o conjunto de
   comandos disponíveis determinam o risco real.
4. **`allowBackup=true` incluindo o banco** com histórico de uso; em dispositivos com ADB
   habilitado ou backup em nuvem, esses dados saem do aparelho.
5. **libsu embutida.** Se root for de fato usado (e não só detectado), a execução de shell
   privilegiado num launcher merece revisão dedicada.
6. **Cronet vindo do Play Services** — comportamento de TLS/rede depende de um componente
   externo atualizável; positivo para correções, mas fora do controle do app.

Do lado positivo: serviço de acessibilidade minimamente escopado, notificações sem persistência
de conteúdo, telemetria desligada por padrão, R8 com repackaging, sem bibliotecas nativas de
terceiros, sem SDK de anúncios, sem WebView exposta no manifesto.

## 9. O que ficaria de fora desta análise

Para ir além do que está aqui seria necessário: decompilar `classes.dex` (jadx/dex2jar) e
reconstruir o pacote `b.*` para (a) confirmar o uso real de root, (b) mapear os endpoints do
backend e o formato dos tokens, (c) auditar a validação de chamador nos providers exportados e
(d) listar os "secret commands". Também seria útil obter o APK **assinado** da Play Store para
verificar integridade e comparar com este pacote.
