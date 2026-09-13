# Changelog

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
