plugins {
    alias(libs.plugins.convention.kmp.domain)
}

kotlin {
    androidTarget()
    jvm("desktop")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SourceApi"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.source.api")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.github.julystar.musicapp.source.api"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
