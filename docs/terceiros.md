# Código e assets de terceiros

Tudo que o Cascata embarca e não escreveu. Cada item entrou pelo canal
oficial (Maven ou repositório do projeto), com a licença conferida na origem.

## Bibliotecas (Gradle)

| Biblioteca | Licença | Uso |
|---|---|---|
| AndroidX (core, activity, lifecycle, datastore, compose, material3) | Apache-2.0 | base do app |
| Kotlin stdlib, kotlinx.coroutines | Apache-2.0 | linguagem e concorrência |
| kotlinx.serialization (json) | Apache-2.0 | arquivos `.cascata-theme` |
| EvalEx 3.7.0 | Apache-2.0 | calculadora na busca (o mesmo motor que o Niagara credita, entrando pelo Maven) |
| JUnit 4 | EPL-1.0 | só testes |

## Fontes (`app/src/main/res/font/`)

Todas sob **SIL Open Font License 1.1**; texto integral em `licenses/fonts/`.

| Fonte | Autores | Arquivo |
|---|---|---|
| Nunito | The Nunito Project Authors | `nunito.ttf` (variável, eixo `wght`) |
| Outfit | The Outfit Project Authors | `outfit.ttf` (variável, eixo `wght`) |
| Sora | The Sora Project Authors | `sora.ttf` (variável, eixo `wght`) |
| Atkinson Hyperlegible | Braille Institute of America | `atkinson_regular.ttf`, `atkinson_bold.ttf` |

## Só nas prévias de loja (fora do APK)

| Item | Licença | Uso |
|---|---|---|
| Path data dos Material Icons (google/material-design-icons) | Apache-2.0 | os símbolos brancos dos ícones dos apps fictícios das fotos da loja |

Os `d="..."` dos SVGs de 24x24 estão transcritos em
`app/src/screenshotTest/java/app/cascata/launcher/preview/DemoIcons.kt`. Esse
source set é só do renderizador de prévias (layoutlib, na JVM): nada dele entra
em APK nenhum, nem na `lite` nem na `full`.

## Convenções abertas (sem código de terceiros)

- **Pacotes de ícones**: o app lê o formato de `appfilter.xml` e responde aos
  intents `org.adw.launcher.THEMES`, `com.novalauncher.THEME` e
  `com.gau.go.launcherex.theme`. É uma convenção pública seguida por dezenas
  de launchers e pacotes; a implementação do leitor é nossa.
