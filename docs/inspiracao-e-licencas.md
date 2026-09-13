# Inspiração, código de terceiros e o que ficou de fora

Este projeto começou depois de uma análise estática do Niagara Launcher
(ver [`analise-niagara-launcher-1.16.28.md`](analise-niagara-launcher-1.16.28.md)).
Como a fronteira entre "inspirar-se" e "copiar" é a coisa mais fácil de errar num
projeto assim, ela está escrita aqui.

## O que o ZIP/APK analisado contém — e o que não contém

Um APK **não tem código-fonte**. O que existe ali é:

- `classes.dex` — bytecode Dalvik já processado pelo R8, com 11.455 das 12.713
  classes renomeadas para o pacote `b.*` e os metadados Kotlin removidos;
- `resources.arsc` e `res/` — recursos compilados (traduções, vetores, fontes);
- metadados de build.

Nada disso é fonte, e nada disso vem com concessão de licença.

## Sobre o "MIT" que aparece no app

O Niagara tem uma tela de licenças de código aberto. As chaves dela estão no
`resources.arsc` — `license_mit`, `license_apache_20`, e cerca de 28 entradas
`license_<projeto>` com autor e texto. **Esses rótulos são das dependências que o
app usa, não do código do app.** São bibliotecas públicas, cada uma com seu
próprio repositório:

| Creditado no app | O que é |
|---|---|
| `license_launcher_3`, `license_aosp` | AOSP / Launcher3 (Apache-2.0) |
| `license_ok_http`, `license_coil`, `license_lottie`, `license_koin`, `license_timber` | bibliotecas Android/Kotlin |
| `license_eval_ex` | motor de expressões (a calculadora da busca) |
| `license_jwt_decode` | decodificador de JWT da Auth0 |
| `license_libsu` | execução de shell root |
| `license_muzei` | API do Muzei |
| `license_feather_icons`, `license_fluent_icons`, `license_phosphor_icons` | conjuntos de ícones |
| `license_oklab` | fórmulas de espaço de cor Oklab |

Ou seja: **"reciclar o que é MIT" significa usar essas bibliotecas direto da
fonte** — uma linha no Gradle cada uma —, não extrair nada do APK. O código
próprio do Niagara (`bitpit.launcher.*` e o pacote `b.*`) é proprietário: não há
licença que o libere, e mesmo que houvesse, ele está ofuscado, sem metadados e
acoplado ao backend deles — decompilar renderia algo que não compila e não
funciona fora daquele servidor.

Antes de adicionar qualquer uma dessas bibliotecas aqui, confira a licença no
repositório oficial do projeto: a lista acima diz **que o Niagara as credita**,
não é uma auditoria de licença feita por nós.

## O que foi usado como inspiração (e é livre para ser)

Ideias e padrões de interação não são protegidos por direito autoral. O que
inspirou este projeto:

- lista alfabética vertical em vez de grade de ícones;
- índice de letras lateral, na zona alcançável pelo polegar;
- favoritos acima da lista;
- busca como caminho principal, e não um gesto escondido.

Além disso, a análise rendeu decisões de engenharia que aplicamos **ao
contrário** do que o app analisado faz, por escolha:

| Niagara 1.16.28 | Cascata |
|---|---|
| `QUERY_ALL_PACKAGES` | `<queries>` com filtro `MAIN`/`LAUNCHER` |
| 26 permissões | nenhuma |
| Firebase, Singular, backend próprio | sem rede |
| Banco com histórico de uso e metadados de notificação | só favoritos |
| Mesmas regras em `cloud-backup` e `device-transfer` | canais declarados separadamente |

## O que não entra aqui, em hipótese alguma

- Código decompilado do `b.*` ou de `bitpit.launcher.*`.
- Recursos do APK: traduções, vetores, fontes licenciadas, wallpapers assinados
  por artistas, conjuntos de ícones do app (Anycons).
- Marca, nome, identidade visual ou textos de interface do Niagara.
- Endpoints, tokens ou qualquer parte do backend deles.
- Qualquer mecanismo que contorne a validação de compra do Niagara Pro.
