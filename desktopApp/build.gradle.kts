import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
}

val appPackageVersion = rootProject.extra["appPackageVersion"] as String
val desktopProguardDir = layout.buildDirectory.dir("compose/proguard")
val desktopProguardEnabled = providers.gradleProperty("desktop.proguard.enabled")
    .map { value -> value.equals("true", ignoreCase = true) }
    .orElse(false)
val desktopTargetFormats = when {
    System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> arrayOf(
        TargetFormat.Dmg,
    )
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> arrayOf(
        TargetFormat.Exe,
    )
    else -> arrayOf(
        TargetFormat.Deb,
        TargetFormat.Rpm,
        TargetFormat.AppImage,
    )
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":core:runtime"))
                implementation(project(":core:domain"))
                implementation(project(":core:presentation"))
                implementation(project(":service:playback:domain"))
                implementation(compose.desktop.currentOs)
                implementation(compose.components.resources)
                implementation(libs.koin.core)
                implementation(libs.filekit.dialogs.compose)
                implementation("net.java.dev.jna:jna:5.19.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.julystar.musicapp.MainKt"
        buildTypes {
            release {
                proguard {
                    // Production hotfix default remains disabled because the previous
                    // packaged build exposed DataStore/SQLite runtime regressions.
                    // CI can explicitly enable shrinking with
                    // -Pdesktop.proguard.enabled=true to validate keep rules safely.
                    isEnabled.set(desktopProguardEnabled)
                    obfuscate.set(true)
                    // Kotlin coroutine state machines currently trigger a ProGuard
                    // stack-size calculation failure when optimization is enabled.
                    optimize.set(false)
                    configurationFiles.from(project.file("proguard-rules.pro"))
                }
            }
        }
        nativeDistributions {
            targetFormats(*desktopTargetFormats)
            modules("jdk.unsupported")
            packageName = "TidePlayer"
            packageVersion = appPackageVersion
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}

// compose-miuix-ui is published as Java 21 bytecode. Keep Gradle/Kotlin builds
// compatible with the repository toolchain, but launch the desktop app on a
// Java 21 runtime so local runs do not fail with UnsupportedClassVersionError.
val desktopRuntimeLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

afterEvaluate {
    desktopProguardDir.get().asFile.mkdirs()

    tasks.named<JavaExec>("run") {
        javaLauncher.set(desktopRuntimeLauncher)
        executable = desktopRuntimeLauncher.get().executablePath.asFile.absolutePath
    }
}
