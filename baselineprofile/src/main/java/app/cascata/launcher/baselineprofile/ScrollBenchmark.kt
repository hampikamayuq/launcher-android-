package app.cascata.launcher.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mede a fluidez da rolagem da gaveta de apps.
 *
 * O `FrameTimingMetric` reporta `frameDurationCpuMs` em percentis (P50/P90/P95/
 * P99). O que importa aqui é a cauda: o orçamento do plano é 300 apps rolando
 * sem frame perdido, ou seja P99 dentro do orçamento de frame do aparelho
 * (~16,6 ms a 60 Hz, ~8,3 ms a 120 Hz).
 *
 * O app é aberto no `setupBlock` para que o trecho medido contenha SÓ a
 * rolagem — startup tem benchmark próprio.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollGaveta() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(launcherIntent())
        },
    ) {
        scrollAppList()
    }
}
