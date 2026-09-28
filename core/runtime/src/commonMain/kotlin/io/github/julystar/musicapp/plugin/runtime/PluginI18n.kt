package io.github.julystar.musicapp.plugin.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okio.FileSystem
import okio.Path

internal expect fun pluginLocalePreferences(): List<String>

internal class PluginI18n(
    val locale: String,
    val strings: Map<String, String>,
) {
    fun resolve(text: String): String = when {
        text.startsWith("@@") -> text.drop(1)
        text.startsWith('@') -> strings[text.drop(1)] ?: error("Unknown plugin string: ${text.drop(1)}")
        else -> text
    }

    fun bootstrap(): String {
        val data = JsonObject(strings.mapValues { JsonPrimitive(it.value) })
        val localeJson = JsonPrimitive(locale)
        return """
            (function() {
              var strings = $data;
              Platform.i18n = {
                getLocale: function() { return $localeJson; },
                t: function(key) {
                  key = String(key);
                  if (!Object.prototype.hasOwnProperty.call(strings, key)) throw new Error('Unknown plugin string: ' + key);
                  var text = strings[key], args = Array.prototype.slice.call(arguments, 1);
                  if (!args.length) return text;
                  if (args.length > 64) throw new Error('Too many plugin string arguments');
                  var used = 0;
                  var result = text.replace(/%(?:([1-9][0-9]*)\${'$'})?([sd%])/g, function(token, position, type) {
                    if (type === '%') return '%';
                    var index = position ? Number(position) - 1 : 0;
                    used = Math.max(used, index + 1);
                    var value = args[index];
                    if (type === 's' ? typeof value !== 'string' : typeof value !== 'number' || !Number.isSafeInteger(value)) throw new Error('Invalid plugin string argument: ' + (index + 1));
                    return String(value);
                  });
                  if (used !== args.length) throw new Error('Invalid plugin string argument count');
                  return result;
                }
              };
            })();
        """.trimIndent()
    }

    companion object {
        fun read(
            root: Path,
            manifest: JsonObject,
            fileSystem: FileSystem = FileSystem.SYSTEM,
            preferences: List<String> = pluginLocalePreferences(),
        ): PluginI18n {
            val references = displayTexts(manifest).filter { it.startsWith('@') && !it.startsWith("@@") }
            val declaration = manifest["i18n"]
            if (declaration == null) {
                require(references.isEmpty()) { "plugin text references require i18n" }
                return PluginI18n("und", emptyMap())
            }
            require((manifest["minHostApiVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.let { it >= 4 } == true) {
                "plugin i18n requires Host API 4"
            }
            val i18n = declaration as? JsonObject ?: error("invalid plugin i18n")
            val default = (i18n["defaultLocale"] as? JsonPrimitive)?.contentOrNull ?: error("i18n defaultLocale is required")
            val paths = i18n["resources"] as? JsonObject ?: error("i18n resources are required")
            require(paths.size in 1..64 && default in paths) { "invalid i18n defaultLocale or resource count" }
            val resources = paths.mapValues { (tag, value) ->
                require(Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*").matches(tag)) { "invalid i18n locale: $tag" }
                val path = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("invalid i18n resource path")
                require(path.endsWith(".json") && !path.startsWith('/') && '\\' !in path && ':' !in path && path.split('/').none { it == ".." || it.isEmpty() }) { "invalid i18n resource path: $path" }
                val file = root / path
                require(fileSystem.metadataOrNull(file)?.isRegularFile == true) { "i18n resource not found: $path" }
                require((fileSystem.metadata(file).size ?: 0) <= 512 * 1024) { "i18n resource exceeds 512 KiB" }
                val content = Json.parseToJsonElement(fileSystem.read(file) { readUtf8() }) as? JsonObject ?: error("invalid i18n resource: $path")
                content.mapValues { (_, text) ->
                    (text as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("i18n values must be strings")
                }
            }
            val defaults = resources.getValue(default)
            require(references.all { it.length > 1 && it.drop(1) in defaults }) { "i18n default resource is missing a text reference" }
            resources.values.forEach { values ->
                require(defaults.keys.containsAll(values.keys)) { "i18n translation has unknown keys" }
                values.forEach { (key, value) ->
                    if (key !in references.map { it.drop(1) }) {
                        require(signature(value) == signature(defaults.getValue(key))) { "i18n placeholder mismatch: $key" }
                    }
                }
            }
            val selected = preferences.firstNotNullOfOrNull { preferred ->
                resources.keys.firstOrNull { it.equals(preferred, ignoreCase = true) }
                    ?: resources.keys.firstOrNull { languageScript(it) == languageScript(preferred) }
            } ?: default
            val chain = generateSequence(selected) { it.substringBeforeLast('-', "").takeIf(String::isNotEmpty) }.toList()
            val merged = defaults.toMutableMap()
            chain.reversed().forEach { resources[it]?.let(merged::putAll) }
            return PluginI18n(selected, merged)
        }

        private fun languageScript(tag: String): String {
            val parts = tag.lowercase().split('-')
            val language = parts.first()
            val script = parts.firstOrNull { it.length == 4 } ?: if (language == "zh") {
                if (parts.any { it in setOf("tw", "hk", "mo") }) "hant" else "hans"
            } else ""
            return "$language-$script"
        }

        private fun signature(text: String): Map<Int, Char> {
            val tokens = Regex("%(?:([1-9][0-9]*)\\$)?([sd%])").findAll(text).filter { it.groupValues[2] != "%" }.toList()
            val numbered = tokens.any { it.groupValues[1].isNotEmpty() }
            require(!numbered || tokens.all { it.groupValues[1].isNotEmpty() }) { "mixed i18n placeholders" }
            require(numbered || tokens.size <= 1) { "multiple i18n arguments require numbered placeholders" }
            val result = mutableMapOf<Int, Char>()
            tokens.forEach { token ->
                val position = token.groupValues[1].toIntOrNull() ?: 1
                val type = token.groupValues[2].single()
                require(position in 1..64 && (result[position] == null || result[position] == type)) { "invalid i18n placeholder" }
                result[position] = type
            }
            require(result.keys == (1..result.size).toSet()) { "i18n placeholder positions must be contiguous" }
            return result
        }

        private fun displayTexts(manifest: JsonObject): List<String> = buildList {
            fun addText(value: kotlinx.serialization.json.JsonElement?) {
                (value as? JsonPrimitive)?.contentOrNull?.let(::add)
            }
            addText(manifest["name"])
            addText(manifest["description"])
            (manifest["configFields"] as? kotlinx.serialization.json.JsonArray).orEmpty().forEach { value ->
                val field = value as? JsonObject ?: return@forEach
                listOf("title", "summary", "group").forEach { addText(field[it]) }
                if ((field["type"] as? JsonPrimitive)?.contentOrNull == "markdown") addText(field["defaultValue"])
                (field["options"] as? kotlinx.serialization.json.JsonArray).orEmpty().forEach { option ->
                    val obj = option as? JsonObject ?: return@forEach
                    addText(obj["label"])
                    addText(obj["summary"])
                }
            }
        }
    }
}
