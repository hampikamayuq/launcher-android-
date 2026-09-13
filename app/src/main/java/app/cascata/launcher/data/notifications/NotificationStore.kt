package app.cascata.launcher.data.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * NADA DE NOTIFICAÇÃO É PERSISTIDO. Nem título, nem texto, nem a chave, nem o
 * horário — nem em DataStore, nem em arquivo, nem em log. Tudo o que está aqui
 * vive em memória enquanto a notificação estiver na barra do sistema; quando o
 * serviço desconecta ou o processo morre, some junto.
 *
 * Ao DataStore vão só preferências (silenciados, estilo do indicador), em
 * `NotificationPrefs`. Foi a decisão mais importante da análise do Niagara
 * (docs/analise-niagara-launcher-1.16.28.md, §6) e ela fica assim.
 *
 * É um `object` porque quem alimenta o store é o `NotificationListenerService`,
 * instanciado pelo sistema — não há construtor onde injetar nada. O store não
 * guarda `Context` nem escopo, então não há o que vazar. `CascataApp` expõe a
 * mesma instância para o ViewModel não ir buscar um global por conta própria.
 */
object NotificationStore {

    /** O que o store precisa do serviço. Assim `data/` não depende do pacote dele. */
    fun interface Canceller {
        fun cancel(keys: Array<String>)
    }

    private val lock = Any()

    /** Chave da notificação -> notificação, cru, antes do agrupamento. */
    private val raw = MutableStateFlow<Map<String, AppNotification>>(emptyMap())

    private val _connected = MutableStateFlow(false)

    /** O serviço está conectado? Sem isso, a lista não tem o que mostrar. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _byApp = MutableStateFlow<Map<String, List<AppNotification>>>(emptyMap())

    /** Por `appKey`, já passado por [groupForDisplay]. */
    val byApp: StateFlow<Map<String, List<AppNotification>>> = _byApp.asStateFlow()

    @Volatile
    private var canceller: Canceller? = null

    fun connect(canceller: Canceller) {
        this.canceller = canceller
        _connected.value = true
    }

    /** Desconectou: o que estava na barra deixou de ser observável, então esvazia. */
    fun disconnect() {
        canceller = null
        _connected.value = false
        mutate { emptyMap() }
    }

    /** Troca tudo de uma vez — é o `activeNotifications` inteiro, ao conectar. */
    fun publish(all: List<AppNotification>) = mutate { all.associateBy { it.key } }

    fun posted(notification: AppNotification) = mutate { it + (notification.key to notification) }

    fun removed(key: String) = mutate { if (key in it) it - key else it }

    /** Cancela no sistema; a remoção do mapa vem pelo callback. No-op desconectado. */
    fun dismiss(key: String) {
        canceller?.cancel(arrayOf(key))
    }

    fun dismissAll(appKey: String) {
        val keys = raw.value.values.filter { it.appKey == appKey }.map { it.key }
        if (keys.isNotEmpty()) canceller?.cancel(keys.toTypedArray())
    }

    /**
     * Os callbacks do listener chegam em thread do sistema: a mutação é atômica
     * (`updateAndGet`) e a projeção derivada é publicada dentro do mesmo lock,
     * para o mapa visível nunca ficar atrás do conjunto cru.
     */
    private fun mutate(block: (Map<String, AppNotification>) -> Map<String, AppNotification>) {
        synchronized(lock) {
            val next = raw.updateAndGet(block)
            _byApp.value = next.values
                .groupBy { it.appKey }
                .mapValues { (_, items) -> groupForDisplay(items) }
        }
    }
}
