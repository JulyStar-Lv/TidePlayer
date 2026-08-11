plugins {
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
            baseName = "CoreData"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.core.data")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "io.github.julystar.musicapp.core.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
