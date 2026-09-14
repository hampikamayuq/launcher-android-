# Performance

Como o Cascata mede e melhora cold start e fluidez de rolagem: o Baseline
Profile e os Macrobenchmarks que o produzem.

## 1. O que é um Baseline Profile

Quando o Android instala um APK, quase nada do código está compilado em código
de máquina. O ART interpreta e, conforme os métodos esquentam, o JIT compila —
o que significa que as *primeiras* aberturas do app são as mais lentas, e são
exatamente as que o usuário lembra.

Um **Baseline Profile** é uma lista de classes e métodos, em texto, que vai
empacotada no APK. Na instalação o ART compila em AOT tudo que está nela, antes
da primeira execução. O ganho típico numa lista em Compose fica entre 20% e 40%
no `timeToInitialDisplay` e, na rolagem, aparece na cauda: menos jank nos
primeiros segundos.

O arquivo não é gerado por adivinhação. Ele é a gravação do que o app realmente
executou durante um percurso guiado — é por isso que a geração precisa de um
aparelho ou emulador, e é por isso que ela não acontece num `assembleRelease`
qualquer.

## 2. Por que Macrobenchmark, e não o gerador padrão

O plugin `androidx.baselineprofile` aceita duas origens de perfil:

- **Gerador padrão do AndroidX** — cobre o startup genérico do Compose
  (inflar a janela, subir o runtime, primeira composição). É melhor do que
  nada, mas não conhece o Cascata.
- **Um módulo de Macrobenchmark** (o que este projeto usa) — grava o percurso
  que *este* app faz: abrir a `HomeActivity`, montar a lista de apps a partir
  do `LauncherApps`, rasterizar os ícones, compor a `LazyColumn` e rolá-la.

A diferença importa porque o custo de abertura do Cascata não está no Compose:
está em ler a lista de aplicativos instalados, normalizar rótulos e montar as
seções. Nada disso entra num perfil genérico. O plano (Fase 8) pede
explicitamente "Baseline Profile gerado por Macrobenchmark (não o padrão do
AndroidX)" por essa razão.

O mesmo módulo que grava o perfil também **mede** — cold start e rolagem — o
que fecha o ciclo: dá para provar que o perfil melhorou alguma coisa em vez de
supor.

## 3. As peças

| Onde | O quê |
|---|---|
| `:baselineprofile` | Módulo `com.android.test`, `minSdk 28`, mesmas flavors do `:app`. Não entra em APK publicado. |
| `BaselineProfileGenerator.kt` | Grava o perfil (`BaselineProfileRule`). |
| `StartupBenchmark.kt` | Cold start com e sem perfil (`StartupTimingMetric`). |
| `ScrollBenchmark.kt` | Rolagem da gaveta (`FrameTimingMetric`). |
| `Interactions.kt` | Intent de abertura e o gesto de rolagem, compartilhados pelos três. |
| `.github/workflows/baseline.yml` | Geração mensal/sob demanda; abre PR com o resultado. |

O módulo exige **API 28+** porque é o piso do Macrobenchmark (é de lá que vêm a
instrumentação de perfil e as métricas de frame). O `:app` continua em
`minSdk 26` — os dois nunca são instalados juntos num aparelho antigo.

### Por que o intent de abertura é explícito

O Cascata é um launcher: a `HomeActivity` declara `CATEGORY_HOME`. O
`startActivityAndWait()` sem argumento resolve `ACTION_MAIN` + `CATEGORY_HOME`,
ou seja, **o launcher padrão do sistema** — num emulador limpo isso abre o
launcher do AOSP, não o Cascata. O perfil sairia vazio e o benchmark estouraria
esperando um frame que nunca viria do nosso processo.

Por isso `Interactions.launcherIntent()` monta o intent no pacote:

```kotlin
Intent(Intent.ACTION_MAIN).apply {
    setPackage("app.cascata.launcher")
    addCategory(Intent.CATEGORY_LAUNCHER)
}
```

A `HomeActivity` declara `CATEGORY_LAUNCHER` junto com `CATEGORY_HOME`, então o
alvo é sempre o nosso APK — esteja ele definido como launcher padrão ou não.

O `packageName` usado é `app.cascata.launcher`, **sem** o sufixo `.debug`: o
plugin gera e mede a partir das variantes `nonMinifiedRelease`, que herdam o
`applicationId` da release.

## 4. Gerar o perfil

### Pelo CI (o caminho normal)

Actions → **Baseline Profile** → *Run workflow*. Também roda sozinho no dia 1º
de cada mês. O job habilita KVM no runner, sobe o emulador gerenciado
`pixel6Api34` e, se algum perfil mudou, abre um PR `chore/baseline-profile`.

Há um terceiro gatilho: qualquer push que altere `baselineprofile/**` ou o
próprio `baseline.yml`. Nesse caso não há PR: os perfis regerados voltam num
commit `chore: atualiza Baseline Profiles (gerados no CI)` na mesma branch.
Quem tem a branch em checkout precisa dar `git pull` antes do próximo push.

Os perfis já estão versionados em `app/src/{lite,full}Release/generated/`
(primeira geração: run #1 do workflow, ~10 min no runner, cerca de 15 mil
linhas por perfil, ~700 delas de classes do próprio Cascata). A release os
consome automaticamente; nenhum passo extra no build.

### Localmente, com emulador gerenciado pelo Gradle

Não precisa de AVD manual: o Gradle baixa e sobe a imagem sozinho. Precisa de
KVM (Linux) ou HVF (macOS).

```bash
./gradlew :app:generateLiteReleaseBaselineProfile
./gradlew :app:generateFullReleaseBaselineProfile
```

Para regerar as duas edições de uma vez: `./gradlew :app:generateBaselineProfile`.

### Localmente, com um aparelho plugado

O módulo está configurado com `useConnectedDevices = false` de propósito (para
o CI nunca cair num aparelho por acidente). Para usar o aparelho da mesa sem
editar o build, force pela linha de comando — a propriedade ignora os managed
devices e usa só o que estiver plugado:

```bash
./gradlew :app:generateLiteReleaseBaselineProfile \
  -Pandroidx.baselineprofile.forceonlyconnecteddevices=true
```

O aparelho precisa ser **rooted ou `userdebug`** — um aparelho de varejo
bloqueia a leitura do perfil do ART. Num aparelho de varejo, use o emulador.

## 5. Onde o arquivo fica — e por que é commitado

Com `saveInSrc = true`, a geração escreve em:

```
app/src/liteRelease/generated/baselineProfiles/baseline-prof.txt
app/src/liteRelease/generated/baselineProfiles/startup-prof.txt
app/src/fullRelease/generated/baselineProfiles/baseline-prof.txt
app/src/fullRelease/generated/baselineProfiles/startup-prof.txt
```

Um conjunto por variante, porque `lite` e `full` compilam código diferente (o
card de clima só existe na `full`).

**Esses arquivos entram no Git.** É o que faz um `./gradlew assembleRelease`
comum — inclusive o de quem só clonou o repositório, sem emulador — já sair com
o perfil embutido. Sem commitar, o perfil só existiria na máquina de quem
rodou a geração, e o CI de release passaria a depender de KVM. Por isso também
`automaticGenerationDuringBuild = false`: gerar é um passo explícito, agendado,
revisado por PR — não um efeito colateral de compilar.

O `startup-prof.txt` alimenta o `dexLayoutOptimization = true`: o R8 agrupa as
classes do caminho de abertura no começo do dex, o que reduz page faults no
cold start. Depende de `isMinifyEnabled = true`, que a release já tem.

A dependência `androidx.profileinstaller` no `:app` é o que aplica o perfil na
primeira execução, sem esperar o Play Store entregar um perfil na nuvem — o que
importa num app distribuído por GitHub Releases e F-Droid.

## 6. Rodar os benchmarks

**O CI não roda benchmark, só geração.** Medir tempo num runner compartilhado,
num emulador sem GPU, produz números que variam mais do que qualquer regressão
que a gente fosse detectar. O emulador serve para *gravar* um percurso; não
serve para cronometrar.

Então benchmark se roda na mão, num aparelho real:

```bash
# Cold start, com e sem perfil
./gradlew :baselineprofile:connectedLiteBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.cascata.launcher.baselineprofile.StartupBenchmark

# Rolagem
./gradlew :baselineprofile:connectedLiteBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.cascata.launcher.baselineprofile.ScrollBenchmark

# Tudo
./gradlew :baselineprofile:connectedLiteBenchmarkReleaseAndroidTest
```

Troque `Lite` por `Full` para medir a outra edição. No emulador gerenciado
(números só comparáveis entre si, nunca com um aparelho):

```bash
./gradlew :baselineprofile:pixel6Api34LiteBenchmarkReleaseAndroidTest
```

Para o aparelho valer como bancada: tela ligada e destravada, sem tocar nele
durante a medição, bateria acima de 30%, modo avião ligado. O AndroidX recusa
medir em aparelho debuggable ou com governor de CPU instável — leia a mensagem
de erro antes de suprimi-la com `androidx.benchmark.suppressErrors`.

Resultados:

- resumo no console do Gradle;
- JSON completo em `baselineprofile/build/outputs/connected_android_test_additional_output/`;
- traces Perfetto ao lado, abríveis em <https://ui.perfetto.dev>.

### Como ler as métricas

**`StartupTimingMetric`** reporta, em milissegundos:

- `timeToInitialDisplayMs` — do toque ao primeiro frame desenhado. É o número
  que o orçamento cobra.
- `timeToFullDisplayMs` — até o app chamar `reportFullyDrawn()`; só aparece se
  o app chamar.

Cada um vem com `min`, `median` e `max` sobre as 5 iterações. **Use a mediana.**
O `min` é sorte e o `max` costuma ser a primeira iteração, ainda com cache frio.

Os dois testes de `StartupBenchmark` existem para serem comparados:
`startupSemPerfil` (`CompilationMode.None()`, tudo interpretado — o que o
usuário vê recém-instalado) contra `startupComPerfil`
(`CompilationMode.Partial()`, perfil aplicado). A diferença entre as duas
medianas é o ganho real do perfil. **Se for perto de zero, o percurso gravado
no gerador não é o que o app executa no startup** — conserte o gerador, não a
medição.

**`FrameTimingMetric`** reporta `frameDurationCpuMs` em percentis (P50, P90,
P95, P99) e, em API 31+, `frameOverrunMs` — quanto o frame passou do prazo
(negativo é bom: sobrou tempo). Aqui o que importa é a **cauda**, não a
mediana: um P50 ótimo com P99 de 40 ms é uma rolagem que trava visivelmente.

## 7. Orçamentos (do plano, Fase 8)

| Métrica | Orçamento | Onde conferir |
|---|---|---|
| Cold start num aparelho mediano | `timeToInitialDisplayMs` mediana **< 500 ms** | `StartupBenchmark.startupComPerfil` |
| Rolagem de **300 apps** | **sem frame perdido**: `frameOverrunMs` P99 ≤ 0, ou `frameDurationCpuMs` P99 dentro do orçamento de frame (~16,6 ms a 60 Hz, ~8,3 ms a 120 Hz) | `ScrollBenchmark.scrollGaveta` |

"Aparelho mediano" é a referência do plano: algo na faixa de um Pixel 6a ou
equivalente, não o topo de linha do ano.

Para exercitar o orçamento de 300 apps é preciso ter 300 apps instalados (ou
visíveis) no aparelho de teste — num aparelho com 60 apps a rolagem passa em
qualquer configuração e a medição não diz nada.

Quando um orçamento estourar, a ordem de investigação que costuma pagar:

1. Rode o `StartupBenchmark` com e sem perfil. Se não houver diferença, o
   problema é o perfil (percurso gravado errado), não o app.
2. Abra o trace Perfetto da pior iteração e procure o que roda na main thread
   antes do primeiro frame — leitura de disco, `LauncherApps`, DataStore.
3. Só depois mexa em Compose. O `LruCache` de ícones e a rasterização no
   tamanho exato já existem; verifique se continuam no caminho.
