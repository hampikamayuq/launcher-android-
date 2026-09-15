package app.cascata.launcher.preview

import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser
import app.cascata.launcher.data.AppEntry
import app.cascata.launcher.data.IconSource
import app.cascata.launcher.data.LoadedIcon
import kotlin.math.min

/**
 * Os ícones dos apps fictícios das prévias de loja: um quadrado arredondado com
 * degradê de duas tonalidades e um símbolo branco no meio. Nada de marca — o
 * desenho é nosso, e o símbolo é um ícone do Material, que é livre.
 *
 * Isto vive só no source set `screenshotTest`: nenhum byte daqui entra em APK.
 */

/** Raio do canto, em fração do lado — a mesma proporção da máscara adaptativa do Android. */
private const val CORNER_FRACTION = 0.22f

/** Quanto do lado o símbolo ocupa. O resto é a margem que o faz respirar. */
private const val SYMBOL_FRACTION = 0.55f

/** O símbolo do Material é desenhado numa grade de 24x24; é dela que vem a escala. */
private const val SYMBOL_VIEWPORT = 24f

/** Tamanho intrínseco do ícone, em px: só um padrão, porque a lista sempre pede o seu. */
private const val INTRINSIC_PX = 108

/**
 * O papel de um app fictício. O rótulo muda de idioma para idioma — Mensagens,
 * Messages, Mensajes — mas o papel não, e é ele que fixa cor e símbolo: assim o
 * mesmo app sai idêntico nas três fotos. A lista de `Demo.apps` de cada idioma
 * está em ordem alfabética daquele idioma, e por isso a posição NÃO serve de
 * chave: `Agenda` é o primeiro em pt e `Calendar` é o terceiro em en.
 *
 * Os `symbol` são path data de 24x24 dos Material Icons (google/material-design-icons,
 * Apache 2.0) — ver [Symbols].
 */
internal enum class DemoRole(
    val labels: List<String>,
    val color: Int,
    val symbol: String,
) {
    CALENDAR(
        labels = listOf("Agenda", "Calendar"),
        color = 0xFF4C6FBF.toInt(),
        symbol = Symbols.CALENDAR_MONTH,
    ),
    FILES(
        labels = listOf("Arquivos", "Files", "Archivos"),
        color = 0xFF2F9E6E.toInt(),
        symbol = Symbols.FOLDER,
    ),
    NOTES(
        labels = listOf("Bloco de Notas", "Notepad", "Bloc de Notas"),
        color = 0xFFCC7A2B.toInt(),
        symbol = Symbols.STICKY_NOTE_2,
    ),
    CALCULATOR(
        labels = listOf("Calculadora", "Calculator"),
        color = 0xFF9A5BC4.toInt(),
        symbol = Symbols.CALCULATE,
    ),
    CAMERA(
        labels = listOf("Câmera", "Camera", "Cámara"),
        color = 0xFFC9525A.toInt(),
        symbol = Symbols.PHOTO_CAMERA,
    ),
    PHOTOS(
        labels = listOf("Fotos", "Photos"),
        color = 0xFF2E8FA8.toInt(),
        symbol = Symbols.IMAGE,
    ),
    MAPS(
        labels = listOf("Mapas", "Maps"),
        color = 0xFF7A8B3C.toInt(),
        symbol = Symbols.MAP,
    ),
    MESSAGES(
        labels = listOf("Mensagens", "Messages", "Mensajes"),
        color = 0xFFB4526E.toInt(),
        symbol = Symbols.CHAT,
    ),
    MARKET(
        labels = listOf("Mercado", "Market"),
        color = 0xFF5B6BA8.toInt(),
        symbol = Symbols.SHOPPING_CART,
    ),
    METRO(
        labels = listOf("Metrô", "Metro"),
        color = 0xFF3F9E8C.toInt(),
        symbol = Symbols.DIRECTIONS_SUBWAY,
    ),
    MUSIC(
        labels = listOf("Música", "Music"),
        color = 0xFFA9762E.toInt(),
        symbol = Symbols.MUSIC_NOTE,
    ),
    BROWSER(
        labels = listOf("Navegador", "Browser"),
        color = 0xFF6E6EAA.toInt(),
        symbol = Symbols.LANGUAGE,
    ),
    PODCASTS(
        labels = listOf("Podcasts", "Pódcasts"),
        color = 0xFF4C6FBF.toInt(),
        symbol = Symbols.PODCASTS,
    ),
    RADIO(
        labels = listOf("Rádio", "Radio"),
        color = 0xFF2F9E6E.toInt(),
        symbol = Symbols.RADIO,
    ),
    CLOCK(
        labels = listOf("Relógio", "Clock", "Reloj"),
        color = 0xFFCC7A2B.toInt(),
        symbol = Symbols.SCHEDULE,
    ),
    TASKS(
        labels = listOf("Tarefas", "Tasks", "Tareas"),
        color = 0xFF9A5BC4.toInt(),
        symbol = Symbols.CHECKLIST,
    ),
    VIDEOS(
        labels = listOf("Vídeos", "Videos"),
        color = 0xFFC9525A.toInt(),
        symbol = Symbols.MOVIE,
    ),
    ;

    internal companion object {
        private val byLabel: Map<String, DemoRole> =
            entries.flatMap { role -> role.labels.map { it to role } }.toMap()

        /** O papel de um rótulo, ou `null` quando é um app que a tabela não conhece. */
        fun of(label: String): DemoRole? = byLabel[label]
    }
}

/**
 * Path data dos Material Icons (estilo "filled", grade de 24x24), do repositório
 * google/material-design-icons — Apache License 2.0. Cada constante é o `d` do
 * arquivo `src/<categoria>/<nome>/materialicons/24px.svg`, com os retângulos de
 * fundo `fill="none"` descartados e os `<circle>` reescritos como arcos; o nome
 * do ícone e a categoria estão no comentário de cada uma. Creditado em
 * `docs/terceiros.md`.
 *
 * Os subcaminhos contam com preenchimento "nonzero" (o padrão do SVG e o do
 * [Path] do Android): é ele que abre os vazados — as linhas do balão de conversa,
 * o miolo do relógio — sem precisar de um segundo desenho.
 */
private object Symbols {

    /** `calendar_month` (action). */
    const val CALENDAR_MONTH: String =
        "M19,4h-1V2h-2v2H8V2H6v2H5C3.89,4,3.01,4.9,3.01,6L3,20c0,1.1,0.89,2,2,2h14c1.1,0,2-0.9,2-2V6C21,4.9,20.1,4,19,4z M19,20 H5V10h14V20z M9,14H7v-2h2V14z M13,14h-2v-2h2V14z M17,14h-2v-2h2V14z M9,18H7v-2h2V18z M13,18h-2v-2h2V18z M17,18h-2v-2h2V18z"

    /** `folder` (file). */
    const val FOLDER: String =
        "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"

    /** `sticky_note_2` (action). */
    const val STICKY_NOTE_2: String =
        "M19,3H4.99C3.89,3,3,3.9,3,5l0.01,14c0,1.1,0.89,2,1.99,2h10l6-6V5C21,3.9,20.1,3,19,3z M7,8h10v2H7V8z M12,14H7v-2h5V14z M14,19.5V14h5.5L14,19.5z"

    /** `calculate` (content). */
    const val CALCULATE: String =
        "M19,3H5C3.9,3,3,3.9,3,5v14c0,1.1,0.9,2,2,2h14c1.1,0,2-0.9,2-2V5C21,3.9,20.1,3,19,3z M13.03,7.06L14.09,6l1.41,1.41 L16.91,6l1.06,1.06l-1.41,1.41l1.41,1.41l-1.06,1.06L15.5,9.54l-1.41,1.41l-1.06-1.06l1.41-1.41L13.03,7.06z M6.25,7.72h5v1.5h-5 V7.72z M11.5,16h-2v2H8v-2H6v-1.5h2v-2h1.5v2h2V16z M18,17.25h-5v-1.5h5V17.25z M18,14.75h-5v-1.5h5V14.75z"

    /** `photo_camera` (image). */
    const val PHOTO_CAMERA: String =
        "M12,8.8 A3.2,3.2 0 1 1 12,15.2 A3.2,3.2 0 1 1 12,8.8 Z M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"

    /** `image` (image). */
    const val IMAGE: String =
        "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"

    /** `map` (maps). */
    const val MAP: String =
        "M20.5 3l-.16.03L15 5.1 9 3 3.36 4.9c-.21.07-.36.25-.36.48V20.5c0 .28.22.5.5.5l.16-.03L9 18.9l6 2.1 5.64-1.9c.21-.07.36-.25.36-.48V3.5c0-.28-.22-.5-.5-.5zM15 19l-6-2.11V5l6 2.11V19z"

    /** `chat` (communication). */
    const val CHAT: String =
        "M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 9h12v2H6V9zm8 5H6v-2h8v2zm4-6H6V6h12v2z"

    /** `shopping_cart` (action). */
    const val SHOPPING_CART: String =
        "M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z"

    /** `directions_subway` (maps). */
    const val DIRECTIONS_SUBWAY: String =
        "M12 2c-4.42 0-8 .5-8 4v9.5C4 17.43 5.57 19 7.5 19L6 20.5v.5h12v-.5L16.5 19c1.93 0 3.5-1.57 3.5-3.5V6c0-3.5-3.58-4-8-4zM7.5 17c-.83 0-1.5-.67-1.5-1.5S6.67 14 7.5 14s1.5.67 1.5 1.5S8.33 17 7.5 17zm3.5-6H6V6h5v5zm5.5 6c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm1.5-6h-5V6h5v5z"

    /** `music_note` (image). */
    const val MUSIC_NOTE: String =
        "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"

    /** `language` (action). */
    const val LANGUAGE: String =
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2 0 .68.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2 0-.68.07-1.35.16-2h4.68c.09.65.16 1.32.16 2 0 .68-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2 0-.68-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z"

    /** `podcasts` (search). */
    const val PODCASTS: String =
        "M14,12c0,0.74-0.4,1.38-1,1.72V22h-2v-8.28c-0.6-0.35-1-0.98-1-1.72c0-1.1,0.9-2,2-2S14,10.9,14,12z M12,6 c-3.31,0-6,2.69-6,6c0,1.74,0.75,3.31,1.94,4.4l1.42-1.42C8.53,14.25,8,13.19,8,12c0-2.21,1.79-4,4-4s4,1.79,4,4 c0,1.19-0.53,2.25-1.36,2.98l1.42,1.42C17.25,15.31,18,13.74,18,12C18,8.69,15.31,6,12,6z M12,2C6.48,2,2,6.48,2,12 c0,2.85,1.2,5.41,3.11,7.24l1.42-1.42C4.98,16.36,4,14.29,4,12c0-4.41,3.59-8,8-8s8,3.59,8,8c0,2.29-0.98,4.36-2.53,5.82l1.42,1.42 C20.8,17.41,22,14.85,22,12C22,6.48,17.52,2,12,2z"

    /** `radio` (av). */
    const val RADIO: String =
        "M3.24 6.15C2.51 6.43 2 7.17 2 8v12c0 1.1.89 2 2 2h16c1.11 0 2-.9 2-2V8c0-1.11-.89-2-2-2H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z"

    /** `schedule` (action). */
    const val SCHEDULE: String =
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z M12.5 7H11v6l5.25 3.15.75-1.23-4.5-2.67z"

    /** `checklist` (editor). */
    const val CHECKLIST: String =
        "M22,7h-9v2h9V7z M22,15h-9v2h9V15z M5.54,11L2,7.46l1.41-1.41l2.12,2.12l4.24-4.24l1.41,1.41L5.54,11z M5.54,19L2,15.46 l1.41-1.41l2.12,2.12l4.24-4.24l1.41,1.41L5.54,19z"

    /** `movie` (av). */
    const val MOVIE: String =
        "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"
}

/**
 * O ícone em si: quadrado arredondado com o degradê da cor do papel e o símbolo
 * branco por cima. É um [Drawable] à mão, e não um `VectorDrawable`, porque aqui
 * não há recursos compilados nem `Resources` — a prévia roda no layoutlib, na JVM.
 */
internal class DemoAppIcon(
    private val argb: Int,
    symbol: String?,
    private val initial: String,
) : Drawable() {

    /** O caminho na grade de 24x24, uma vez só; o de cada tamanho sai dele por matriz. */
    private val source: Path? = symbol?.let {
        runCatching { PathParser.createPathFromPathData(it) }.getOrNull()
    }

    private val plate = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val symbolPath = Path()
    private val matrix = Matrix()
    private val plateRect = RectF()
    private var corner = 0f

    override fun onBoundsChange(bounds: Rect) {
        val side = min(bounds.width(), bounds.height()).toFloat()
        plateRect.set(bounds)
        corner = side * CORNER_FRACTION
        // Claro no canto de cima, cheio no de baixo: é o degradê que dá volume à placa.
        plate.shader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            lighten(argb),
            argb,
            Shader.TileMode.CLAMP,
        )
        ink.textSize = side * 0.44f
        val path = source ?: return
        val scale = side * SYMBOL_FRACTION / SYMBOL_VIEWPORT
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            bounds.exactCenterX() - SYMBOL_VIEWPORT * scale / 2f,
            bounds.exactCenterY() - SYMBOL_VIEWPORT * scale / 2f,
        )
        path.transform(matrix, symbolPath)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(plateRect, corner, corner, plate)
        if (source != null) {
            canvas.drawPath(symbolPath, ink)
        } else {
            // Sem símbolo na tabela, a inicial do rótulo: nunca fica uma placa vazia.
            val baseline = bounds.exactCenterY() - (ink.descent() + ink.ascent()) / 2f
            canvas.drawText(initial, bounds.exactCenterX(), baseline, ink)
        }
    }

    override fun getIntrinsicWidth(): Int = INTRINSIC_PX

    override fun getIntrinsicHeight(): Int = INTRINSIC_PX

    override fun setAlpha(alpha: Int) {
        plate.alpha = alpha
        ink.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        plate.colorFilter = colorFilter
        ink.colorFilter = colorFilter
    }

    @Deprecated("Drawable.getOpacity está obsoleto, mas continua abstrato.")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /** Clareia a cor para o alto da placa — o degradê é sempre a mesma cor, mais leve. */
    private fun lighten(argb: Int): Int {
        fun channel(shift: Int): Int {
            val value = (argb shr shift) and 0xFF
            return (value + (255 - value) * 35 / 100) and 0xFF
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/**
 * A fonte de ícones das prévias. Não há `LauncherApps` aqui — é exatamente por
 * isso que os composables pedem [IconSource] e não o repositório inteiro.
 *
 * O ícone vai como `fromPack = true` porque já chega no formato final: a lista
 * não deve recortá-lo num círculo nem encolhê-lo como faz com um ícone legado.
 */
internal object DemoIcons : IconSource {

    override suspend fun icon(entry: AppEntry): LoadedIcon {
        val role = DemoRole.of(entry.label)
        return LoadedIcon(
            DemoAppIcon(
                argb = role?.color ?: fallbackColor(entry.label),
                symbol = role?.symbol,
                initial = entry.label.take(1).uppercase(),
            ),
            fromPack = true,
        )
    }

    /** Nenhuma prévia mostra atalhos: eles exigiriam um `ShortcutInfo` de verdade. */
    override suspend fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = null

    /** Cor de um rótulo fora da tabela — estável, para a mesma foto sair sempre igual. */
    private fun fallbackColor(label: String): Int =
        DemoRole.entries[(label.hashCode() and Int.MAX_VALUE) % DemoRole.entries.size].color
}
