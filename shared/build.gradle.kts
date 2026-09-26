plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.cmp.library)
    alias(libs.plugins.convention.feature)
    alias(libs.plugins.kotlin.atomicfu)
    id("com.android.library")
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
            baseName = "SharedKit"
            isStatic = true
            binaryOption("bundleId", "io.github.julystar.musicapp.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:runtime"))
            implementation(project(":core:domain"))
            implementation(project(":core:lyrics-core"))
            implementation(project(":core:presentation"))
            implementation(project(":source:api"))
            implementation(project(":source:local"))
            implementation(project(":source:webdav"))
            implementation(project(":source:onedrive"))
            implementation(project(":source:smb"))
            implementation(project(":source:openlist"))
            implementation(project(":source:server"))
            implementation(project(":service:playback:domain"))
            implementation(project(":service:playback:presentation"))
            implementation(project(":service:download:data"))
            implementation(project(":service:download:domain"))
            implementation(project(":feature:downloads"))
            implementation(project(":feature:search"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:playlist"))
            implementation(project(":feature:sources"))
            implementation(project(":feature:home"))
            implementation(project(":feature:importing"))
            implementation(project(":feature:queue"))
            implementation(project(":feature:radio"))
            implementation(project(":feature:lyrics"))
            implementation(project(":feature:album"))
            implementation(project(":feature:artist"))
            implementation(project(":feature:browse"))
            implementation(project(":feature:library"))
            implementation(project(":feature:recentlyadded"))
            implementation(project(":feature:recentlyplayed"))
            implementation(project(":service:librarysync:domain"))
            implementation(project(":service:librarysync:data"))
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.components.resources)
            implementation(libs.animation)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.datetime)
            implementation(libs.reorderable)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.filekit.dialogs.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "io.github.julystar.musicapp.shared"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        ndk.abiFilters += setOf("arm64-v8a", "x86_64")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    val liveWebDavEnabled = providers.systemProperty("musicapp.liveWebdav.enabled").orElse("false")
    inputs.property("musicapp.liveWebdav.enabled", liveWebDavEnabled)
    systemProperty("musicapp.liveWebdav.enabled", liveWebDavEnabled.get())
    if (providers.environmentVariable("CI").isPresent) {
        testLogging.events(
            org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
        )
    }
}
