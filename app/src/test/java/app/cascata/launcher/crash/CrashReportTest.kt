package app.cascata.launcher.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O cabeçalho do relatório é a única parte com regra de formatação — e é a que
 * responde "qual versão, qual aparelho, quando" antes de qualquer rolagem.
 * Nada aqui toca em Android: [crashReport] recebe tudo pronto.
 */
class CrashReportTest {

    private fun report(
        timestamp: String = "2026-09-15T10:30:00-0300",
        versionName: String = "1.1.0",
        versionCode: Int = 111,
        flavor: String = "lite",
        manufacturer: String = "Xiaomi",
        model: String = "Redmi Note 12",
        sdkInt: Int = 34,
        threadName: String = "main",
        stackTrace: String = "java.lang.IllegalStateException: teste\n\tat app.Foo.bar(Foo.kt:10)",
    ) = crashReport(
        timestamp = timestamp,
        versionName = versionName,
        versionCode = versionCode,
        flavor = flavor,
        manufacturer = manufacturer,
        model = model,
        sdkInt = sdkInt,
        threadName = threadName,
        stackTrace = stackTrace,
    )

    @Test
    fun `cabecalho traz versao, edicao, aparelho, api e thread`() {
        val lines = report().lines()
        assertEquals("Cascata 1.1.0 (111, lite)", lines[0])
        assertEquals("Quando: 2026-09-15T10:30:00-0300", lines[1])
        assertEquals("Aparelho: Xiaomi Redmi Note 12", lines[2])
        assertEquals("Android: API 34", lines[3])
        assertEquals("Thread: main", lines[4])
    }

    @Test
    fun `uma linha em branco separa o cabecalho do rastro`() {
        val lines = report().lines()
        assertEquals("", lines[5])
        assertEquals("java.lang.IllegalStateException: teste", lines[6])
        assertEquals("\tat app.Foo.bar(Foo.kt:10)", lines[7])
    }

    @Test
    fun `o rastro vai inteiro, com as causas`() {
        val trace = buildString {
            appendLine("java.lang.RuntimeException: de fora")
            appendLine("\tat app.Foo.bar(Foo.kt:10)")
            appendLine("Caused by: java.lang.NullPointerException: de dentro")
            append("\tat app.Foo.baz(Foo.kt:42)")
        }
        val text = report(stackTrace = trace)
        assertTrue(text.contains("Caused by: java.lang.NullPointerException: de dentro"))
        assertTrue(text.endsWith("\tat app.Foo.baz(Foo.kt:42)"))
    }

    @Test
    fun `o relatorio nao termina em linhas em branco`() {
        val text = report(stackTrace = "java.lang.Error: x\n\n\n")
        assertEquals(text, text.trimEnd())
        assertTrue(text.endsWith("java.lang.Error: x"))
    }

    @Test
    fun `campos vazios nao quebram o formato`() {
        // `Build.MODEL` e a data podem vir vazios; o cabeçalho continua com as
        // mesmas cinco linhas e o rastro continua começando na sétima.
        val lines = report(timestamp = "", model = "", stackTrace = "").lines()
        assertEquals(7, lines.size)
        assertEquals("Quando: ", lines[1])
        assertEquals("Aparelho: Xiaomi ", lines[2])
        // A linha em branco que separa cabeçalho e rastro, e o rastro vazio.
        assertEquals("", lines[5])
        assertEquals("", lines[6])
    }

    @Test
    fun `a edicao aparece no cabecalho`() {
        assertTrue(report(flavor = "full").lines()[0].endsWith("(111, full)"))
    }
}
