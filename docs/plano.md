# Plano: do v0.1.0 ao APK publicável

Roteiro para levar o Cascata do protótipo que compila hoje até um launcher
completo, inspirado no conceito do Niagara (inclusive nas funções Pro), com
implementação própria. Cada fase termina num APK instalável que faz mais que o
anterior — não existe fase que "prepara terreno" sem entregar nada.

## 0. Onde estamos

v0.1.0 (commit `5fa72e1`): lista alfabética, índice lateral, busca, favoritos,
relógio, menu de contexto, atualização automática, perfis de trabalho. Release
com R8 em 1,29 MB, zero permissões, testes de unidade passando.

O que falta para chamar de launcher: virar o padrão do sistema com dignidade,
gestos, atalhos de app, esconder/renomear apps, notificações, widgets, "at a
glance", temas, backup. O plano abaixo é a ordem em que isso entra.

## 1. Regras que não mudam

1. **Código e assets originais.** Nada extraído do APK do Niagara, nunca. As
   bibliotecas que ele credita entram pelo Maven, depois de conferida a licença
   no repositório de origem (`docs/inspiracao-e-licencas.md`).
2. **Permissão só quando a função liga.** O app instala com zero permissões e
   cada recurso pede a sua na hora de ser ativado, com explicação de uma linha.
   Recurso desligado = permissão não pedida.
3. **Sem rede própria.** Busca na web abre o navegador por intent; clima usa um
   provedor sem chave e só quando o usuário ativa. O app nunca ganha
   `INTERNET` "por via das dúvidas".
4. **Nada de telemetria.** Sem Firebase, sem crash reporting remoto, sem
   atribuição. Bugs chegam pelo GitHub; logs são locais e opt-in.
5. **Dados de uso ficam no aparelho e fora do backup em nuvem.** Aprendido na
   análise: `cloud-backup` e `device-transfer` declarados separadamente, sempre.
6. **Orçamentos que quebram o build:** APK release < 4 MB; cold start < 500 ms
   num aparelho mediano; lista de 300 apps sem frame perdido no scroll.
7. **Toda fase fecha com:** testes passando, `lint` limpo, APK release
   gerado no CI, changelog escrito.

## 2. As fases

### Fase 1 — Virar launcher de verdade (v0.2)

Objetivo: dar para usar como home no dia a dia sem sentir falta do básico.

| Entrega | Como |
|---|---|
| Ser o launcher padrão | `RoleManager.createRequestRoleIntent(ROLE_HOME)` no primeiro uso; tela de boas-vindas de um passo |
| Botão voltar e Home | `OnBackPressedCallback` fecha busca/menus; Home limpa a busca e rola ao topo |
| Gesto de busca | Swipe-up na lista abre a busca com teclado (`Modifier.pointerInput` + `FocusRequester`) |
| Atalhos de app (long-press) | `LauncherApps.getShortcuts` — só funciona sendo o padrão (`hasShortcutHostPermission`); mostrar estáticos + dinâmicos + fixados |
| Desinstalar / esconder / renomear | `ACTION_DELETE` por intent (sem permissão); ocultos e apelidos no DataStore |
| Reordenar favoritos | Arraste na linha de favoritos (`LazyRow` + drag) |
| Ícones adaptativos coerentes | Máscara única (círculo/squircle) para ícones legados via `AdaptiveIconDrawable` |
| Perfil privado (Android 15) | `ACCESS_HIDDEN_PROFILES` **declarada** mas cadeado só aparece se o perfil existir |
| Keystore + CI | `release.keystore` fora do repo; GitHub Actions rodando `test`, `lint`, `assembleRelease` e anexando o APK a cada tag |

Critério de saída: alguém usa por uma semana e não volta para o launcher antigo
por falta de algo desta tabela.

### Fase 2 — Aparência e identidade (v0.3)

| Entrega | Como |
|---|---|
| Configurações | Tela própria em Compose; DataStore tipado (Proto) em vez de Preferences quando passar de ~15 chaves |
| Densidade e tamanho | Escala de fonte e altura de linha da lista (3 presets + fino) |
| Fontes | Fonte do sistema + 3–4 fontes livres (SIL OFL) embutidas; custom por arquivo via SAF |
| Cores | Material You por padrão; paleta extraída do wallpaper com `WallpaperManager.getWallpaperColors`; acento manual |
| Pacotes de ícones | Suporte ao formato aberto (`appfilter.xml`, intents `org.adw.launcher.THEMES` / `com.novalauncher.THEME`) — é convenção pública, não código de ninguém |
| Wallpaper | Seleção via `ACTION_SET_WALLPAPER`; sem galeria própria por enquanto |
| Temas exportáveis | JSON com cores, fonte, densidade, ícones; import/export por SAF (`.cascata-theme`) |

### Fase 3 — "At a glance" (v0.4)

Cada módulo é um card no topo, independente, e liga individualmente.

| Módulo | Fonte | Permissão |
|---|---|---|
| Relógio (4 estilos) | já existe; adicionar analógico, dígitos grandes, duas linhas | nenhuma |
| Próximo alarme | `AlarmManager.getNextAlarmClock()` | nenhuma |
| Bateria | `BatteryManager` / broadcast sticky | nenhuma |
| Próximo evento | `CalendarContract.Instances` | `READ_CALENDAR`, ao ligar |
| Clima | Open-Meteo (sem chave) via `HttpURLConnection`; cache de 30 min; localização grosseira | `INTERNET` + `ACCESS_COARSE_LOCATION`, ao ligar |
| Mídia em reprodução | `MediaSessionManager` | acesso a notificações (Fase 4) |

Regra: o card de clima é a **única** razão para `INTERNET` existir no manifesto,
e ele entra num *product flavor* separado (`full`) — o flavor `lite` compila sem
a permissão. Assim quem não quer rede tem um APK que não pode usá-la.

### Fase 4 — Notificações na lista (v0.5)

A assinatura do Niagara: a notificação aparece embaixo do app, na própria lista.

| Entrega | Como |
|---|---|
| `NotificationListenerService` | Exportado só com `BIND_NOTIFICATION_LISTENER_SERVICE`; pedido via `ACTION_NOTIFICATION_LISTENER_SETTINGS` quando o usuário liga |
| Ponto/contador por app | Estado em memória (`StateFlow`), zero persistência |
| Expansão inline | Título, texto, ações (`Notification.Action`) e resposta direta (`RemoteInput`) |
| Agrupamento | Por app e por `groupKey`; resumo colapsa |
| Silenciar por app | Lista de pacotes ignorados no DataStore |
| Persistência | **Nenhuma** por padrão. Se um dia houver histórico, guarda só o que a análise mostrou ser seguro: hash da chave, canal, horários, importância — nunca texto |

### Fase 5 — Widgets (v0.6)

| Entrega | Como |
|---|---|
| Host | `AppWidgetHost` + `AppWidgetHostView` dentro de `AndroidView`; `BIND_APPWIDGET` vem automaticamente para o launcher padrão |
| Seletor | `AppWidgetManager.getInstalledProviders()` agrupado por app, com preview |
| Layout | Widgets numa área acima da lista, largura total, altura em células; redimensionar por arraste |
| Pilha de widgets | Vários widgets no mesmo slot, com swipe horizontal (`HorizontalPager`) — ideia inspirada, implementação própria |
| Restauração | `AppWidgetHost.startListening` + `APPWIDGET_HOST_RESTORED` para sobreviver a backup/restore |

### Fase 6 — Busca que resolve (v0.7)

| Entrega | Como |
|---|---|
| Atalhos na busca | Incluir atalhos de app (Fase 1) nos resultados |
| Calculadora | **EvalEx** (Apache-2.0, a mesma que o Niagara credita) — resultado como primeira linha quando a query é expressão |
| Contatos | `ContactsContract` com `READ_CONTACTS` ao ligar; ligar/mensagem por intent |
| Web | Motor escolhido nas configurações; abre o navegador por `ACTION_VIEW` — sem `INTERNET` |
| Configurações do sistema | Índice estático de intents `Settings.ACTION_*` com rótulos traduzidos |
| Busca em texto | Já existe; adicionar tolerância a erro de 1 letra para rótulos > 4 caracteres |

### Fase 7 — Uso do aparelho, opcional (v0.8)

| Entrega | Como |
|---|---|
| Tempo por app | `UsageStatsManager` com `PACKAGE_USAGE_STATS` ao ligar (leva à tela do sistema) |
| Resumo diário | Card no "at a glance"; detalhe por app no long-press |
| Pausa deliberada | Limite por app com tela de "respira" antes de abrir — a ideia do *usage breaker*, feita do zero |
| Onde fica | Banco Room local, **excluído do `cloud-backup`**, incluído no `device-transfer` |

### Fase 8 — Backup, restore e polimento (v0.9)

| Entrega | Como |
|---|---|
| Backup em arquivo | JSON versionado (favoritos, ocultos, apelidos, tema, layout de widgets) via SAF; `.cascata-backup` |
| Restore com migração | Cada versão de esquema tem migração testada |
| Acessibilidade | TalkBack em toda a lista e índice; `contentDescription` por letra; fontes escaláveis |
| Idiomas | pt-BR, en, es no mínimo; `locale-config` para o seletor por app |
| Onboarding | Três telas: virar padrão, escolher densidade, explicar o índice lateral |
| Performance | Baseline Profile gerado por Macrobenchmark (não o padrão do AndroidX); medir cold start no CI |

### Fase 9 — Publicar (v1.0)

| Entrega | Como |
|---|---|
| Assinatura | Chave própria guardada fora do repo; considerar Play App Signing |
| Canais | GitHub Releases (APK universal) e F-Droid (o app não tem dependência proprietária — qualifica) ; Play Store se quiser alcance |
| Política de privacidade | Uma página: "o app não coleta nem envia dados" — e o manifesto prova |
| Build reproduzível | Wrapper fixado, versões travadas no catálogo, `--no-daemon`, mesmo SDK no CI |
| Listagem | Screenshots das fases 1, 3, 4 e 5; descrição curta focada na navegação |

## 3. Mapa: ideias do Niagara Pro → onde entram aqui

Ideias não têm dono; o que está listado é o *conceito*, reimplementado.

| Conceito (artigos do help deles) | Fase |
|---|---|
| Fontes personalizadas (art. 79) | 2 |
| Temas e compartilhamento (art. 193) | 2 |
| Pacotes de ícones | 2 |
| Relógios e "at a glance" (art. 23) | 3 |
| Clima | 3 |
| Notificações na lista | 4 |
| Pilha de widgets | 5 |
| Calculadora na busca (art. 173) | 6 |
| Usage breaker (art. 181) | 7 |
| Backup/restore (art. 179) | 8 |
| Bloqueio de tela por gesto (art. 73) | fora do plano — exige serviço de acessibilidade; só se pedirem muito, e escopado como o deles (`canRetrieveWindowContent=false`) |
| Anycons (ícones gerados) | fora do plano — é o asset mais autoral deles; não se reimplementa "inspirado" |

## 4. Arquitetura quando o projeto crescer

Um módulo até a Fase 3. A partir da Fase 4, quando entram serviço de
notificação e widget host, dividir:

```
:app                 # HomeActivity, navegação, tema
:core:apps           # LauncherApps, ícones, modelo AppEntry  (o que já existe em data/)
:core:prefs          # DataStore e migrações
:feature:glance      # cards do topo
:feature:notifications
:feature:widgets
:feature:search
```

Regra prática: um módulo só nasce quando dois lugares precisariam do mesmo
código. Antes disso é pacote.

## 5. Qualidade e CI

- **Unit:** normalização, seções, ranqueamento de busca, migrações de backup
  (já começou com `LabelTest`).
- **UI (Compose):** índice lateral leva à seção certa; busca vazia mostra
  mensagem; long-press abre menu.
- **Macrobenchmark:** cold start e scroll de 300 itens, rodando no CI num
  emulador `-no-window`.
- **Lint + ktlint** bloqueiam merge.
- **GitHub Actions:** `push` → test + lint + assembleDebug; `tag v*` →
  assembleRelease assinado + APK anexado ao release.

## 6. Riscos que já conhecemos

| Risco | Mitigação |
|---|---|
| Atalhos e widgets só funcionam sendo o launcher padrão | Detectar via `RoleManager.isRoleHeld` e explicar na UI, não falhar em silêncio |
| Play Store restringe apps com acesso a notificações | Justificativa de launcher é aceita; manter a persistência em zero facilita |
| Android 15 (perfil privado) e 16 mudam visibilidade | Testar em emulador API 35/36 no CI |
| Ícones em memória numa lista grande | `LruCache` já existe; rasterizar no tamanho exato (já feito); medir em Macrobenchmark |
| Fase 3 traz `INTERNET` para o manifesto | Flavor `lite` sem a permissão, sempre publicado junto |
| Escopo cresce e nada fecha | Cada fase é um APK; a próxima só começa depois da tag |

## 7. Primeiros passos concretos (Fase 1, nesta ordem)

1. Keystore local + workflow de CI com `test`/`lint`/`assembleRelease`.
2. `RoleManager` + tela de boas-vindas.
3. Swipe-up para busca com teclado.
4. Atalhos de app no long-press (depende de ser o padrão — daí a ordem).
5. Esconder e renomear apps.
6. Reordenar favoritos.
7. Tag `v0.2.0`, APK no GitHub Release.

## 8. Estado em 1.0

| Fase | Versão | Entregue / diferenças |
|---|---|---|
| 1 — Virar launcher de verdade | v0.2.0 | Entregue como planejado. Diferença: as tags de atalho de app (`LauncherApps.getShortcuts`) são as que o próprio app de cada pacote publica — o Cascata não define nenhuma própria, só lê e mostra. |
| 2 — Aparência e identidade | v0.3.0 | Entregue como planejado. |
| 3 — At a glance | v0.4.0 | Entregue como planejado (clima só na edição `full`). |
| 4 — Notificações na lista | v0.5.0 | Entregue como planejado. |
| 5 — Widgets | v0.6.0 | Entregue como planejado. |
| 6 — Busca que resolve | v0.7.0 | Entregue como planejado. |
| 7 — Uso do aparelho, opcional | v0.8.0 | Entregue **sem Room**: a ideia original citava um banco Room local; a implementação final não persiste histórico nenhum — os eventos do dia vêm do `UsageStatsManager` a cada consulta (cache de 60 s em memória), e só limites e duração da pausa vão ao DataStore. Zero uso persistido é uma garantia mais forte do que "Room fora do cloud-backup", então a mudança ficou. |
| 8 — Backup, restore e polimento | v0.9.0 | Entregue como planejado, com uma diferença de método: a acessibilidade foi revisada estaticamente (cabeçalhos, papéis, estados, regiões vivas conferidos no código e por inspeção), não com testes automatizados de TalkBack rodando no CI — não existe suíte de acessibilidade instrumentada no repositório. O Baseline Profile é gerado por CI agendado (`baseline.yml`), commitado como o plano previa, não gerado a cada build. |
| 9 — Publicar | v1.0.0 | Material desta fase: política de privacidade, metadata de loja (`fastlane/`), checklist das três vias (`docs/publicacao.md`). Diferença do plano original: as tags do perfil privado (Fase 1) dependem inteiramente do perfil existir e estar desbloqueado no aparelho de quem usa — não há dado de perfil publicado pelo Cascata, só o que o próprio Android já expõe. |

### Fora da 1.0

Ideias descartadas ou adiadas ao longo do plano, para não ficarem perdidas em
commits antigos:

- **Bloqueio de tela por acessibilidade.** Como já dizia a seção 3 (Mapa),
  exige um serviço de acessibilidade — escopo maior do que o resto do app
  pede, e só entraria sob demanda explícita.
- **Anycons** (ícones gerados automaticamente). É o asset mais autoral do
  Niagara; não se reimplementa "inspirado", como já registrado em
  [`docs/inspiracao-e-licencas.md`](inspiracao-e-licencas.md).
- **Sugestões de busca online.** Contrariaria a regra 3 ("sem rede própria")
  para a edição `lite` e adicionaria uma chamada de rede fora do card de
  clima na `full` — nenhuma das duas edições ganha com isso.
- **Badge de perfil de trabalho em ícones de pacote.** O ícone do perfil de
  trabalho (Fase 1) já é mostrado; o selo sobre o ícone de *pacotes de ícones*
  customizados especificamente não chegou a ser implementado.
- **Arrastar para redimensionar widgets.** A Fase 5 entregou redimensionar
  pelo botão de edição (−/+ dentro dos limites do provedor); o gesto de
  arrastar a borda do widget ficou de fora.
- **Animação de saída das folhas** (*bottom sheets*). As folhas (menu de
  contexto, edição de widget, uso) abrem com animação; fechar é instantâneo.
