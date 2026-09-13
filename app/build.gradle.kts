plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.cascata.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.cascata.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
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
            buildConfigField("boolean", "HAS_NETWORK", "false")
        }
        create("full") {
            dimension = "network"
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

    buildFeatures {
        compose = true
        // HAS_NETWORK e VERSION_NAME (User-Agent do clima) vêm daqui.
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
}
