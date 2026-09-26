plugins {
    alias(libs.plugins.convention.music.source)
}

kotlin {
    androidTarget()
    jvm("desktop")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SourceOneDrive"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.source.onedrive")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":source:api"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "io.github.julystar.musicapp.source.onedrive"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
