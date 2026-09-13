package app.cascata.launcher.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.cascata.launcher.R
import java.io.File

/** Uma fonte embutida, escolhível nas configurações. */
data class BundledFont(val id: String, val label: String, val family: FontFamily)

/**
 * As fontes que o app carrega consigo — todas sob SIL Open Font License, com o
 * texto da licença em `licenses/fonts/`. Variáveis quando o projeto oferece
 * (um arquivo cobre todos os pesos), estáticas quando não.
 */
object Fonts {
    const val SYSTEM_ID = "system"
    const val CUSTOM_ID = "custom"

    val bundled: List<BundledFont> = listOf(
        BundledFont("outfit", "Outfit", variable(R.font.outfit)),
        BundledFont("sora", "Sora", variable(R.font.sora)),
        BundledFont(
            "atkinson",
            "Atkinson Hyperlegible",
            FontFamily(
                Font(R.font.atkinson_regular, FontWeight.Normal),
                Font(R.font.atkinson_bold, FontWeight.Bold),
            ),
        ),
    )

    /**
     * Família para um id de configuração. `system` e ids desconhecidos devolvem
     * null (= fonte do sistema); `custom` lê o arquivo importado, se existir.
     */
    fun family(id: String, customFile: File?): FontFamily? = when (id) {
        SYSTEM_ID -> null
        CUSTOM_ID -> customFile?.takeIf { it.isFile }?.let { file ->
            runCatching { FontFamily(Font(file)) }.getOrNull()
        }
        else -> bundled.firstOrNull { it.id == id }?.family
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
