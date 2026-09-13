package app.cascata.launcher.data.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Envelope do arquivo: o tema fica dentro de `theme` para que `format` e
 * `version` possam ser lidos antes de confiar no resto.
 */
@Serializable
private data class ThemeEnvelope(
    val format: String = "",
    val version: Int = 0,
    val theme: ThemeSettings = ThemeSettings.DEFAULT,
)

/**
 * O arquivo `.cascata-theme`: JSON puro, sem nada de Android, para que import e
 * export sejam testáveis em JVM. Quem faz o I/O (SAF) é a UI.
 */
object ThemeFile {
    const val MIME = "application/json"
    const val EXTENSION = "cascata-theme"

    /** Marca do formato. Um JSON qualquer com os mesmos campos não passa por engano. */
    const val FORMAT = "cascata-theme"

    /** Versão que este código escreve; lê tudo até ela. */
    const val VERSION = 1

    private val json = Json {
        prettyPrint = true
        // Campo novo escrito por uma versão futura não invalida o arquivo inteiro.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(settings: ThemeSettings): String =
        json.encodeToString(ThemeEnvelope(FORMAT, VERSION, settings.coerced()))

    /**
     * Devolve o tema do arquivo, ou a falha explicando o porquê — JSON inválido,
     * outro formato ou versão que este app ainda não sabe ler.
     */
    fun decode(text: String): Result<ThemeSettings> = runCatching {
        val envelope = json.decodeFromString<ThemeEnvelope>(text)
        require(envelope.format == FORMAT) { "não é um tema do Cascata: format=${envelope.format}" }
        require(envelope.version <= VERSION) { "tema de uma versão mais nova: version=${envelope.version}" }
        envelope.theme.coerced()
    }
}
