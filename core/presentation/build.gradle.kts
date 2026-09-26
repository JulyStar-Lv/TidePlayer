import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    alias(libs.plugins.convention.cmp.library)
    alias(libs.plugins.convention.kmp.library)
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
            baseName = "CorePresentation"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.core.presentation")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.components.resources)
            implementation(libs.animation)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.backdrop)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation("net.java.dev.jna:jna:5.19.1")
                implementation("net.java.dev.jna:jna-platform:5.19.1")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "io.github.julystar.musicapp.core.presentation"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.withType<ProcessResources>().configureEach {
    if (name == "desktopProcessResources") {
        exclude("**/noto_sans_sc_wght.ttf")
        exclude("**/noto_sans_sc_ofl.txt")
    }
}
