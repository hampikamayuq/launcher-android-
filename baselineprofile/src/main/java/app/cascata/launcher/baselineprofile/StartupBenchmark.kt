package app.cascata.launcher.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mede o cold start com e sem Baseline Profile.
 *
 * Os dois testes existem para serem comparados: `semPerfil` é a linha de base
 * (`CompilationMode.None()`, tudo interpretado/JIT, que é o que um usuário vê
 * logo depois de instalar) e `comPerfil` é o mesmo caminho com o perfil já
 * aplicado. A diferença entre os dois `timeToInitialDisplayMs` é o ganho real
 * do perfil — se for perto de zero, o percurso gravado no gerador não é o que
 * o app executa no startup.
 *
 * Orçamento do plano: cold start abaixo de 500 ms num aparelho mediano.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupSemPerfil() = measureStartup(CompilationMode.None())

    @Test
    fun startupComPerfil() = measureStartup(CompilationMode.Partial())

    private fun measureStartup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait(launcherIntent())
    }
}
