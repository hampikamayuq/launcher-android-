package app.cascata.launcher.data.backup

import app.cascata.launcher.data.glance.GlanceSettings
import app.cascata.launcher.data.notifications.NotificationSettings
import app.cascata.launcher.data.search.SearchSettings
import app.cascata.launcher.data.theme.ThemeSettings
import app.cascata.launcher.data.theme.coerced
import app.cascata.launcher.data.usage.UsageSettings
import app.cascata.launcher.data.widgets.WidgetLayout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tudo o que o launcher persiste, num objeto só. É o conteúdo do arquivo
 * `.cascata-backup` (ver [BackupFile]) e o que [BackupManager] lê e grava.
 *
 * Portabilidade das chaves: favoritos, ocultos e apelidos são chaves de
 * `AppEntry` — `pacote/classe#hash`, onde o hash é o do `UserHandle`. Esse hash
 * é do aparelho: o mesmo perfil de trabalho tem outro número em outro celular, e
 * até o perfil principal pode mudar depois de um reset. Por isso a restauração
 * passa por [remapUserHashes] antes de gravar: chave cujo hash não existe mais
 * vai para o perfil principal, onde o componente provavelmente está.
 *
 * Widgets: o `appWidgetId` também é do aparelho — quem o aloca é o
 * `AppWidgetHost` local. O layout é restaurado como está e os ids que não valem
 * mais viram "Widget indisponível" na home (a moldura já trata isso), para o
 * usuário recolocar o widget no lugar certo. Quem prefere não ver molduras
 * vazias restaura com [withoutWidgets].
 */
@Serializable
data class BackupPayload(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val aliases: Map<String, String> = emptyMap(),
    val theme: ThemeSettings = ThemeSettings.DEFAULT,
    val glance: GlanceSettings = GlanceSettings.DEFAULT,
    val notifications: NotificationSettings = NotificationSettings.DEFAULT,
    val search: SearchSettings = SearchSettings.DEFAULT,
    val usage: UsageSettings = UsageSettings.DEFAULT,
    val widgets: WidgetLayout = WidgetLayout.EMPTY,
)

/**
 * Envelope do arquivo: o conteúdo fica dentro de `payload` para que `format` e
 * `version` possam ser lidos antes de confiar no resto. `createdAtMillis` e
 * `appVersion` não são usados na leitura — servem para quem abrir o arquivo
 * saber de onde ele veio.
 */
@Serializable
private data class BackupEnvelope(
    val format: String = "",
    val version: Int = 0,
    val createdAtMillis: Long = 0L,
    val appVersion: String = "",
    val payload: BackupPayload = BackupPayload(),
)

/**
 * O arquivo `.cascata-backup`: JSON puro, sem nada de Android, para que import e
 * export sejam testáveis em JVM. Quem faz o I/O (SAF) é a UI.
 */
object BackupFile {
    /** Marca do formato. Um JSON qualquer com os mesmos campos não passa por engano. */
    const val FORMAT = "cascata-backup"

    /** Versão que este código escreve; lê tudo até ela, migrando o que for mais antigo. */
    const val VERSION = 1

    const val MIME = "application/json"
    const val EXTENSION = "cascata-backup"

    private val json = Json {
        prettyPrint = true
        // Campo novo escrito por uma versão futura não invalida o arquivo inteiro.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * O arquivo pronto para gravar. [createdAtMillis] é parâmetro para o teste
     * não depender do relógio.
     */
    fun encode(
        payload: BackupPayload,
        appVersion: String = "",
        createdAtMillis: Long = System.currentTimeMillis(),
    ): String = json.encodeToString(
        BackupEnvelope(FORMAT, VERSION, createdAtMillis, appVersion, payload.normalized()),
    )

    /**
     * Devolve o conteúdo do arquivo, ou a falha explicando o porquê — JSON
     * inválido, outro formato ou versão que este app ainda não sabe ler.
     */
    fun decode(text: String): Result<BackupPayload> = runCatching {
        val envelope = json.decodeFromString<BackupEnvelope>(text)
        require(envelope.format == FORMAT) {
            "não é um backup do Cascata: format=${envelope.format}"
        }
        require(envelope.version <= VERSION) {
            "backup de uma versão mais nova do Cascata (version=${envelope.version}, " +
                "esta lê até $VERSION) — atualize o app para restaurá-lo"
        }
        migrate(envelope.payload, envelope.version).normalized()
    }

    /**
     * Um degrau por versão: `1 to { ... }` leva o conteúdo da v1 para a v2.
     * Enquanto só existir a 1 o mapa fica vazio — mas é ele que garante que a
     * migração futura entre num lugar só, com teste próprio.
     */
    private val migrations: Map<Int, (BackupPayload) -> BackupPayload> = emptyMap()

    /** Sobe o conteúdo da versão em que o arquivo foi escrito até [VERSION]. */
    private fun migrate(payload: BackupPayload, fromVersion: Int): BackupPayload {
        var current = payload
        for (from in fromVersion until VERSION) {
            current = migrations[from]?.invoke(current) ?: current
        }
        return current
    }
}

/** Põe cada campo dentro do que a UI sabe desenhar, como na leitura das preferências. */
private fun BackupPayload.normalized(): BackupPayload =
    copy(theme = theme.coerced(), usage = usage.coerced())

/**
 * Adapta as chaves ao aparelho onde o backup está sendo restaurado.
 * [knownHashes] são os `UserHandle.hashCode()` dos perfis que existem agora
 * (principal, trabalho, privado); [primaryHash] é o do perfil principal.
 *
 * Chave cujo hash já existe fica como está — é o mesmo perfil. Chave com hash
 * desconhecido vira `componente#primaryHash`: o app de trabalho do aparelho
 * antigo passa a apontar para a cópia pessoal, que é o que quase sempre existe
 * do outro lado. Chave sem `#` ou com sufixo que não é número fica intacta: não
 * dá para adivinhar o que ela era.
 *
 * Duas chaves de perfis diferentes podem colidir depois do remapeamento; vence a
 * primeira, que nos favoritos é também a de cima na lista.
 *
 * Os widgets não passam por aqui: o `userHash` deles anda junto com o
 * `appWidgetId`, que também não sobrevive à troca de aparelho.
 */
fun BackupPayload.remapUserHashes(knownHashes: Set<Int>, primaryHash: Int): BackupPayload {
    fun remap(key: String): String {
        val (component, hash) = splitEntryKey(key) ?: return key
        return if (hash in knownHashes) key else "$component#$primaryHash"
    }
    return copy(
        favorites = favorites.map(::remap).distinct(),
        hidden = hidden.mapTo(LinkedHashSet(), ::remap),
        aliases = buildMap {
            aliases.forEach { (key, alias) ->
                val remapped = remap(key)
                if (!containsKey(remapped)) put(remapped, alias)
            }
        },
    )
}

/** O mesmo backup sem widgets — para a UI oferecer "restaurar sem widgets". */
fun BackupPayload.withoutWidgets(): BackupPayload = copy(widgets = WidgetLayout.EMPTY)

/**
 * Quebra `pacote/classe#hash` nas duas partes, o inverso de `AppEntry.key`.
 * Null quando não há `#` ou o sufixo não é um número.
 */
private fun splitEntryKey(key: String): Pair<String, Int>? {
    val cut = key.lastIndexOf('#')
    if (cut <= 0 || cut == key.lastIndex) return null
    val hash = key.substring(cut + 1).toIntOrNull() ?: return null
    return key.substring(0, cut) to hash
}
