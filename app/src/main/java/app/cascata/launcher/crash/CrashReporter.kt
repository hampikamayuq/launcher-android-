package app.cascata.launcher.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import app.cascata.launcher.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Quando um launcher quebra, o aparelho fica sem tela inicial: o processo morre,
 * o sistema reabre a home, ela quebra de novo, e quem está do outro lado só vê o
 * app "fechar sozinho" sem nada para olhar. Sem computador e sem `adb` não há
 * rastro nenhum.
 *
 * Este handler existe para isso: grava o rastro num arquivo do próprio app e
 * abre a [CrashActivity] mostrando o texto. Nada aqui pode lançar — um relatório
 * de falha que falha é pior que não ter relatório —, então tudo o que toca disco,
 * sistema ou formatação vai dentro de `runCatching`, e o caminho de erro sempre
 * termina delegando ao handler que já existia.
 */
object CrashReporter {

    /** O texto do relatório viaja por aqui até a [CrashActivity]. */
    const val EXTRA_REPORT = "app.cascata.launcher.crash.RELATORIO"

    /** Só a última falha é guardada: a de antes não ajuda e o arquivo não cresce. */
    private const val FILE_NAME = "ultima-falha.txt"

    /** Nome do processo da [CrashActivity], o mesmo do `android:process` no manifesto. */
    private const val CRASH_PROCESS_SUFFIX = ":crash"

    /** Código de saída próprio, para separar esta morte de um `exit(0)` qualquer. */
    private const val EXIT_CODE = 10

    /**
     * Primeira trava contra laço: o handler é instalado uma vez por processo.
     * Segunda: nunca no processo `:crash` — se a tela de relatório quebrar, quem
     * a atende é o handler padrão do Android, e não este, que a reabriria.
     */
    private val installed = AtomicBoolean(false)

    /**
     * Terceira trava: uma falha *dentro* do próprio handler (ou uma segunda
     * thread caindo enquanto a primeira ainda é tratada) não recomeça o
     * tratamento — vai direto para o handler anterior.
     */
    private val handling = AtomicBoolean(false)

    /** Um aviso não fatal por processo: o arquivo guarda um relatório, não um histórico. */
    private val noted = AtomicBoolean(false)

    /**
     * Chamado uma vez, do `onCreate` da Application. Guarda o handler anterior:
     * tudo o que este aqui não consegue fazer volta para ele.
     */
    fun install(app: Application) {
        if (isCrashProcess(app)) return
        if (!installed.compareAndSet(false, true)) return

        val previous = runCatching { Thread.getDefaultUncaughtExceptionHandler() }.getOrNull()

        runCatching {
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                handle(app, previous, thread, error)
            }
        }
    }

    /** O último relatório gravado, ou null quando não há nenhum (o caso normal). */
    fun lastReport(context: Context): String? = runCatching {
        val file = reportFile(context)
        if (file.isFile) file.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    /** Apaga o relatório. O botão das configurações é o único que chama. */
    fun clear(context: Context) {
        runCatching { reportFile(context).delete() }
    }

    /**
     * Uma falha que **não** derrubou o processo — um escopo de corrotina da
     * aplicação que deixou escapar uma exceção, por exemplo. Ela não aparece em
     * lugar nenhum da tela, e sem isto o único rastro seria o logcat, que quem
     * usa o app no dia a dia não tem como ler.
     *
     * Duas travas para não atrapalhar o relatório que importa: só o primeiro
     * aviso do processo vira arquivo, e só quando ainda não há relatório
     * gravado. Uma falha de verdade, que passa por [handle], sobrescreve o
     * arquivo de qualquer jeito — o rastro fatal sempre ganha do aviso.
     */
    fun note(context: Context, source: String, error: Throwable) {
        if (!noted.compareAndSet(false, true)) return
        runCatching {
            val file = reportFile(context)
            if (file.isFile && file.length() > 0L) return
            file.writeText(note(source, error))
        }
    }

    private fun handle(
        app: Application,
        previous: Thread.UncaughtExceptionHandler?,
        thread: Thread,
        error: Throwable,
    ) {
        if (!handling.compareAndSet(false, true)) {
            fallback(previous, thread, error)
            return
        }

        // Montar o texto não pode derrubar o tratamento: na pior das hipóteses
        // sobra o rastro cru, que já é a parte que importa.
        val report = runCatching { report(thread, error) }
            .getOrElse { runCatching { error.stackTraceToString() }.getOrDefault("") }

        runCatching { reportFile(app).writeText(report) }

        val shown = runCatching { showReport(app, report) }.getOrDefault(false)
        if (!shown) {
            fallback(previous, thread, error)
            return
        }

        // A Activity vive noutro processo e já foi lançada: este aqui morre sem
        // passar pelo diálogo "o app parou", que não teria nada a dizer.
        runCatching { Process.killProcess(Process.myPid()) }
        exitProcess(EXIT_CODE)
    }

    /** Devolve a falha a quem cuidava dela antes — o diálogo do sistema, no fim da fila. */
    private fun fallback(
        previous: Thread.UncaughtExceptionHandler?,
        thread: Thread,
        error: Throwable,
    ) {
        if (previous != null) {
            previous.uncaughtException(thread, error)
        } else {
            runCatching { Process.killProcess(Process.myPid()) }
            exitProcess(EXIT_CODE)
        }
    }

    /**
     * True quando a Activity de relatório subiu; false quando não há como
     * mostrá-la. A falha que interessa aqui acontece com o launcher na frente,
     * e aí o lançamento é permitido; uma falha em segundo plano cairia na
     * restrição de abrir Activity do Android 10+ e a tela não apareceria — o
     * arquivo, que é o que importa nesse caso, já foi gravado antes.
     */
    private fun showReport(app: Application, report: String): Boolean = runCatching {
        val intent = Intent(app, CrashActivity::class.java)
            .putExtra(EXTRA_REPORT, report)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun reportFile(context: Context): File = File(context.filesDir, FILE_NAME)

    /** O mesmo cabeçalho do relatório fatal, com a origem no lugar da thread. */
    private fun note(source: String, error: Throwable): String = crashReport(
        timestamp = isoNow(),
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        flavor = BuildConfig.FLAVOR,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        sdkInt = Build.VERSION.SDK_INT,
        threadName = source,
        stackTrace = runCatching { error.stackTraceToString() }.getOrDefault(""),
        fatal = false,
    )

    private fun report(thread: Thread, error: Throwable): String = crashReport(
        timestamp = isoNow(),
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        flavor = BuildConfig.FLAVOR,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        sdkInt = Build.VERSION.SDK_INT,
        threadName = thread.name,
        // `stackTraceToString` já desce por `Caused by:` e por `Suppressed:`.
        stackTrace = error.stackTraceToString(),
    )

    /**
     * Data e hora locais em ISO 8601, com o fuso — quem lê o relatório precisa
     * saber "quando" no relógio de quem viu a falha, não em UTC.
     */
    private fun isoNow(): String = runCatching {
        SimpleDateFormat(ISO_PATTERN, Locale.US).format(Date())
    }.getOrDefault("")

    private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ssZ"

    /**
     * O processo é reconhecido pelo nome. `Application.getProcessName()` só
     * existe do Android 9 em diante; abaixo dele, `/proc/self/cmdline` é o que o
     * próprio framework lê. Se nada der certo, assumimos o processo principal —
     * errar para o lado de instalar o handler perde o laço, mas nunca perde o
     * relatório; e o laço ainda é impedido pela trava [handling].
     */
    private fun isCrashProcess(app: Application): Boolean {
        val name = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                // O cmdline é uma sequência de strings terminadas em NUL; a
                // primeira é o nome do processo.
                File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
            }
        }.getOrNull().orEmpty()
        return name.endsWith(CRASH_PROCESS_SUFFIX)
    }
}

/**
 * O texto do relatório, sem nada de Android: é a única parte com regra de
 * formatação, e assim ela é testável na JVM.
 *
 * O cabeçalho vem antes do rastro de propósito — numa tela de celular é o que
 * aparece sem rolar, e é o que responde "qual versão, qual aparelho, quando".
 */
internal fun crashReport(
    timestamp: String,
    versionName: String,
    versionCode: Int,
    flavor: String,
    manufacturer: String,
    model: String,
    sdkInt: Int,
    threadName: String,
    stackTrace: String,
    /** `false` num aviso não fatal: o app continuou aberto, e quem lê precisa saber. */
    fatal: Boolean = true,
): String = buildString {
    if (!fatal) appendLine("Aviso: o app seguiu aberto com um recurso degradado.")
    appendLine("Cascata $versionName ($versionCode, $flavor)")
    appendLine("Quando: $timestamp")
    appendLine("Aparelho: $manufacturer $model")
    appendLine("Android: API $sdkInt")
    appendLine("Thread: $threadName")
    appendLine()
    append(stackTrace.trimEnd())
}
