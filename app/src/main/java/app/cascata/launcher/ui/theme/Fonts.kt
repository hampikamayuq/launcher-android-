package app.cascata.launcher.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import app.cascata.launcher.R
import app.cascata.launcher.crash.degraded
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Tag dos avisos deste arquivo: sem fonte, o app desenha com a do sistema. */
private const val TAG = "CascataFonts"

/**
 * Uma fonte embutida, escolhível nas configurações. [probeResId] é o arquivo que
 * [Fonts.family] manda o aparelho carregar antes de devolver a família — ver a
 * explicação lá.
 */
data class BundledFont(
    val id: String,
    val label: String,
    val family: FontFamily,
    val probeResId: Int,
)

/**
 * As fontes que o app carrega consigo — todas sob SIL Open Font License, com o
 * texto da licença em `licenses/fonts/`. Variáveis quando o projeto oferece
 * (um arquivo cobre todos os pesos), estáticas quando não.
 */
object Fonts {
    const val SYSTEM_ID = "system"
    const val CUSTOM_ID = "custom"

    val bundled: List<BundledFont> = listOf(
        // Arredondada e de rótulo curto: é a fonte padrão desde a Fase 10.
        BundledFont("nunito", "Nunito", variable(R.font.nunito), R.font.nunito),
        BundledFont("outfit", "Outfit", variable(R.font.outfit), R.font.outfit),
        BundledFont("sora", "Sora", variable(R.font.sora), R.font.sora),
        BundledFont(
            "atkinson",
            "Atkinson Hyperlegible",
            FontFamily(
                Font(R.font.atkinson_regular, FontWeight.Normal),
                Font(R.font.atkinson_bold, FontWeight.Bold),
            ),
            R.font.atkinson_regular,
        ),
    )

    /** O veredito por fonte, para não pedir o mesmo arquivo ao aparelho duas vezes. */
    private val loadable = ConcurrentHashMap<String, Boolean>()

    /**
     * Família para um id de configuração. `system` e ids desconhecidos devolvem
     * null (= fonte do sistema); `custom` lê o arquivo importado, se existir.
     *
     * O `FontFamily` que sai daqui é só uma descrição: quem realmente abre o
     * arquivo é o resolvedor do Compose, **dentro da composição** e no momento de
     * medir o primeiro texto. Uma fonte que o aparelho recusa (arquivo truncado
     * por um desligamento, fonte variável que a versão do FreeType daquele
     * aparelho não digere) lança ali — onde `runCatching` nenhum alcança, e onde
     * a exceção derruba a home inteira antes de ela aparecer.
     *
     * A defesa é carregar a fonte **aqui**, fora da composição, com [context]:
     * `ResourcesCompat.getFont` e `Typeface.createFromFile` fazem o mesmo trabalho
     * e lançam onde dá para pegar. Recusada, a família não é devolvida e o app
     * desenha com a fonte do sistema — feio, e aberto.
     *
     * Sem [context] (as prévias de screenshot, que não têm aparelho) a checagem é
     * pulada e o comportamento é o de antes.
     */
    fun family(id: String, customFile: File?, context: Context? = null): FontFamily? = when (id) {
        SYSTEM_ID -> null
        CUSTOM_ID -> customFile
            ?.takeIf { runCatching { it.isFile }.getOrDefault(false) }
            ?.takeIf { file -> usableFile(file) }
            ?.let { file -> runCatching { FontFamily(Font(file)) }.getOrNull() }
        else -> bundled.firstOrNull { it.id == id }
            ?.takeIf { font -> context == null || usableResource(font, context) }
            ?.family
    }

    /** O arquivo importado abre como `Typeface`? Se não, ele não vai para a composição. */
    private fun usableFile(file: File): Boolean = runCatching {
        Typeface.createFromFile(file) != null
    }.onFailure { degraded(TAG, "a fonte importada não carrega", it) }.getOrDefault(false)

    /**
     * O mesmo para uma fonte embutida. O veredito fica guardado por id: o
     * carregamento acontece uma vez por processo, e a troca de fonte nas
     * configurações não paga de novo.
     */
    private fun usableResource(font: BundledFont, context: Context): Boolean =
        loadable.getOrPut(font.id) {
            runCatching { ResourcesCompat.getFont(context, font.probeResId) != null }
                .onFailure { degraded(TAG, "a fonte ${font.id} não carrega neste aparelho", it) }
                .getOrDefault(false)
        }

    /** Fonte variável: o mesmo arquivo em quatro pesos, via eixo `wght`. */
    @OptIn(ExperimentalTextApi::class)
    private fun variable(resId: Int): FontFamily = FontFamily(
        listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
            Font(
                resId = resId,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        }
    )
}
