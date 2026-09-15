# Changelog

## 1.1.1 — Abre mesmo degradado

Correções vindas do primeiro uso em aparelho real, onde a 1.1.0 fechava
sozinha na abertura sem deixar rastro.

- **Relatório de falha local.** Uma quebra passa a abrir uma tela com o
  erro e botões de copiar e compartilhar, em vez de o app sumir. O texto
  fica só no aparelho (`filesDir/ultima-falha.txt`), fora do backup, e
  pode ser apagado em Configurações → Sobre. A tela roda em processo
  próprio, sem depender de nada do app, e três travas impedem laço.
- **Nenhum recurso opcional derruba mais a tela inicial.** A varredura de
  perfis do aparelho (Secure Folder, trabalho, privado) descarta o perfil
  que recusar a consulta em vez de matar o processo; o escopo de
  corrotinas do app ganhou tratamento de exceção; os nove arquivos de
  preferências tratam leitura corrompida; o serviço de widgets pode
  faltar; as fontes do topo, o relógio e a leitura do papel de parede
  falham em silêncio. Falha vira recurso vazio, com aviso no relatório.
- **Fonte recusada pelo aparelho não quebra mais a composição:** a carga é
  conferida fora da tela e cai na fonte do sistema.
- Arraste dos favoritos morria no primeiro movimento (as linhas não tinham
  identidade própria); índice lateral podia travar ou estourar se a lista
  de apps mudasse durante o arraste; o gesto de subir não reabria a busca
  com o campo já em cena.
- No release, `SourceFile`/`LineNumberTable` preservados: sem isso todo
  rastro saía sem arquivo nem linha.
- 283 testes de unidade por edição (eram 248).

## 1.1.0 — Fase 10: sobre o papel de parede

- Tela inicial sem superfície por cima (opacidade padrão 0): a tinta do
  texto é escolhida pelo papel de parede (automático pelo
  `HINT_SUPPORTS_DARK_TEXT` do sistema, ou claro/escuro à mão), com sombra em
  toda a tipografia; a cor de destaque ganha piso de luminância para não
  sumir em fundo escuro. Telas com fundo próprio (configurações, seletor de
  widgets, boas-vindas, folhas e diálogos, cartão da calculadora) mantêm a
  paleta normal.
- Favoritos em lista vertical, como as linhas da gaveta, reordenáveis por
  toque longo e arraste; toque longo parado abre o menu do app. A linha
  horizontal continua como opção.
- Índice alfabético em onda: as letras se curvam ao redor do dedo, a letra
  sob ele vira uma bolha e a lista mostra só a seção daquela letra. Voltar,
  rolar, digitar ou tocar na estrela do topo devolve a tela inicial. O
  índice reto continua como opção.
- Campo de busca escondido até o gesto de subir (ou sempre visível, por
  opção).
- Fonte Nunito (SIL OFL) embutida e usada por padrão.
- Cinco preferências novas em Aparência; `.cascata-theme` e backups
  anteriores continuam válidos.
- Screenshots da loja regeradas com o visual novo (a imagem 2 mostra o
  índice em onda). 248 testes de unidade por edição.

## 1.0.0 — Fase 9: publicação

- Nove fases do plano fechadas; código, documentação e CI prontos para
  GitHub Releases (`release.yml`), F-Droid (`fastlane/metadata`, receita
  em `docs/publicacao.md`) e Play Store (checklist).
- `versionCode` por edição (101 lite, 102 full) para as lojas distinguirem os
  dois builds do mesmo pacote.
- Política de privacidade verificável em `docs/privacidade.md`: nenhum dado
  coletado ou enviado; a única conexão é o clima na edição `full`.
- Baseline Profile gerado no CI (`baseline.yml`) e commitado quando o
  gerador muda. Primeiros perfis versionados (≈ 700 métodos do próprio app
  marcados como hot/startup); a release passa a embutir
  `assets/dexopt/baseline.prof` com eles, ao custo de ≈ 100 KB no APK.
- Screenshots da ficha da loja (6 telas × pt-BR/en-US/es-ES, 1080×2400) em
  `fastlane/metadata`, renderizadas por layoutlib na JVM a partir de prévias
  do Compose (`app/src/screenshotTest`), sem emulador; `scripts/screenshots.sh`
  regenera. `scripts/benchmark.sh` roda os Macrobenchmarks num aparelho e
  confronta com os orçamentos.
- Correção: a data do relógio segue o padrão do idioma
  (`getBestDateTimePattern`); em inglês saía "Thursday, 12 de February".
- Fora isso, sem mudança funcional em relação à 0.9.0.

## 0.9.0 — Fase 8: backup, restore e polimento

- Backup em arquivo `.cascata-backup` (JSON versionado com todas as
  preferências e o layout de widgets); importar remapeia os perfis do
  aparelho novo e permite deixar os widgets de fora; "Apagar tudo" com
  confirmação. Sem histórico de uso nem notificações no arquivo.
- Onboarding de três telas no primeiro uso: tela inicial padrão, densidade,
  índice lateral.
- Inglês e espanhol completos (227 strings, 9 plurais), com `locale-config`
  para o seletor de idioma por app do Android 13+.
- Acessibilidade revisada: cabeçalhos, papéis, linhas mescladas, estados de
  seleção, interruptores alternáveis pela linha inteira, hora do relógio
  analógico em texto, regiões vivas.
- Baseline Profile: módulo `:baselineprofile` com gerador e Macrobenchmarks de
  cold start e rolagem; workflow manual/mensal que gera os perfis num
  emulador gerenciado e abre PR. `docs/performance.md`.
- 228 testes de unidade por variante (15 novos).

## 0.8.0 — Fase 7: uso do aparelho, opcional

- Acesso a estatísticas de uso concedido na tela do sistema, só ao ligar.
- Card "Uso hoje" no at-a-glance: tempo total e app mais usado; a folha lista
  os dez primeiros e permite definir um limite diário por app.
- Pausa deliberada: app com o limite do dia atingido abre uma folha de
  respiração com contagem (3 a 30 s) antes de "Abrir mesmo assim"; vale para
  a lista, os favoritos, o badge de notificação e a tecla de busca.
- Nada de histórico gravado: os eventos do dia vêm do sistema a cada consulta
  (cache de 60 s); só limites, pausa e card vão às preferências.
- Seção "Uso do aparelho" nas configurações com limites por app.
- 213 testes de unidade por variante (28 novos: agregação de sessões,
  virada de meia-noite, limites, duração).

## 0.7.0 — Fase 6: busca que resolve

- Calculadora na busca (EvalEx, Apache-2.0): "2+2", "15% de 200", "3,5*2",
  "sqrt(16)", "2^10"; toque copia o resultado.
- Atalhos de apps nos resultados (quando o Cascata é o launcher padrão).
- Contatos (`READ_CONTACTS` pedida só ao ligar): abrir, ligar, mensagem — tudo
  por intent.
- Configurações do sistema: 18 telas com sinônimos, só as que o aparelho tem.
- Buscar na web no motor escolhido (DuckDuckGo, Startpage, Brave, Ecosia,
  Google, Bing) abrindo o navegador — sem `INTERNET`, funciona na edição lite.
- Tolerância a um erro de letra em consultas de 4+ caracteres ("whatsap",
  "telgram").
- Tecla de busca do teclado abre o primeiro resultado.
- Seção Busca nas configurações. Durante a busca só aparecem resultados.
- 185 testes de unidade por variante (45 novos).

## 0.6.0 — Fase 5: widgets

- Widgets acima da lista de apps, largura total, altura em células de 72 dp;
  rolam junto com a lista.
- Pilha de widgets: vários no mesmo espaço com swipe horizontal e pontos.
- Botão de edição em cada widget: altura (−/+, dentro dos limites do
  provedor), mover para cima/baixo, adicionar à pilha, remover.
- Seletor agrupado por app com preview e altura mínima; o sistema pede a
  autorização de widgets uma única vez (`ACTION_APPWIDGET_BIND`) e a
  configuração do provedor quando exigida.
- Seção Widgets nas configurações com a lista dos slots.
- Restauração após backup: os ids realocados pelo sistema são remapeados
  (`APPWIDGET_HOST_RESTORED`); layout corrompido volta ao vazio em vez de
  derrubar a home.
- 140 testes de unidade por variante (22 novos: layout de slots e pilhas).

## 0.5.0 — Fase 4: notificações na lista

- Acesso a notificações concedido na tela do sistema (Android 11+ abre direto
  na chave do Cascata); o serviço só existe depois disso.
- Indicador na linha do app: contador ou ponto. Tocar expande as notificações
  daquele app na própria lista: título, texto, hora relativa, ações, resposta
  direta (`RemoteInput`), dispensar uma ou todas.
- Agrupamento por app e por `groupKey`: o resumo some quando há filhos.
- Silenciar por app; opção de não expandir na lista.
- Card de mídia no at-a-glance (título, artista, anterior / play-pause / próxima).
- Nada de notificação é gravado: tudo em memória enquanto está na barra.
- 118 testes de unidade por variante (32 novos: extração de texto por estilo,
  agrupamento, preferências, hora relativa).

## 0.4.0 — Fase 3: at a glance

- Duas edições do mesmo app: `lite` (sem `INTERNET`) e `full` (clima). Mesmo
  `applicationId`; instala-se uma ou outra. O release publica as duas.
- Relógio em quatro estilos: básico, dígitos grandes, duas linhas e analógico
  (desenhado em Canvas). O estilo vai junto no tema exportado.
- Cards abaixo do relógio, cada um ligado separadamente nas configurações:
  próximo alarme, bateria (com nível desenhado e indicação de carga), próximo
  evento da agenda (`READ_CALENDAR` pedida só ao ligar) e clima.
- Clima (edição `full`): Open-Meteo sem chave, localização grosseira via
  `LocationManager` (sem Play Services), coordenadas arredondadas a ~1 km, cache
  de 30 minutos, atualização ao voltar à home e por toque; °C ou °F.
  Desligar o card apaga o cache.
- Vetores próprios para bateria e nove condições de clima.
- CI roda testes e lint por variante e publica `Cascata-vX.Y.Z-full.apk` e
  `-lite.apk` assinados, cada um com sha256.
- 86 testes de unidade por variante (28 novos).

## 0.3.0 — Fase 2: aparência e identidade

- Tela de configurações (engrenagem ao lado do relógio, ou "configurações do
  app" no sistema): tudo grava na hora e a home repinta atrás.
- Modo escuro (sistema / claro / escuro).
- Cores: Material You, paleta derivada do papel de parede, ou cor de destaque
  (12 amostras + hex personalizado) — paleta calculada com contraste WCAG ≥ 4,5
  garantido.
- Opacidade do fundo sobre o papel de parede.
- Densidade da lista (compacta / padrão / confortável) e tamanho do texto.
- Fontes: do sistema, três embutidas sob SIL OFL (Outfit, Sora, Atkinson
  Hyperlegible) ou um arquivo TTF/OTF seu, importado via seletor de arquivos.
- Pacotes de ícones no formato aberto (`appfilter.xml`; ADW / Nova / Go).
- Trocar papel de parede pelo seletor do sistema.
- Exportar e importar tema (`.cascata-theme`, JSON) e restaurar padrões.
- Fonte importada acompanha o backup e a transferência entre aparelhos.
- 58 testes de unidade (35 novos: codec do tema, paleta, parser de
  `appfilter`, cabeçalho de fonte, limites das configurações).

## 0.2.0 — Fase 1: virar launcher de verdade

- Pedido para virar o launcher padrão via `RoleManager`, com folha de boas-vindas.
- Swipe-up na lista (ou no relógio) abre a busca com o teclado; Voltar limpa a busca.
- Toque longo abre uma folha com atalhos do app, fixar/desafixar, renomear,
  esconder, informações e desinstalar.
- Favoritos com ordem própria, reordenáveis por arraste.
- Apps escondidos saem da lista e da busca; ficam numa folha própria para reexibir.
- Apelidos por app; o app renomeado muda de posição e de seção na lista.
- Ícones legados desenhados dentro de um círculo, no mesmo tamanho dos adaptativos.
- Perfil privado do Android 15 listado com cadeado (`ACCESS_HIDDEN_PROFILES`,
  permissão normal).
- CI: testes, lint e APK debug em todo push; release assinado publicado por tag `v*`.
- 23 testes de unidade sobre a lógica pura de seções, busca, reordenação e preferências.

## 0.1.0

- Lista alfabética com seções, índice lateral, busca sem acento, favoritos,
  relógio, menu de contexto, perfis de trabalho, Material You.
