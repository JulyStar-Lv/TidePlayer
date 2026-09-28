package io.github.julystar.musicapp.plugin.runtime

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okio.Path.Companion.toPath

class PluginI18nTest {
    private fun read(
        en: String = """{"name":"Source","message":"Item %1${'$'}s: %2${'$'}d","fallback":"Default"}""",
        zh: String = """{"name":"来源","message":"第 %2${'$'}d 项：%1${'$'}s"}""",
        path: String = "locales/en.json",
        host: Int = 4,
        preferences: List<String> = listOf("zh-CN"),
    ): PluginI18n {
        val root = Files.createTempDirectory("tideplayer-i18n")
        Files.createDirectories(root.resolve("locales"))
        Files.writeString(root.resolve("locales/en.json"), en)
        Files.writeString(root.resolve("locales/zh-Hans.json"), zh)
        val manifest = Json.parseToJsonElement("""
            {"name":"@name","minHostApiVersion":$host,"i18n":{"defaultLocale":"en","resources":{"en":"$path","zh-Hans":"locales/zh-Hans.json"}}}
        """) as JsonObject
        return PluginI18n.read(root.toString().toPath(), manifest, preferences = preferences)
    }

    @Test
    fun selectsScriptAndFallsBackPerKeyWithoutResolvingTwice() {
        val resource = read()
        assertEquals("zh-Hans", resource.locale)
        assertEquals("来源", resource.resolve("@name"))
        assertEquals("Default", resource.resolve("@fallback"))
        assertEquals("@name", resource.resolve("@@name"))
        assertEquals("literal", resource.resolve("literal"))
        assertEquals("en", read(preferences = listOf("de-DE")).locale)
        assertEquals("zh-Hans", read(preferences = listOf("de-DE", "zh-CN")).locale)
        assertEquals("en", read(preferences = listOf("zh-TW")).locale)
    }

    @Test
    fun rejectsUnsafePathsInvalidReferencesAndInconsistentPlaceholders() {
        assertFailsWith<IllegalArgumentException> { read(path = "../en.json") }
        assertFailsWith<IllegalArgumentException> { read(host = 3) }
        assertFailsWith<IllegalArgumentException> { read(en = "{}", zh = "{}") }
        assertFailsWith<IllegalArgumentException> { read(zh = """{"extra":"Unknown"}""") }
        assertFailsWith<IllegalArgumentException> { read(zh = """{"message":"Wrong %1${'$'}d"}""") }
        assertFailsWith<IllegalStateException> { read(en = """{"name":42}""", zh = "{}") }
    }
}
