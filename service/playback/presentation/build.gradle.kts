plugins {
    alias(libs.plugins.convention.feature)
}

compose.resources {
    publicResClass = true
}

kotlin {
    androidTarget()
    jvm("desktop")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "PlaybackPresentation"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.service.playback.presentation")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:presentation"))
            implementation(project(":core:lyrics-core"))
            implementation(project(":core:lyrics-ui"))
            implementation(project(":service:playback:domain"))
            implementation(project(":service:download:domain"))
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.components.resources)
            implementation(libs.animation)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.collections.immutable)
        }
    }
}

android {
    namespace = "io.github.julystar.musicapp.service.playback.presentation"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
