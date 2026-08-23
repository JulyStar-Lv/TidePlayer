import gobley.gradle.GobleyHost
import gobley.gradle.cargo.dsl.jvm
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateGitInfoTask : DefaultTask() {
    @get:Input
    abstract val gitCommitSha: Property<String>

    @get:Input
    abstract val appVersionName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDirectory.file(
            "io/github/julystar/musicapp/platform/GeneratedBuildInfo.kt"
        ).get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.github.julystar.musicapp.platform

            internal object GeneratedBuildInfo {
                const val gitCommitSha: String = "${gitCommitSha.get()}"
                const val appVersionName: String = "${appVersionName.get()}"
            }
            """.trimIndent() + "\n"
        )
    }
}

plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.cmp.library)
    alias(libs.plugins.convention.feature)
    alias(libs.plugins.convention.room)
    alias(libs.plugins.convention.cargo.uniffi)
    alias(libs.plugins.kotlin.atomicfu)
    id("com.android.library")
}

compose.resources {
    publicResClass = true
}

val generatedGitInfoDirectory = layout.buildDirectory.dir("generated/gitInfo/commonMain/kotlin")
val gitCommitShaProvider = providers.exec {
    commandLine("git", "rev-parse", "--short=12", "HEAD")
}.standardOutput.asText.map(String::trim)
val appVersionNameProvider = providers.provider {
    rootProject.extra["appVersionName"] as String
}

val generateGitInfo by tasks.registering(GenerateGitInfoTask::class) {
    gitCommitSha.set(gitCommitShaProvider)
    appVersionName.set(appVersionNameProvider)
    outputDirectory.set(generatedGitInfoDirectory)
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
        iosTarget.compilations.getByName("main").cinterops.create("audioProcessingTap") {
            definitionFile.set(
                layout.projectDirectory.file("src/nativeInterop/cinterop/AudioProcessingTap.def")
            )
            includeDirs(layout.projectDirectory.dir("../iosApp"))
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedGitInfoDirectory)
        }
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
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
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.session)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.lyricon.provider)
            implementation(libs.lyric.getter.api)
            implementation(libs.superlyric.api)
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

tasks.matching { task ->
    task.name.startsWith("compileKotlin") || task.name.startsWith("ksp")
}.configureEach {
    dependsOn(generateGitInfo)
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val suppressGeneratedUniffiAndroidWarnings by tasks.registering {
    val generatedFile = layout.buildDirectory.file(
        "generated/uniffi/androidMain/kotlin/uniffi/app_backend/app_backend.android.kt"
    )

    dependsOn(tasks.named("buildUniffiBindings"))

    doLast {
        val file = generatedFile.get().asFile
        if (!file.isFile) return@doLast

        val source = file.readText()
        val packageIndex = source.indexOf("package ")
        if (packageIndex < 0) return@doLast

        val header = source.substring(0, packageIndex)
        val body = source.substring(packageIndex)
        val fileSuppressRegex = Regex("""@file:Suppress\(([^)]*)\)""")
        val existingSuppressions = fileSuppressRegex.findAll(header)
            .flatMap { match -> Regex(""""([^"]+)"""").findAll(match.groupValues[1]) }
            .map { match -> match.groupValues[1] }
            .toList()
        val suppressions = (existingSuppressions + "UNUSED_EXPRESSION").distinct()
        val suppressAnnotation = suppressions.joinToString(
            prefix = "@file:Suppress(",
            postfix = ")"
        ) { suppression -> "\"$suppression\"" }
        val remainingHeader = fileSuppressRegex.replace(header, "").trim()
        val patchedSource = buildString {
            append(suppressAnnotation)
            append("\n\n")
            if (remainingHeader.isNotEmpty()) {
                append(remainingHeader)
                append("\n\n")
            }
            append(body.trimStart())
        }
        if (patchedSource != source) file.writeText(patchedSource)
    }
}

tasks.matching { task ->
    task.name.startsWith("compile") && task.name.endsWith("KotlinAndroid")
}.configureEach {
    dependsOn(suppressGeneratedUniffiAndroidWarnings)
}

cargo {
    packageDirectory = layout.projectDirectory.dir("../rust-libs/app-backend")
    builds.jvm {
        embedRustLibrary = rustTarget == GobleyHost.current.rustTarget
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
}
