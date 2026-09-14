package app.cascata.launcher.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction

/**
 * O applicationId da RELEASE, sem sufixo.
 *
 * O plugin gera e mede a partir das variantes `nonMinifiedRelease`, que herdam
 * o `applicationId` de release. Usar `app.cascata.launcher.debug` aqui faria os
 * testes procurarem um pacote que nunca é instalado durante a geração.
 */
const val TARGET_PACKAGE = "app.cascata.launcher"

/**
 * Cascata é um launcher: a activity principal está na categoria HOME.
 *
 * `startActivityAndWait()` sem argumento resolve o launcher *padrão* do
 * sistema via `ACTION_MAIN` + `CATEGORY_HOME` — num emulador limpo isso abre o
 * launcher do AOSP, não o Cascata, e o perfil sairia vazio (ou o teste
 * estouraria em timeout esperando um frame que nunca vem do nosso processo).
 *
 * Por isso o intent é explícito no pacote e usa `CATEGORY_LAUNCHER`, que a
 * HomeActivity também declara: assim o alvo é sempre o nosso APK, esteja ele
 * definido como launcher padrão ou não.
 */
fun launcherIntent(packageName: String = TARGET_PACKAGE): Intent =
    Intent(Intent.ACTION_MAIN).apply {
        setPackage(packageName)
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

/**
 * Percorre a lista de apps para baixo e de volta para cima.
 *
 * O mesmo gesto é usado pelo gerador de perfil e pelo [ScrollBenchmark] de
 * propósito: o perfil só otimiza o caminho de código que alguém realmente
 * exercitou, então medir um percurso diferente do que foi gravado mediria
 * outra coisa.
 *
 * `findObject` é tolerante a nulo porque a lista pode não estar composta ainda
 * em aparelhos lentos; nesse caso o passo vira um no-op em vez de derrubar a
 * geração inteira.
 */
fun MacrobenchmarkScope.scrollAppList(times: Int = 3) {
    device.waitForIdle()
    val list = device.findObject(By.scrollable(true))
    if (list == null) {
        device.waitForIdle()
        return
    }
    // Sem gesto de fling: `scroll` com 1f percorre uma "tela" por vez e deixa o
    // FrameTimingMetric com uma amostra estável, não com uma inércia aleatória.
    list.setGestureMargin(device.displayWidth / 5)
    repeat(times) {
        list.scroll(Direction.DOWN, 1f)
        device.waitForIdle()
    }
    repeat(times) {
        list.scroll(Direction.UP, 1f)
        device.waitForIdle()
    }
}
