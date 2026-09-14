package app.cascata.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gera o Baseline Profile do Cascata.
 *
 * Não é o perfil "padrão" do AndroidX (aquele que só cobre o startup genérico
 * do Compose): aqui o percurso é o do app — abrir a HomeActivity, esperar a
 * lista compor e rolar a gaveta inteira — porque é esse código que a gente
 * quer pré-compilado em AOT na primeira execução.
 *
 * Rode com `./gradlew :app:generateLiteReleaseBaselineProfile` (veja
 * docs/performance.md). O arquivo resultante é COMMITADO no repositório.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        // Marca o trecho até o primeiro frame como "startup profile", que o R8
        // usa para agrupar essas classes no começo do dex (dexLayoutOptimization).
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait(launcherIntent())
        device.waitForIdle()
        scrollAppList()
    }
}
