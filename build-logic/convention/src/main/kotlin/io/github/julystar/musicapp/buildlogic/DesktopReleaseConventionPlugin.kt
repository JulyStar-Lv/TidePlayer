package io.github.julystar.musicapp.buildlogic

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class TrimDesktopNativesTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val imageRoot: DirectoryProperty

    @get:Input
    abstract val targetOs: Property<String>

    @get:Input
    abstract val targetArchitecture: Property<String>

    @get:Input
    @get:Optional
    abstract val macSigningIdentity: Property<String>

    init {
        doNotTrackState("This task trims jars produced by createReleaseDistributable in place.")
    }

    @TaskAction
    fun trim() {
        val osName = targetOs.get().lowercase()
        val architecture = targetArchitecture.get().lowercase()
        val isArm64 = architecture == "aarch64" || architecture == "arm64"
        val sqliteNativeDirectory = when {
            osName.contains("mac") -> if (isArm64) "osx_arm64" else "osx_x64"
            osName.contains("win") -> if (isArm64) "windows_arm64" else "windows_x64"
            else -> if (isArm64) "linux_arm64" else "linux_x64"
        }
        val jnaNativeDirectory = when {
            osName.contains("mac") -> if (isArm64) "darwin-aarch64" else "darwin-x86-64"
            osName.contains("win") -> if (isArm64) "win32-aarch64" else "win32-x86-64"
            else -> if (isArm64) "linux-aarch64" else "linux-x86-64"
        }
        val jnaPlatformDirectories = when {
            osName.contains("mac") -> setOf("mac", "unix", "bsd")
            osName.contains("win") -> setOf("win32", "wince")
            else -> setOf("linux", "unix", "bsd")
        }
        val knownJnaPlatformDirectories = setOf("win32", "wince", "linux", "mac", "unix", "bsd")
        val applicationDirectory = imageRoot.get().asFile.walkTopDown()
            .firstOrNull { directory ->
                directory.isDirectory && directory.resolve("TidePlayer.cfg").isFile
            }
            ?: error("Cannot locate the packaged Desktop application under ${imageRoot.get().asFile}")
        val hasExternalSkiko = applicationDirectory.listFiles().orEmpty().any { file ->
            !file.name.endsWith(".jar") && file.name.contains("skiko", ignoreCase = true)
        }

        var savedBytes = 0L
        applicationDirectory.listFiles { file -> file.extension.equals("jar", ignoreCase = true) }
            .orEmpty()
            .forEach { jar ->
                val name = jar.name.lowercase()
                savedBytes += when {
                    name.startsWith("sqlite-bundled-jvm-") -> rewriteJar(jar) { entry ->
                        !entry.startsWith("natives/") || entry.startsWith("natives/$sqliteNativeDirectory/")
                    }
                    name.startsWith("jna-") && !name.startsWith("jna-platform-") -> rewriteJar(jar) { entry ->
                        val isNativeDispatch = entry.startsWith("com/sun/jna/") &&
                            (entry.endsWith("jnidispatch.dll") ||
                                entry.endsWith("libjnidispatch.so") ||
                                entry.endsWith("libjnidispatch.jnilib") ||
                                entry.endsWith("libjnidispatch.a"))
                        !isNativeDispatch || entry.startsWith("com/sun/jna/$jnaNativeDirectory/")
                    }
                    name.startsWith("jna-platform-") -> rewriteJar(jar) { entry ->
                        val prefix = "com/sun/jna/platform/"
                        val platformDirectory = entry.takeIf { it.startsWith(prefix) }
                            ?.removePrefix(prefix)
                            ?.substringBefore('/')
                        platformDirectory == null ||
                            platformDirectory !in knownJnaPlatformDirectories ||
                            platformDirectory in jnaPlatformDirectories
                    }
                    hasExternalSkiko && name.startsWith("skiko-awt-runtime-") -> rewriteJar(jar) { entry ->
                        !entry.substringAfterLast('/').startsWith("libskiko-") &&
                            !entry.substringAfterLast('/').startsWith("skiko-")
                    }
                    else -> 0L
                }
            }

        var storedJarGrowthBytes = 0L
        applicationDirectory.listFiles { file -> file.extension.equals("jar", ignoreCase = true) }
            .orEmpty()
            .forEach { jar ->
                val compressedSize = jar.length()
                rewriteJar(jar, storeEntries = true) { true }
                storedJarGrowthBytes += jar.length() - compressedSize
            }

        if (osName.contains("mac")) {
            val appBundle = imageRoot.get().asFile.walkTopDown()
                .firstOrNull { directory -> directory.isDirectory && directory.extension == "app" }
                ?: error("Cannot locate the macOS application bundle under ${imageRoot.get().asFile}")
            val nativeLibraries = appBundle.walkTopDown()
                .filter { file -> file.isFile && file.extension == "dylib" }
                .toList()
            nativeLibraries.forEach { library ->
                val originalSize = library.length()
                execOperations.exec {
                    commandLine("strip", "-x", library.absolutePath)
                }
                savedBytes += originalSize - library.length()
            }

            val signingIdentity = macSigningIdentity.getOrElse("-")
            nativeLibraries.forEach { library ->
                execOperations.exec {
                    commandLine(
                        "codesign", "--force", "--options", "runtime",
                        "--sign", signingIdentity, library.absolutePath,
                    )
                }
            }
            execOperations.exec {
                commandLine(
                    "codesign", "--force", "--options", "runtime",
                    "--preserve-metadata=identifier,entitlements,requirements,flags",
                    "--sign", signingIdentity, appBundle.absolutePath,
                )
            }
            execOperations.exec {
                commandLine("codesign", "--verify", "--deep", "--strict", appBundle.absolutePath)
            }
        }

        logger.lifecycle(
            "Removed or stripped %.2f MiB of packaged native code."
                .format(savedBytes / 1024.0 / 1024.0),
        )
        logger.lifecycle(
            "Stored JVM jar entries without inner compression (+%.2f MiB before installer compression)."
                .format(storedJarGrowthBytes / 1024.0 / 1024.0),
        )
    }

    private fun rewriteJar(
        file: File,
        storeEntries: Boolean = false,
        keepEntry: (String) -> Boolean,
    ): Long {
        val originalSize = file.length()
        val temporaryFile = File(file.parentFile, "${file.name}.tmp")
        ZipFile(file).use { input ->
            ZipOutputStream(temporaryFile.outputStream().buffered()).use { output ->
                output.setLevel(9)
                input.entries().asSequence().forEach { sourceEntry ->
                    if (!keepEntry(sourceEntry.name)) return@forEach
                    val targetEntry = ZipEntry(sourceEntry.name).apply {
                        time = sourceEntry.time
                        comment = sourceEntry.comment
                        extra = sourceEntry.extra
                        if (storeEntries) {
                            method = ZipEntry.STORED
                            size = sourceEntry.size
                            compressedSize = sourceEntry.size
                            crc = sourceEntry.crc
                        }
                    }
                    output.putNextEntry(targetEntry)
                    if (!sourceEntry.isDirectory) {
                        input.getInputStream(sourceEntry).use { it.copyTo(output) }
                    }
                    output.closeEntry()
                }
            }
        }
        Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return originalSize - file.length()
    }
}

abstract class RecompressMacDmgTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val dmgDirectory: DirectoryProperty

    init {
        doNotTrackState("This task recompresses the DMG produced by packageReleaseDmg in place.")
    }

    @TaskAction
    fun recompress() {
        val packages = dmgDirectory.get().asFile
            .listFiles { file -> file.isFile && file.extension.equals("dmg", ignoreCase = true) }
            .orEmpty()
        require(packages.size == 1) {
            "Expected exactly one release DMG under ${dmgDirectory.get().asFile}, found ${packages.size}"
        }
        val source = packages.single()
        val temporary = File(source.parentFile, ".${source.nameWithoutExtension}-recompressed.dmg")
        Files.deleteIfExists(temporary.toPath())
        execOperations.exec {
            // ULMO uses LZMA and is supported on macOS 10.15+, below TidePlayer's
            // current minimum macOS version. It is materially smaller than jpackage's UDZO.
            commandLine(
                "hdiutil", "convert", source.absolutePath,
                "-format", "ULMO", "-o", temporary.absolutePath,
            )
        }
        require(temporary.isFile) { "hdiutil did not create ${temporary.absolutePath}" }
        Files.move(temporary.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING)
        logger.lifecycle(
            "Recompressed ${source.name} as ULMO (%.2f MiB)."
                .format(source.length() / 1024.0 / 1024.0),
        )
    }
}

class DesktopReleaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.matching { task -> task.name == "createRuntimeImage" }.configureEach {
            // Compose 1.11 exposes this jlink option on its task but not yet in
            // the public DSL. Constant-string sharing reduced the measured DMG;
            // fail visibly if a future Compose version changes the task API.
            val compressionProperty = javaClass.methods
                .singleOrNull { method -> method.name.startsWith("getCompressionLevel") }
                ?.invoke(this)
                ?: error("Compose createRuntimeImage no longer exposes a compression level")
            val stringSharing = Class.forName(
                "org.jetbrains.compose.desktop.application.internal.RuntimeCompressionLevel",
            ).enumConstants.single { value ->
                (value as Enum<*>).name == "CONSTANT_STRING_SHARING"
            }
            @Suppress("UNCHECKED_CAST")
            (compressionProperty as Property<Any>).set(stringSharing)
        }

        val trimReleaseNatives = target.tasks.register(
            "trimReleaseNativeLibraries",
            TrimDesktopNativesTask::class.java,
        ) {
            dependsOn("createReleaseDistributable")
            imageRoot.set(target.layout.buildDirectory.dir("compose/binaries/main-release/app"))
            targetOs.set(System.getProperty("os.name"))
            targetArchitecture.set(System.getProperty("os.arch"))
            macSigningIdentity.set(
                target.providers.gradleProperty("compose.desktop.mac.signing.identity").orElse("-"),
            )
        }

        target.tasks.matching { task -> task.name.startsWith("packageRelease") }.configureEach {
            dependsOn(trimReleaseNatives)
        }

        if (System.getProperty("os.name").contains("mac", ignoreCase = true)) {
            val recompressMacDmg = target.tasks.register(
                "recompressReleaseDmg",
                RecompressMacDmgTask::class.java,
            ) {
                dmgDirectory.set(target.layout.buildDirectory.dir("compose/binaries/main-release/dmg"))
            }
            target.tasks.matching { task -> task.name == "packageReleaseDmg" }.configureEach {
                finalizedBy(recompressMacDmg)
            }
        }
    }
}
