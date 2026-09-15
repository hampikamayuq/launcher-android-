plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.android.screenshot)
}

android {
    namespace = "app.cascata.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.cascata.launcher"
        minSdk = 26
        targetSdk = 36
        versionName = "1.1.0"
        // versionCode base; cada edição recebe o seu abaixo (base*10 + edição),
        // porque F-Droid e Play identificam builds por (pacote, versionCode) e as
        // duas edições compartilham o pacote.
        versionCode = 11
    }

    /**
     * Duas edições do mesmo app, não dois apps: `lite` compila sem INTERNET nem
     * localização (o card de clima some), `full` traz as duas permissões. Mesmo
     * applicationId de propósito — instala-se uma OU outra, e trocar de edição é
     * uma atualização, não um segundo ícone na gaveta.
     */
    flavorDimensions += "network"

    productFlavors {
        create("lite") {
            dimension = "network"
            isDefault = true
            versionCode = defaultConfig.versionCode!! * 10 + 1
            buildConfigField("boolean", "HAS_NETWORK", "false")
        }
        create("full") {
            dimension = "network"
            versionCode = defaultConfig.versionCode!! * 10 + 2
            buildConfigField("boolean", "HAS_NETWORK", "true")
        }
    }

    val keystorePath = System.getenv("CASCATA_KEYSTORE_PATH")
    val hasReleaseSigning = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("CASCATA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CASCATA_KEY_ALIAS")
                keyPassword = System.getenv("CASCATA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
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

    /**
     * Prévias do Compose renderizadas por layoutlib na JVM, sem emulador — é o
     * que produz os screenshots da loja. Ainda é API experimental do AGP.
     */
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    buildFeatures {
        compose = true
        // HAS_NETWORK e VERSION_NAME (User-Agent do clima) vêm daqui.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

/**
 * Baseline Profile: lista de métodos/classes que o ART compila em AOT logo na
 * instalação, em vez de esperar o JIT aquecer. Quem produz essa lista é o
 * módulo :baselineprofile (Macrobenchmark), não o gerador padrão do AndroidX.
 */
baselineProfile {
    /**
     * Gerar exige emulador — não pode acontecer dentro de um `assembleRelease`
     * comum, senão o CI de release passaria a depender de KVM. A geração é um
     * passo explícito e agendado (.github/workflows/baseline.yml).
     */
    automaticGenerationDuringBuild = false

    /**
     * O perfil gerado é escrito em `app/src/<variante>/generated/baselineProfiles/`
     * e COMMITADO no repositório. É o que faz uma build de release normal —
     * inclusive a de quem só clonou o projeto — já sair com o perfil embutido.
     */
    saveInSrc = true

    /**
     * Deixa o R8 reordenar o dex usando o startup profile: as classes do
     * caminho de abertura ficam juntas, o que reduz page faults no cold start.
     * Depende de `isMinifyEnabled = true`, que a release já tem.
     */
    dexLayoutOptimization = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.evalex)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Aplica o Baseline Profile empacotado já na primeira execução, sem
    // depender do Play Store entregar um perfil na nuvem.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)

    // As prévias de screenshot vivem em `app/src/screenshotTest/`: `@PreviewTest`
    // (o que marca a prévia como caso de teste) vem da validation-api, e o
    // ui-tooling é o runtime que layoutlib usa para compor fora do aparelho.
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)

    // Consome o perfil produzido pelo módulo de Macrobenchmark.
    baselineProfile(project(":baselineprofile"))
}
