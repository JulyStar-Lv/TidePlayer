plugins {
    alias(libs.plugins.convention.cmp.library)
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    androidTarget()
    jvm("desktop")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "LyricsUi"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.core.lyrics.ui")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:lyrics-core"))
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.animation)
            implementation(libs.compose.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.compose.ui.test)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "io.github.julystar.musicapp.core.lyrics.ui"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
