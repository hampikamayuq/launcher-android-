package app.cascata.launcher.data.theme

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Teto do arquivo importado. Uma fonte de verdade cabe folgada; acima disso é outra coisa. */
private const val MAX_FONT_BYTES = 10L * 1024 * 1024

/** Tamanho do cabeçalho que identifica o formato. */
private const val HEADER_BYTES = 4

/**
 * O arquivo começa como uma fonte TrueType/OpenType? `0x00010000` (TTF),
 * `OTTO` (OpenType com contornos CFF) ou `true` (TTF da Apple). É só o
 * `sfntVersion` do começo do arquivo — barato, puro e testável.
 */
fun isFontHeader(bytes: ByteArray): Boolean {
    if (bytes.size < HEADER_BYTES) return false
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    val tag = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    return tag == 0x00010000 || tag == 0x4F54544F /* OTTO */ || tag == 0x74727565 /* true */
}

/**
 * A fonte que o usuário importa por SAF. Uma só: o app oferece "fonte do
 * sistema", as embutidas e *a* personalizada — guardar um acervo seria outra tela.
 */
class FontStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "fonts")
    private val target: File get() = File(dir, "custom.ttf")

    /**
     * O arquivo importado, ou null se não há nenhum. É lido no `onCreate` e no
     * `onResume` da home, então nem tocar no disco pode lançar daqui: sem
     * resposta, vale "não há fonte importada".
     */
    fun customFile(): File? = runCatching { target.takeIf { it.isFile } }.getOrNull()

    /**
     * Copia o conteúdo de [uri] para o diretório do app. Grava num temporário e
     * só renomeia no fim: uma importação que falha no meio não apaga a fonte que
     * já estava valendo.
     */
    suspend fun importCustom(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val temp = File(dir, "custom.tmp")
        runCatching {
            val header = ByteArray(HEADER_BYTES)
            var headerLength = 0
            var total = 0L
            val input = context.contentResolver.openInputStream(uri)
                ?: error("não foi possível abrir o arquivo")
            input.use { source ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        if (headerLength < HEADER_BYTES) {
                            val take = minOf(HEADER_BYTES - headerLength, read)
                            buffer.copyInto(header, headerLength, 0, take)
                            headerLength += take
                        }
                        total += read
                        require(total <= MAX_FONT_BYTES) { "a fonte passa de 10 MB" }
                        out.write(buffer, 0, read)
                    }
                }
            }
            require(isFontHeader(header.copyOf(headerLength))) {
                "o arquivo não é uma fonte TrueType/OpenType"
            }
            require(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) {
                "não foi possível gravar a fonte"
            }
        }.onFailure { temp.delete() }
    }

    suspend fun removeCustom() {
        withContext(Dispatchers.IO) { target.delete() }
    }
}
