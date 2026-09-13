# Changelog

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
