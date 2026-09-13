# Changelog

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
