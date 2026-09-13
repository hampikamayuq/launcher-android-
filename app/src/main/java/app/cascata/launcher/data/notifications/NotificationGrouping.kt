package app.cascata.launcher.data.notifications

/**
 * O que as regras puras precisam saber de uma notificação. [AppNotification]
 * implementa; os testes usam um objeto mínimo, porque `UserHandle` e
 * `PendingIntent` não existem fora do aparelho.
 */
interface NotificationFacts {
    val appKey: String
    val packageName: String
    val groupKey: String?
    val isGroupSummary: Boolean
    val postTimeMillis: Long
    val isMedia: Boolean
}

/** Teto por app: a lista de apps não pode virar a gaveta de notificações. */
private const val MAX_PER_APP = 10

/**
 * O que fica visível embaixo de cada app.
 *
 * O resumo de grupo (`FLAG_GROUP_SUMMARY`) é descartado quando os filhos dele
 * estão à vista — mostrar "5 novas mensagens" junto das 5 é ruído. Quando o app
 * só postou o resumo (acontece enquanto os filhos ainda não chegaram, e nos apps
 * que mandam só ele), o resumo é o que há e fica.
 *
 * Notificações contínuas (`isOngoing`) não são descartadas: quem decide como
 * mostrar "tocando", "baixando" ou "rodando em segundo plano" é a UI, pelo campo.
 */
fun <T : NotificationFacts> groupForDisplay(items: List<T>): List<T> {
    val groupsWithChildren = items
        .filterNot { it.isGroupSummary }
        .mapNotNull { it.groupKey }
        .toSet()

    val perApp = HashMap<String, Int>()
    return items
        .filterNot { n -> n.isGroupSummary && n.groupKey.let { it != null && it in groupsWithChildren } }
        .sortedByDescending { it.postTimeMillis }
        .filter { (perApp.merge(it.appKey, 1, Int::plus) ?: 1) <= MAX_PER_APP }
}

/**
 * O filtro que a home aplica em cima do store: recurso desligado não mostra
 * nada, pacote silenciado some, e mídia só aparece quando o usuário pediu (o
 * normal é a faixa tocando morar no card do topo, não na lista).
 */
fun <T : NotificationFacts> visibleNotifications(
    byApp: Map<String, List<T>>,
    settings: NotificationSettings,
): Map<String, List<T>> {
    if (!settings.enabled) return emptyMap()
    return byApp
        .mapValues { (_, list) ->
            list.filter { it.packageName !in settings.mutedPackages && (settings.showMedia || !it.isMedia) }
        }
        .filterValues { it.isNotEmpty() }
}
