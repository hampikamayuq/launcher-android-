package app.cascata.launcher.data

/**
 * Codificação do que vai para o DataStore, sem nada de Android — fica testável
 * em JVM e longe da classe que faz I/O.
 */

/** Prefixo das chaves dinâmicas de apelido: uma chave de preferência por app renomeado. */
const val ALIAS_PREFIX = "alias:"

/** Nome da preferência que guarda o apelido de [entryKey]. */
fun aliasPrefName(entryKey: String): String = ALIAS_PREFIX + entryKey

/** Caminho inverso: nome da preferência -> chave do app, ou null se não for um apelido. */
fun aliasEntryKey(prefName: String): String? =
    if (prefName.startsWith(ALIAS_PREFIX)) {
        prefName.substring(ALIAS_PREFIX.length).takeIf { it.isNotEmpty() }
    } else {
        null
    }

/**
 * Favoritos agora têm ordem, então viram uma string só, uma chave por linha.
 * Chave de app é `pacote/classe#usuário` — nunca contém quebra de linha.
 */
fun encodeKeys(keys: List<String>): String = keys.joinToString("\n")

/** Decodifica [encodeKeys]; linhas vazias e nulo viram lista vazia. */
fun decodeKeys(raw: String?): List<String> =
    if (raw.isNullOrEmpty()) emptyList() else raw.split('\n').filter { it.isNotEmpty() }
