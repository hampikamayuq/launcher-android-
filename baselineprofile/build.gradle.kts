import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "app.cascata.launcher.baselineprofile"
    compileSdk = 36

    /**
     * Macrobenchmark precisa de API 28+ (é daí que existe a instrumentação de
     * perfil e o `dumpsys gfxinfo` que alimenta o FrameTimingMetric). O :app
     * continua em minSdk 26 — este módulo nunca é instalado junto com ele.
     */
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    /** O app sob medição. */
    targetProjectPath = ":app"

    /**
     * As MESMAS flavors do :app, na mesma dimensão. Sem isto o plugin não
     * consegue casar `liteRelease` daqui com `liteRelease` de lá e a
     * configuração falha com "unable to find a matching variant".
     * Não precisam de `buildConfigField` nenhum: só o nome importa.
     */
    flavorDimensions += "network"

    productFlavors {
        create("lite") { dimension = "network" }
        create("full") { dimension = "network" }
    }

    /**
     * O APK de teste se auto-instrumenta em vez de instrumentar o :app. É o que
     * permite matar e reiniciar o processo do app a cada iteração — obrigatório
     * para medir cold start de verdade.
     */
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            allDevices {
                /**
                 * Emulador gerenciado pelo Gradle: baixa e sobe sozinho, sem
                 * AVD manual. `aosp-atd` (Automated Test Device) é a imagem
                 * enxuta — sem Play Services, sem apps de sistema pesados —
                 * e por isso a mais rápida e determinística no CI.
                 * Conferido em `sdkmanager --list`: há `aosp_atd` x86_64 para
                 * as APIs 30 a 36 — 34 é o ponto de equilíbrio entre imagem
                 * estável e proximidade do targetSdk 36.
                 */
                create<ManagedVirtualDevice>("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

baselineProfile {
    // Gera no emulador gerenciado, nunca num aparelho plugado por acidente.
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    // Num módulo `com.android.test` o source set principal JÁ é o de teste:
    // as dependências entram como `implementation`, não `androidTestImplementation`.
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
