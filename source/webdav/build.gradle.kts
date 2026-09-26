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
            baseName = "SourceWebDav"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.source.webdav")
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
    namespace = "io.github.julystar.musicapp.source.webdav"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
